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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RosterUnitsTest {
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    private final String prefix = "units_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = "Unit selection test 123!";

    @AfterEach void cleanup() {
        for (String suffix : new String[]{"a", "b"}) {
            jdbc.update("DELETE FROM rosters WHERE user_id IN (SELECT id FROM users WHERE username=?)", prefix + suffix);
            jdbc.update("DELETE FROM users WHERE username=?", prefix + suffix);
        }
    }
    private long faction() { return jdbc.queryForObject("SELECT id FROM factions WHERE slug='new-antioch'", Long.class); }
    private long yeoman() { return jdbc.queryForObject("SELECT id FROM units WHERE faction_id=? AND name='Yeoman'", Long.class, faction()); }
    private void signIn(AuthTestClient client, String suffix) throws Exception {
        String name = prefix + suffix;
        var response = client.postJson("/api/auth/register", Map.of("username", name, "email", name + "@example.invalid",
                "password", PASSWORD, "confirmPassword", PASSWORD));
        assertEquals(201, response.statusCode(), response.body());
        assertEquals(200, client.login(name, PASSWORD).statusCode());
    }
    private long roster(AuthTestClient client) throws Exception {
        var response = client.postJson("/api/rosters", Map.of("rosterName", "Unit test roster", "factionId", faction(), "ducatLimit", 700));
        assertEquals(201, response.statusCode(), response.body());
        return json(response).get("id").asLong();
    }
    private String path(long rosterId) { return "/api/rosters/" + rosterId + "/units"; }
    private JsonNode json(HttpResponse<String> response) { return AuthTestClient.JSON.readTree(response.body()); }
    private JsonNode add(AuthTestClient client, long rosterId, long unitId) throws Exception {
        var response = client.postJson(path(rosterId), Map.of("unitId", unitId));
        assertEquals(201, response.statusCode(), response.body());
        assertEquals("no-store", response.headers().firstValue("cache-control").orElseThrow());
        return json(response);
    }
    private HttpRequest deleteRequest(AuthTestClient client, long rosterId, long entryId, boolean csrf) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path(rosterId) + "/" + entryId)).DELETE();
        if (csrf) request.header("X-CSRF-TOKEN", client.csrf());
        return request.build();
    }

    @Test void duplicateTypesAreSeparateAndRemovingOnePersistsAcrossSessions() throws Exception {
        try (var client = new AuthTestClient(port)) {
            signIn(client, "a");
            long id = roster(client);
            jdbc.update("UPDATE rosters SET updated_at=created_at - INTERVAL '1' DAY WHERE id=?", id);
            var empty = client.get(path(id));
            assertEquals(200, empty.statusCode());
            assertEquals(0, json(empty).get("units").size());
            assertEquals(3, json(empty).get("availableUnits").size());
            String previous = json(empty).get("roster").get("updatedAt").asString();
            long first = add(client, id, yeoman()).get("units").get(0).get("id").asLong();
            var two = add(client, id, yeoman());
            long second = two.get("units").get(1).get("id").asLong();
            assertNotEquals(first, second);
            assertEquals(2, two.get("roster").get("unitCount").asInt());
            assertNotEquals(previous, two.get("roster").get("updatedAt").asString());
            assertEquals(yeoman(), two.get("units").get(0).get("unitId").asLong());
            assertEquals(yeoman(), two.get("units").get(1).get("unitId").asLong());
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM roster_units WHERE roster_id=? AND quantity=1", Integer.class, id));
            long gear = jdbc.queryForObject("SELECT MIN(id) FROM equipment WHERE faction_id=?", Long.class, faction());
            jdbc.update("INSERT INTO roster_equipment (roster_unit_id,equipment_id) VALUES (?,?),(?,?)", first, gear, second, gear);
            var removed = client.send(deleteRequest(client, id, first, true));
            assertEquals(200, removed.statusCode(), removed.body());
            assertEquals(1, json(removed).get("roster").get("unitCount").asInt());
            assertEquals(second, json(removed).get("units").get(0).get("id").asLong());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM roster_equipment WHERE roster_unit_id=?", Integer.class, first));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM roster_equipment WHERE roster_unit_id=?", Integer.class, second));
            assertEquals(204, client.logout().statusCode());
            assertEquals(200, client.login(prefix + "a", PASSWORD).statusCode());
            var saved = json(client.get(path(id))).get("units");
            assertEquals(1, saved.size());
            assertEquals(second, saved.get(0).get("id").asLong());
            assertEquals(1, json(client.get("/api/rosters")).get(0).get("unitCount").asInt());
        }
    }

    @Test void anotherAccountCannotReadAddOrRemoveRosterUnits() throws Exception {
        try (var owner = new AuthTestClient(port); var other = new AuthTestClient(port)) {
            signIn(owner, "a"); signIn(other, "b");
            long owned = roster(owner), otherRoster = roster(other);
            long entry = add(owner, owned, yeoman()).get("units").get(0).get("id").asLong();
            assertEquals(404, other.get(path(owned)).statusCode());
            assertEquals(other.get(path(Long.MAX_VALUE)).body(), other.get(path(owned)).body());
            assertEquals(404, other.postJson(path(owned), Map.of("unitId", yeoman())).statusCode());
            assertEquals(404, other.send(deleteRequest(other, owned, entry, true)).statusCode());
            assertEquals(404, other.send(deleteRequest(other, otherRoster, entry, true)).statusCode());
            assertEquals(1, json(owner.get(path(owned))).get("units").size());
            assertEquals(0, json(other.get(path(otherRoster))).get("units").size());
        }
    }

    @Test void entryFromAnotherRosterCannotBeDeletedEvenByTheSameOwner() throws Exception {
        try (var client = new AuthTestClient(port)) {
            signIn(client, "a");
            long first = roster(client), second = roster(client);
            long entry = add(client, first, yeoman()).get("units").get(0).get("id").asLong();
            assertEquals(404, client.send(deleteRequest(client, second, entry, true)).statusCode());
            assertEquals(404, client.send(deleteRequest(client, first, Long.MAX_VALUE, true)).statusCode());
            assertEquals(1, json(client.get(path(first))).get("units").size());
        }
    }

    @Test void authenticationAndCsrfAreRequired() throws Exception {
        try (var client = new AuthTestClient(port); var anonymous = new AuthTestClient(port)) {
            signIn(client, "a");
            long id = roster(client);
            long entry = add(client, id, yeoman()).get("units").get(0).get("id").asLong();
            assertEquals(401, anonymous.get(path(id)).statusCode());
            assertEquals(401, anonymous.postJson(path(id), Map.of("unitId", yeoman())).statusCode());
            assertEquals(401, anonymous.send(deleteRequest(anonymous, id, entry, true)).statusCode());
            assertEquals(403, client.send(client.postRequest(path(id), "{\"unitId\":" + yeoman() + "}", "application/json", null)).statusCode());
            assertEquals(403, client.send(deleteRequest(client, id, entry, false)).statusCode());
            assertEquals(1, json(client.get(path(id))).get("units").size());
        }
    }

    @Test void invalidAndForeignFactionUnitsDoNotChangeTheRoster() throws Exception {
        try (var client = new AuthTestClient(port)) {
            signIn(client, "a"); long id = roster(client);
            long foreign = jdbc.queryForObject("SELECT MIN(id) FROM units WHERE faction_id<>?", Long.class, faction());
            for (Object value : new Object[]{0, -1, Long.MAX_VALUE, foreign, 1.5, "1"}) {
                var result = client.postJson(path(id), Map.of("unitId", value));
                assertEquals(400, result.statusCode(), result.body());
            }
            for (String body : new String[]{"{}", "null", "{broken", "{\"unitId\":null}"}) {
                assertEquals(400, client.postJsonRaw(path(id), body).statusCode());
            }
            assertEquals(0, json(client.get(path(id))).get("units").size());
            var result = client.postJson(path(id), Map.of("unitId", yeoman(), "quantity", 30, "rosterId", Long.MAX_VALUE));
            assertEquals(201, result.statusCode());
            assertEquals(1, json(result).get("units").size());
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "INSERT INTO roster_units (roster_id,unit_id,quantity) VALUES (?,?,2)", id, yeoman()));
        }
    }

    @Test void simultaneousAddsSaveTwoIndividualsWithoutLosingCount() throws Exception {
        try (var client = new AuthTestClient(port)) {
            signIn(client, "a"); long id = roster(client);
            String token = client.csrf();
            var request = client.postRequest(path(id), "{\"unitId\":" + yeoman() + "}", "application/json", token);
            var first = client.sendAsync(request);
            var second = client.sendAsync(request);
            assertEquals(201, first.get().statusCode());
            assertEquals(201, second.get().statusCode());
            var result = json(client.get(path(id)));
            assertEquals(2, result.get("units").size());
            assertEquals(2, result.get("roster").get("unitCount").asInt());
        }
    }
}
