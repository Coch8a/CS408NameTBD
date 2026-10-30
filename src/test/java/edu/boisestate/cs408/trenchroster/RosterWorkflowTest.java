package edu.boisestate.cs408.trenchroster;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

class RosterWorkflowTest {
    private static final String PASSWORD = "Roster workflow test 123!";

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(TrenchRosterApplication.class)
                .profiles("test").bannerMode(Banner.Mode.OFF).registerShutdownHook(false)
                .run("--server.port=0", "--server.address=127.0.0.1");
    }

    private int port(ConfigurableApplicationContext app) {
        return Integer.parseInt(app.getEnvironment().getRequiredProperty("local.server.port"));
    }

    private JsonNode expect(int status, HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), response.body());
        return response.body().isBlank() ? null : AuthTestClient.JSON.readTree(response.body());
    }

    private HttpResponse<String> write(AuthTestClient client, int port, String method, String path, Object body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", client.csrf())
                .method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : AuthTestClient.JSON.writeValueAsString(body)))
                .build());
    }

    private void cleanup(JdbcTemplate jdbc, String username) {
        jdbc.update("DELETE FROM rosters WHERE user_id IN (SELECT id FROM users WHERE username=?)", username);
        jdbc.update("DELETE FROM users WHERE username=?", username);
    }

    @Test void rosterSettingsAndIndividualUnitsSurviveRestartAndCanBeDeleted() throws Exception {
        String username = "roster_" + UUID.randomUUID().toString().replace("-", "");
        long rosterId, survivor, unitId;
        try (var app = start(); var client = new AuthTestClient(port(app))) {
            var jdbc = app.getBean(JdbcTemplate.class);
            try {
                expect(201, client.postJson("/api/auth/register", Map.of("username", username,
                        "email", username + "@example.invalid", "password", PASSWORD, "confirmPassword", PASSWORD)));
                expect(200, client.login(username, PASSWORD));
                long faction = expect(200, client.get("/api/rosters/factions")).get(0).get("id").asLong();
                rosterId = expect(201, client.postJson("/api/rosters", Map.of(
                        "rosterName", "First Company", "factionId", faction, "ducatLimit", 700))).get("id").asLong();
                String path = "/api/rosters/" + rosterId;
                unitId = expect(200, client.get(path + "/units")).get("availableUnits").get(0).get("id").asLong();
                expect(201, client.postJson(path + "/units", Map.of("unitId", unitId)));
                var units = expect(201, client.postJson(path + "/units", Map.of("unitId", unitId))).get("units");
                long removed = units.get(0).get("id").asLong();
                survivor = units.get(1).get("id").asLong();
                assertNotEquals(removed, survivor);
                expect(200, write(client, port(app), "DELETE", path + "/units/" + removed, null));
                expect(200, write(client, port(app), "PUT", path, Map.of(
                        "rosterName", "Second Company", "factionId", faction, "ducatLimit", 900)));
                expect(204, client.logout());
                expect(401, client.get(path + "/units"));
            } catch (Throwable failure) {
                cleanup(jdbc, username);
                throw failure;
            }
        }

        try (var app = start(); var client = new AuthTestClient(port(app))) {
            var jdbc = app.getBean(JdbcTemplate.class);
            try {
                String path = "/api/rosters/" + rosterId;
                expect(401, client.get(path));
                expect(200, client.login(username, PASSWORD));
                var dashboard = expect(200, client.get("/api/rosters"));
                assertEquals(1, dashboard.size());
                assertEquals(rosterId, dashboard.get(0).get("id").asLong());
                assertEquals("Second Company", dashboard.get(0).get("rosterName").asString());
                assertEquals(900, dashboard.get(0).get("ducatLimit").asInt());
                assertEquals(1, dashboard.get(0).get("unitCount").asInt());
                var saved = expect(200, client.get(path + "/units"));
                assertEquals(1, saved.get("units").size());
                assertEquals(survivor, saved.get("units").get(0).get("id").asLong());
                assertEquals(unitId, saved.get("units").get(0).get("unitId").asLong());
                expect(204, write(client, port(app), "DELETE", path, null));
                expect(404, client.get(path));
                expect(404, client.get(path + "/units"));
                assertEquals(0, expect(200, client.get("/api/rosters")).size());
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM roster_units WHERE roster_id=?", Integer.class, rosterId));
            } finally {
                cleanup(jdbc, username);
            }
        }
    }
}
