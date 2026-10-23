package edu.boisestate.cs408.trenchroster;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Complete account workflows against real HTTP and the test database, including restart. */
class AuthenticationWorkflowTest {
    private static final String PASSWORD = "Workflow test passphrase 123!";

    private ConfigurableApplicationContext startApplication() {
        // Uses TEST_DB_* from test.sh; otherwise the existing test profile falls back to H2.
        // Random ports keep this separate from the user's running local application.
        return new SpringApplicationBuilder(TrenchRosterApplication.class)
                .profiles("test").bannerMode(Banner.Mode.OFF).registerShutdownHook(false)
                .run("--server.port=0", "--server.address=127.0.0.1");
    }

    private int port(ConfigurableApplicationContext application) {
        return Integer.parseInt(application.getEnvironment().getRequiredProperty("local.server.port"));
    }

    private String uniqueUsername() {
        return "workflow_" + UUID.randomUUID().toString().replace("-", "");
    }

    private void removeTestAccount(JdbcTemplate jdbc, String username) {
        // Never truncate users or reset the database: delete only this test's random account.
        jdbc.update("DELETE FROM users WHERE username_normalized=? AND email_normalized=?",
                username, username + "@example.invalid");
    }

    private long register(AuthTestClient client, String username) throws Exception {
        var response = client.postJson("/api/auth/register", Map.of("username", username,
                "email", username + "@example.invalid", "password", PASSWORD, "confirmPassword", PASSWORD));
        assertEquals(201, response.statusCode(), response.body());
        return AuthTestClient.JSON.readTree(response.body()).get("id").asLong();
    }

    @Test void frontendApiClientCompletesTheAccountWorkflowAgainstTheRunningBackend() throws Exception {
        String username = uniqueUsername();
        try (var application = startApplication()) {
            var jdbc = application.getBean(JdbcTemplate.class);
            Path output = Files.createTempFile("trench-auth-workflow-", ".log");
            Process process = null;
            try {
                Path script = Path.of("src/test/scripts/auth-client-workflow.mjs").toAbsolutePath();
                assertTrue(Files.isRegularFile(script), "Run Maven from the repository root.");
                process = new ProcessBuilder("node", script.toString(), "http://127.0.0.1:" + port(application), username)
                        .redirectErrorStream(true).redirectOutput(output.toFile()).start();
                assertTrue(process.waitFor(90, TimeUnit.SECONDS), "The frontend API workflow timed out.");
                String report = Files.readString(output);
                assertEquals(0, process.exitValue(), report);
                assertTrue(report.contains("PASS: real frontend API client"), report);
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(10, TimeUnit.SECONDS);
                }
                removeTestAccount(jdbc, username);
                Files.deleteIfExists(output);
            }
        }
    }

    @Test void accountAndPasswordSurviveApplicationRestartButTheOldSessionDoesNot() throws Exception {
        String username = uniqueUsername();
        long id;
        String hash;
        String oldSession;
        // The two application contexts use the same test database, but separate HTTP sessions.
        try (var first = startApplication()) {
            var jdbc = first.getBean(JdbcTemplate.class);
            try (var client = new AuthTestClient(port(first))) {
                id = register(client, username);
                hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, id);
                assertEquals(200, client.login(username, PASSWORD).statusCode());
                oldSession = client.sessionId();
                assertFalse(oldSession.isEmpty());
            } catch (Throwable failure) {
                removeTestAccount(jdbc, username);
                throw failure;
            }
        }
        try (var second = startApplication()) {
            var jdbc = second.getBean(JdbcTemplate.class);
            try (var client = new AuthTestClient(port(second)); var raw = HttpClient.newHttpClient()) {
                assertEquals(hash, jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, id));
                var replay = raw.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port(second) + "/api/auth/me"))
                        .timeout(Duration.ofSeconds(10)).header("Cookie", "JSESSIONID=" + oldSession).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(401, replay.statusCode());
                assertEquals(200, client.login(username, PASSWORD).statusCode());
                var profile = client.get("/api/auth/me");
                assertEquals(200, profile.statusCode());
                assertEquals(id, AuthTestClient.JSON.readTree(profile.body()).get("id").asLong());
                assertEquals(204, client.logout().statusCode());
            } finally {
                removeTestAccount(jdbc, username);
            }
        }
    }

    @Test void twoSignedInUsersHaveIndependentProfilesAndLogoutSessions() throws Exception {
        String firstUsername = uniqueUsername();
        String secondUsername = uniqueUsername();
        try (var application = startApplication()) {
            var jdbc = application.getBean(JdbcTemplate.class);
            try (var first = new AuthTestClient(port(application)); var second = new AuthTestClient(port(application))) {
                long firstId = register(first, firstUsername);
                long secondId = register(second, secondUsername);
                assertEquals(200, first.login(firstUsername, PASSWORD).statusCode());
                assertEquals(200, second.login(secondUsername, PASSWORD).statusCode());
                // Supplying someone else's ID must not select a different account.
                var firstProfile = first.get("/api/auth/me?userId=" + secondId);
                var secondProfile = second.get("/api/auth/me?userId=" + firstId);
                assertEquals(200, firstProfile.statusCode());
                assertEquals(200, secondProfile.statusCode());
                assertEquals(firstId, AuthTestClient.JSON.readTree(firstProfile.body()).get("id").asLong());
                assertEquals(secondId, AuthTestClient.JSON.readTree(secondProfile.body()).get("id").asLong());
                assertFalse(firstProfile.body().contains(secondUsername));
                assertFalse(secondProfile.body().contains(firstUsername));
                assertEquals(204, first.logout().statusCode());
                assertEquals(401, first.get("/api/auth/me").statusCode());
                assertEquals(200, second.get("/api/auth/me").statusCode());
                assertEquals(204, second.logout().statusCode());
            } finally {
                removeTestAccount(jdbc, firstUsername);
                removeTestAccount(jdbc, secondUsername);
            }
        }
    }
}
