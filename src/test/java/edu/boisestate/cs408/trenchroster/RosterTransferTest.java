package edu.boisestate.cs408.trenchroster;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RosterTransferTest {
    private static final String IMPORT = "/api/rosters/import";
    private static final String ROSTER = """
            {"version":1,"name":"Forward patrol","faction":"new-antioch","ducatLimit":50,"units":[
              {"name":"Yeoman","equipment":[{"name":"Bolt-Action Rifle","quantity":1},{"name":"Bayonet","quantity":1}]},
              {"name":"Yeoman","equipment":[{"name":"Pistol","quantity":1}]}
            ]}
            """;
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void importsDistinctLoadoutsAndRoundTripsWithFreshIdsAndRecalculatedCosts() throws Exception {
        String name = username();
        try (var client = new AuthTestClient(port)) {
            login(client, name);
            var first = client.postJsonRaw(IMPORT, ROSTER);
            assertEquals(201, first.statusCode(), first.body());
            var a = AuthTestClient.JSON.readTree(first.body());
            assertTrue(a.path("overBudget").asInt() > 0);
            assertFalse(a.path("unitIssues").isEmpty());
            var saved = client.get("/api/rosters/" + a.path("id").asLong() + "/export");
            assertEquals(200, saved.statusCode(), saved.body());
            var snapshot = AuthTestClient.JSON.readTree(saved.body());
            var file = snapshot.path("file");
            assertEquals("Yeoman", file.path("units").path(0).path("name").asString());
            assertEquals("Bayonet", file.path("units").path(0).path("equipment").path(0).path("name").asString());
            assertEquals("Pistol", file.path("units").path(1).path("equipment").path(0).path("name").asString());
            var second = client.postJson(IMPORT, file);
            assertEquals(201, second.statusCode(), second.body());
            var b = AuthTestClient.JSON.readTree(second.body());
            assertNotEquals(a.path("id"), b.path("id"));
            var again = client.get("/api/rosters/" + b.path("id").asLong() + "/export");
            assertEquals(snapshot, AuthTestClient.JSON.readTree(again.body()));
            try (var anonymous = new AuthTestClient(port)) {
                assertEquals(401, anonymous.get("/api/rosters/" + b.path("id").asLong() + "/export").statusCode());
            }
            String other = username();
            try (var stranger = new AuthTestClient(port)) {
                login(stranger, other);
                assertEquals(404, stranger.get("/api/rosters/" + b.path("id").asLong() + "/export").statusCode());
            } finally { cleanup(other); }
        } finally { cleanup(name); }
    }

    @Test
    void invalidImportsLeaveNoPartialRoster() throws Exception {
        String name = username();
        try (var client = new AuthTestClient(port)) {
            login(client, name);
            for (String bad : new String[]{"{", ROSTER + "{}", ROSTER.replace("\"version\":1", "\"version\":2"),
                    ROSTER.replace("\"version\":1", "\"version\":1,\"version\":1"),
                    ROSTER.replace("Pistol", "Unknown gear"), ROSTER.replace("Pistol", "Bayonet"),
                    ROSTER.replace("\"quantity\":1", "\"quantity\":1.5"),
                    ROSTER.replace("new-antioch", "missing-faction"),
                    ROSTER.replace("Yeoman", "Lieutenant")}) {
                var response = client.postJsonRaw(IMPORT, bad);
                assertEquals(400, response.statusCode(), response.body());
                var list = client.get("/api/rosters");
                assertEquals(200, list.statusCode());
                assertTrue(AuthTestClient.JSON.readTree(list.body()).isEmpty());
            }
        } finally { cleanup(name); }
    }

    private String username() { return "cp6_" + UUID.randomUUID().toString().replace("-", ""); }

    private void login(AuthTestClient client, String name) throws Exception {
        String password = "Transfer verification 123!";
        var response = client.postJson("/api/auth/register", Map.of("username", name, "email", name + "@example.invalid",
                "password", password, "confirmPassword", password));
        assertEquals(201, response.statusCode(), response.body());
        assertEquals(200, client.login(name, password).statusCode());
    }

    private void cleanup(String name) {
        jdbc.update("DELETE FROM rosters WHERE user_id IN (SELECT id FROM users WHERE username=?)", name);
        jdbc.update("DELETE FROM users WHERE username=?", name);
    }
}
