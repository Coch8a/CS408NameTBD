package edu.boisestate.cs408.trenchroster;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
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

/** Tests login, cookies, isolation and logout over HTTP, without mocked authentication. */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionAuthenticationTest {
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    private final String username = "s" + UUID.randomUUID().toString().replace("-", "");
    private final String email = username + "@example.invalid";
    private static final String PASSWORD = "Local session test 123!";

    @AfterEach void cleanUpOnlyThisTestsAccount() {
        jdbc.update("DELETE FROM users WHERE email_normalized=?", email);
    }

    private void register(AuthTestClient client) throws Exception {
        var response = client.postJson("/api/auth/register", Map.of("username", username, "email", email,
                "password", PASSWORD, "confirmPassword", PASSWORD));
        assertEquals(201, response.statusCode(), response.body());
    }

    private HttpResponse<String> replaySession(String sessionId) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/me"))
                    .header("Cookie", "JSESSIONID=" + sessionId).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test void anonymousUsersGetJson401AndPublicPagesStillLoad() throws Exception {
        try (var client = new AuthTestClient(port)) {
            var response = client.get("/api/auth/me");
            assertEquals(401, response.statusCode());
            assertTrue(response.body().contains("Sign in to continue."));
            assertTrue(response.headers().firstValue("location").isEmpty());
            assertTrue(response.headers().firstValue("www-authenticate").isEmpty());
            assertEquals("", client.sessionId());
            for (String path : new String[]{"/", "/api/health", "/api/catalog", "/api/checkpoint"}) {
                assertEquals(200, client.get(path).statusCode(), path);
            }
            assertEquals(401, client.get("/api/private/future-feature").statusCode());
        }
    }

    @Test void registrationDoesNotSignInAndLoginPersistsOnlyInItsCookieJar() throws Exception {
        try (var client = new AuthTestClient(port); var other = new AuthTestClient(port)) {
            register(client);
            assertEquals(401, client.get("/api/auth/me").statusCode());
            var login = client.login("  " + username.toUpperCase(Locale.ROOT) + "  ", PASSWORD);
            assertEquals(200, login.statusCode(), login.body());
            assertTrue(login.headers().firstValue("location").isEmpty());
            assertFalse(login.body().contains(PASSWORD));
            for (int request = 0; request < 2; request++) {
                var me = client.get("/api/auth/me");
                assertEquals(200, me.statusCode(), me.body());
                var account = AuthTestClient.JSON.readTree(me.body());
                assertEquals(3, account.size());
                assertTrue(account.get("id").asLong() > 0);
                assertEquals(username, account.get("username").asString());
                assertEquals(email, account.get("email").asString());
                assertFalse(me.body().contains("password"));
                assertFalse(me.body().contains("bcrypt"));
                assertEquals("no-store", me.headers().firstValue("cache-control").orElseThrow());
            }
            assertEquals(401, other.get("/api/auth/me").statusCode());
        }
    }

    @Test void wrongPasswordUnknownAccountAndEmptyCredentialsReturnSameFailure() throws Exception {
        try (var client = new AuthTestClient(port)) {
            register(client);
            var wrong = client.login(username, "wrong password");
            var unknown = client.login("missing_" + username, PASSWORD);
            var empty = client.login("", "");
            var oversized = client.login(username, "x".repeat(73));
            for (var response : java.util.List.of(wrong, unknown, empty, oversized)) {
                assertEquals(401, response.statusCode(), response.body());
                assertEquals(wrong.body(), response.body());
                assertFalse(response.body().contains(username));
                assertFalse(response.body().contains(PASSWORD));
                assertFalse(response.body().contains("Exception"));
            }
            assertEquals(401, client.get("/api/auth/me").statusCode());
        }
    }

    @Test void loginRotatesSessionIdAndInvalidatesPreLoginCsrfToken() throws Exception {
        try (var client = new AuthTestClient(port)) {
            register(client);
            String oldToken = client.csrf();
            String oldSession = client.sessionId();
            assertFalse(oldSession.isEmpty());
            assertEquals(200, client.login(username, PASSWORD).statusCode());
            assertNotEquals(oldSession, client.sessionId());
            assertEquals(401, replaySession(oldSession).statusCode());
            var staleToken = client.send(client.postRequest("/api/auth/logout", "",
                    "application/x-www-form-urlencoded", oldToken));
            assertEquals(403, staleToken.statusCode());
            assertEquals(200, client.get("/api/auth/me").statusCode());
            assertEquals(204, client.logout().statusCode());
        }
    }

    @Test void logoutInvalidatesSessionAndOldCookieCannotBeReused() throws Exception {
        try (var client = new AuthTestClient(port)) {
            register(client);
            assertEquals(200, client.login(username, PASSWORD).statusCode());
            String session = client.sessionId();
            var logout = client.logout();
            assertEquals(204, logout.statusCode());
            assertEquals("", logout.body());
            assertTrue(logout.headers().allValues("set-cookie").stream()
                    .flatMap(header -> java.net.HttpCookie.parse(header).stream())
                    .anyMatch(cookie -> cookie.getName().equals("JSESSIONID") && cookie.hasExpired()),
                    logout.headers().allValues("set-cookie").toString());
            assertEquals("", client.sessionId());
            assertEquals(401, client.get("/api/auth/me").statusCode());
            assertEquals(401, replaySession(session).statusCode());
            assertEquals(200, client.login(username, PASSWORD).statusCode());
        }
    }

    @Test void missingAndInvalidCsrfTokensRejectEveryAuthWrite() throws Exception {
        try (var client = new AuthTestClient(port)) {
            client.csrf();
            for (String path : new String[]{"/api/auth/register", "/api/auth/login", "/api/auth/logout"}) {
                for (String token : new String[]{null, "invalid-token"}) {
                    var response = client.send(client.postRequest(path, "{}", "application/json", token));
                    assertEquals(403, response.statusCode(), path + ": " + response.body());
                    assertTrue(response.body().contains("CSRF token"));
                    assertTrue(response.headers().firstValue("location").isEmpty());
                }
            }
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email_normalized=?", Integer.class, email));
    }

    @Test void anotherBrowserCannotUseYourCsrfToken() throws Exception {
        try (var first = new AuthTestClient(port); var second = new AuthTestClient(port)) {
            String token = first.csrf();
            second.csrf();
            var response = second.send(second.postRequest("/api/auth/login",
                    AuthTestClient.form(Map.of("username", username, "password", PASSWORD)),
                    "application/x-www-form-urlencoded", token));
            assertEquals(403, response.statusCode());
        }
    }

    @Test void csrfEndpointSetsHttpOnlySameSiteSessionCookie() throws Exception {
        try (var client = new AuthTestClient(port)) {
            var response = client.get("/api/auth/csrf");
            assertEquals(200, response.statusCode());
            var body = AuthTestClient.JSON.readTree(response.body());
            assertEquals("X-CSRF-TOKEN", body.get("headerName").asString());
            assertFalse(body.get("token").asString().isBlank());
            assertEquals("no-store", response.headers().firstValue("cache-control").orElseThrow());
            String cookie = response.headers().firstValue("set-cookie").orElseThrow();
            assertTrue(cookie.contains("HttpOnly"));
            assertTrue(cookie.contains("SameSite=Lax"));
        }
    }

    @Test void getRequestCannotLogOutSignedInUser() throws Exception {
        try (var client = new AuthTestClient(port)) {
            register(client);
            assertEquals(200, client.login(username, PASSWORD).statusCode());
            assertEquals(404, client.get("/api/auth/logout").statusCode());
            assertEquals(200, client.get("/api/auth/me").statusCode());
        }
    }

    @Test void deletedAccountCannotRetrieveProfileWithOldSession() throws Exception {
        try (var client = new AuthTestClient(port)) {
            register(client);
            assertEquals(200, client.login(username, PASSWORD).statusCode());
            String session = client.sessionId();
            jdbc.update("DELETE FROM users WHERE email_normalized=?", email);
            assertEquals(401, client.get("/api/auth/me").statusCode());
            assertEquals(401, replaySession(session).statusCode());
        }
    }
}
