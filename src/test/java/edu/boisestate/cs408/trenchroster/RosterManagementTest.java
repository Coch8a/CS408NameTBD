package edu.boisestate.cs408.trenchroster;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises saved roster CRUD through real HTTP, session cookies, CSRF and the test database. */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RosterManagementTest {
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    private final String prefix = "r" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = "Roster test passphrase 123!";

    @AfterEach void cleanOnlyTestAccountsAndTheirRosters() {
        for (String name : new String[]{prefix + "a", prefix + "b"}) {
            jdbc.update("DELETE FROM rosters WHERE user_id IN (SELECT id FROM users WHERE username=?)", name);
            jdbc.update("DELETE FROM users WHERE username=?", name);
        }
    }

    private long faction() { return jdbc.queryForObject("SELECT id FROM factions WHERE slug='new-antioch'", Long.class); }
    private Map<String, Object> settings(String name) { return Map.of("rosterName", name, "factionId", faction(), "ducatLimit", 700); }
    private long signIn(AuthTestClient client, String suffix) throws Exception {
        String name = prefix + suffix;
        var register = client.postJson("/api/auth/register", Map.of("username", name, "email", name + "@example.invalid",
                "password", PASSWORD, "confirmPassword", PASSWORD));
        assertEquals(201, register.statusCode(), register.body());
        assertEquals(200, client.login(name, PASSWORD).statusCode());
        return AuthTestClient.JSON.readTree(register.body()).get("id").asLong();
    }
    private HttpResponse<String> write(AuthTestClient client, String method, String path, Object body, boolean csrf) throws Exception {
        return raw(client, method, path, body == null ? "" : AuthTestClient.JSON.writeValueAsString(body), csrf);
    }
    private HttpResponse<String> raw(AuthTestClient client, String method, String path, String body, boolean csrf) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        if (csrf) builder.header("X-CSRF-TOKEN", client.csrf());
        return client.send(builder.build());
    }
    private long create(AuthTestClient client, String name) throws Exception {
        var response = client.postJson("/api/rosters", settings(name));
        assertEquals(201, response.statusCode(), response.body());
        long id = AuthTestClient.JSON.readTree(response.body()).get("id").asLong();
        assertEquals("/api/rosters/" + id, response.headers().firstValue("location").orElseThrow());
        return id;
    }

    @Test void createsListsReadsUpdatesAndDeletesPersistentRosterSettings() throws Exception {
        try (var client = new AuthTestClient(port)) {
            signIn(client, "a");
            assertEquals("[]", client.get("/api/rosters").body());
            long id = create(client, "  First Company  ");
            var detail = client.get("/api/rosters/" + id);
            var roster = AuthTestClient.JSON.readTree(detail.body());
            assertEquals("First Company", roster.get("rosterName").asString());
            assertEquals("New Antioch", roster.get("factionName").asString());
            assertEquals(0, roster.get("unitCount").asInt());
            assertFalse(roster.has("userId"));
            assertEquals("no-store", detail.headers().firstValue("cache-control").orElseThrow());
            assertEquals(1, AuthTestClient.JSON.readTree(client.get("/api/rosters").body()).size());
            var update = write(client, "PUT", "/api/rosters/" + id,
                    Map.of("rosterName", "Second Company", "factionId", faction(), "ducatLimit", 900), true);
            assertEquals(200, update.statusCode(), update.body());
            assertEquals("Second Company", jdbc.queryForObject("SELECT roster_name FROM rosters WHERE id=?", String.class, id));
            assertEquals(900, jdbc.queryForObject("SELECT ducat_limit FROM rosters WHERE id=?", Integer.class, id));
            assertEquals(204, client.logout().statusCode());
            assertEquals(200, client.login(prefix + "a", PASSWORD).statusCode());
            assertEquals(900, AuthTestClient.JSON.readTree(client.get("/api/rosters/" + id).body()).get("ducatLimit").asInt());
            assertEquals(204, write(client, "DELETE", "/api/rosters/" + id, null, true).statusCode());
            assertEquals(404, client.get("/api/rosters/" + id).statusCode());
            assertEquals("[]", client.get("/api/rosters").body());
        }
    }

    @Test void ownerCannotBeSpoofedAndOtherAccountsCannotReadUpdateOrDelete() throws Exception {
        try (var owner = new AuthTestClient(port); var other = new AuthTestClient(port)) {
            long ownerId = signIn(owner, "a");
            long otherId = signIn(other, "b");
            var forged = new java.util.HashMap<String, Object>(settings("Private list"));
            forged.put("userId", otherId);
            forged.put("ownerId", otherId);
            var created = owner.postJson("/api/rosters", forged);
            assertEquals(201, created.statusCode(), created.body());
            long id = AuthTestClient.JSON.readTree(created.body()).get("id").asLong();
            assertEquals(ownerId, jdbc.queryForObject("SELECT user_id FROM rosters WHERE id=?", Long.class, id));
            assertEquals("[]", other.get("/api/rosters?userId=" + ownerId).body());
            var hidden = other.get("/api/rosters/" + id);
            var missing = other.get("/api/rosters/9223372036854775807");
            assertEquals(404, hidden.statusCode());
            assertEquals(missing.body(), hidden.body());
            assertEquals(404, write(other, "PUT", "/api/rosters/" + id, settings("Stolen"), true).statusCode());
            assertEquals(404, write(other, "DELETE", "/api/rosters/" + id, null, true).statusCode());
            assertEquals("Private list", jdbc.queryForObject("SELECT roster_name FROM rosters WHERE id=?", String.class, id));
        }
    }

    @Test void anonymousAccessAndMissingCsrfCannotMutateRosters() throws Exception {
        try (var owner = new AuthTestClient(port); var anonymous = new AuthTestClient(port)) {
            signIn(owner, "a");
            long id = create(owner, "Protected");
            for (String path : new String[]{"/api/rosters", "/api/rosters/factions", "/api/rosters/" + id}) {
                assertEquals(401, anonymous.get(path).statusCode());
            }
            for (String method : new String[]{"POST", "PUT", "DELETE"}) {
                String path = method.equals("POST") ? "/api/rosters" : "/api/rosters/" + id;
                assertEquals(401, write(anonymous, method, path, settings("Unauthorized"), true).statusCode());
                assertEquals(403, write(owner, method, path, settings("No token"), false).statusCode());
            }
            assertEquals("Protected", jdbc.queryForObject("SELECT roster_name FROM rosters WHERE id=?", String.class, id));
        }
    }

    @Test void invalidFieldsAndUnsupportedFactionsReturnSafeErrorsWithoutChangingData() throws Exception {
        try (var client = new AuthTestClient(port)) {
            signIn(client, "a");
            long id = create(client, "Keep me");
            for (var invalid : java.util.List.of(
                    Map.of("rosterName", "   ", "factionId", faction(), "ducatLimit", 700),
                    Map.of("rosterName", "x".repeat(121), "factionId", faction(), "ducatLimit", 700),
                    Map.of("rosterName", "Bad", "factionId", faction(), "ducatLimit", 0),
                    Map.of("rosterName", "Bad", "factionId", faction(), "ducatLimit", -1),
                    Map.of("rosterName", "Bad", "factionId", faction(), "ducatLimit", 1000001),
                    Map.of("rosterName", "Bad", "factionId", Long.MAX_VALUE, "ducatLimit", 700),
                    Map.of("rosterName", "Bad", "factionId", faction(), "ducatLimit", 700.5),
                    Map.of("rosterName", "Bad", "factionId", faction() + 0.5, "ducatLimit", 700))) {
                var response = client.postJson("/api/rosters", invalid);
                assertEquals(400, response.statusCode(), invalid + " => " + response.body());
                assertFalse(response.body().contains("Exception"));
                assertEquals(400, write(client, "PUT", "/api/rosters/" + id, invalid, true).statusCode());
            }
            for (String body : new String[]{"{}", "null", "{broken", "{\"rosterName\":\"Missing fields\"}"}) {
                assertEquals(400, raw(client, "POST", "/api/rosters", body, true).statusCode());
            }
            assertEquals(1, AuthTestClient.JSON.readTree(client.get("/api/rosters").body()).size());
            assertEquals("Keep me", jdbc.queryForObject("SELECT roster_name FROM rosters WHERE id=?", String.class, id));
            var factions = AuthTestClient.JSON.readTree(client.get("/api/rosters/factions").body());
            assertEquals(1, factions.size());
            assertEquals(faction(), factions.get(0).get("id").asLong());
        }
    }

    @Test void deletionCascadesOnlyTheSelectedRostersChildren() throws Exception {
        try (var client = new AuthTestClient(port)) {
            signIn(client, "a");
            long deleted = create(client, "Delete me");
            long kept = create(client, "Keep me");
            long unit = jdbc.queryForObject("SELECT MIN(id) FROM units WHERE faction_id=?", Long.class, faction());
            long gear = jdbc.queryForObject("SELECT MIN(id) FROM equipment WHERE faction_id=?", Long.class, faction());
            for (long id : new long[]{deleted, kept}) {
                jdbc.update("INSERT INTO roster_units (roster_id, unit_id) VALUES (?, ?)", id, unit);
                long row = jdbc.queryForObject("SELECT id FROM roster_units WHERE roster_id=?", Long.class, id);
                jdbc.update("INSERT INTO roster_equipment (roster_unit_id, equipment_id) VALUES (?, ?)", row, gear);
            }
            assertEquals(1, AuthTestClient.JSON.readTree(client.get("/api/rosters/" + deleted).body()).get("unitCount").asInt());
            long removedUnit = jdbc.queryForObject("SELECT id FROM roster_units WHERE roster_id=?", Long.class, deleted);
            assertEquals(204, write(client, "DELETE", "/api/rosters/" + deleted, null, true).statusCode());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM roster_units WHERE roster_id=?", Integer.class, deleted));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM roster_equipment WHERE roster_unit_id=?", Integer.class, removedUnit));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM roster_units WHERE roster_id=?", Integer.class, kept));
            assertEquals(200, client.get("/api/rosters/" + kept).statusCode());
        }
    }

    @Test void changingFactionCannotLeaveExistingUnitReferencesInconsistent() throws Exception {
        try (var client = new AuthTestClient(port)) {
            signIn(client, "a");
            long id = create(client, "With units");
            long unit = jdbc.queryForObject("SELECT MIN(id) FROM units WHERE faction_id=?", Long.class, faction());
            jdbc.update("INSERT INTO roster_units (roster_id, unit_id) VALUES (?, ?)", id, unit);
            var result = write(client, "PUT", "/api/rosters/" + id,
                    Map.of("rosterName", "Invalid faction change", "factionId", Long.MAX_VALUE, "ducatLimit", 700), true);
            assertEquals(400, result.statusCode());
            assertTrue(result.body().contains("Remove the roster's units"));
            assertEquals("With units", jdbc.queryForObject("SELECT roster_name FROM rosters WHERE id=?", String.class, id));
            assertEquals(200, write(client, "PUT", "/api/rosters/" + id, settings("Rename only"), true).statusCode());
        }
    }
}
