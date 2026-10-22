package edu.boisestate.cs408.trenchroster;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationTest {
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    private final String prefix = "r" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = "A test passphrase 123!";
    private final JsonMapper json = JsonMapper.builder().build();

    @AfterEach void cleanUpOnlyThisTestsAccounts() {
        jdbc.update("DELETE FROM users WHERE email_normalized LIKE ?", prefix + "%@example.invalid");
    }

    private Map<String, String> valid(String suffix) {
        return Map.of("username", prefix + suffix, "email", prefix + suffix + "@example.invalid",
                "password", PASSWORD, "confirmPassword", PASSWORD);
    }
    private HttpRequest request(String body) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/register"))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }
    private HttpResponse<String> post(Object body) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request(json.writeValueAsString(body)), HttpResponse.BodyHandlers.ofString());
        }
    }
    private String hash(String suffix) {
        return jdbc.queryForObject("SELECT password_hash FROM users WHERE email_normalized=?", String.class,
                prefix + suffix + "@example.invalid");
    }
    private int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email_normalized LIKE ?", Integer.class,
                prefix + "%@example.invalid");
    }

    @Test void createsAccountWithSafeResponseAndSaltedHash() throws Exception {
        var response = post(valid("a"));
        assertEquals(201, response.statusCode(), response.body());
        var body = json.readTree(response.body());
        assertEquals(3, body.size());
        assertTrue(body.get("id").asLong() > 0);
        assertEquals(prefix + "a", body.get("username").asText());
        assertEquals(prefix + "a@example.invalid", body.get("email").asText());
        assertFalse(response.body().contains(PASSWORD));
        assertFalse(response.body().contains("password"));
        assertEquals("no-store", response.headers().firstValue("cache-control").orElseThrow());
        assertTrue(response.headers().allValues("set-cookie").isEmpty());
        assertTrue(hash("a").startsWith("{bcrypt}"));
        assertTrue(encoder.matches(PASSWORD, hash("a")));
        assertFalse(encoder.matches("incorrect password", hash("a")));
        assertEquals(201, post(valid("b")).statusCode());
        assertNotEquals(hash("a"), hash("b"));
    }

    @Test void trimsIdentityFieldsButPreservesPasswordSpaces() throws Exception {
        var body = new java.util.HashMap<>(valid("a"));
        body.put("username", "  " + prefix + "A  ");
        body.put("email", "  " + (prefix + "a@example.invalid").toUpperCase(java.util.Locale.ROOT) + "  ");
        body.put("password", " " + PASSWORD + " ");
        body.put("confirmPassword", " " + PASSWORD + " ");
        var response = post(body);
        assertEquals(201, response.statusCode(), response.body());
        assertEquals(prefix + "A", json.readTree(response.body()).get("username").asText());
        assertTrue(encoder.matches(" " + PASSWORD + " ", hash("a")));
        assertFalse(encoder.matches(PASSWORD, hash("a")));
    }

    @Test void rejectsCaseInsensitiveUsernameAndEmailDuplicates() throws Exception {
        assertEquals(201, post(valid("a")).statusCode());
        var usernameDuplicate = new java.util.HashMap<>(valid("b"));
        usernameDuplicate.put("username", (prefix + "a").toUpperCase(java.util.Locale.ROOT));
        var emailDuplicate = new java.util.HashMap<>(valid("c"));
        emailDuplicate.put("email", (prefix + "a@example.invalid").toUpperCase(java.util.Locale.ROOT));
        for (var body : java.util.List.of(usernameDuplicate, emailDuplicate)) {
            var response = post(body);
            assertEquals(409, response.statusCode(), response.body());
            assertTrue(response.body().contains("Username or email is already in use."));
            assertFalse(response.body().contains("INSERT"));
            assertFalse(response.body().contains(PASSWORD));
        }
        assertEquals(1, count());
    }

    @Test void rejectsMissingFields() throws Exception {
        var response = post(Map.of());
        assertEquals(400, response.statusCode());
        var errors = json.readTree(response.body()).get("errors");
        for (var field : valid("a").keySet()) assertTrue(errors.has(field));
        assertEquals(0, count());
    }

    @Test void rejectsInvalidValuesWithoutInsertingOrEchoingPasswords() throws Exception {
        var invalidValues = Map.of("username", "bad name!", "email", "invalid-email", "password", "short",
                "confirmPassword", "does not match");
        for (var entry : invalidValues.entrySet()) {
            var body = new java.util.HashMap<>(valid("a"));
            body.put(entry.getKey(), entry.getValue());
            var response = post(body);
            assertEquals(400, response.statusCode(), response.body());
            assertTrue(json.readTree(response.body()).get("errors").has(entry.getKey()));
            assertFalse(response.body().contains(PASSWORD));
        }
        assertEquals(0, count());
    }

    @Test void enforcesBcryptByteLimitForUnicodePasswords() throws Exception {
        var body = new java.util.HashMap<>(valid("a"));
        for (String password : new String[]{"x".repeat(73), "é".repeat(37)}) {
            body.put("password", password);
            body.put("confirmPassword", password);
            assertEquals(400, post(body).statusCode());
        }
        assertEquals(0, count());
        body.put("password", "é".repeat(36));
        body.put("confirmPassword", "é".repeat(36));
        var response = post(body);
        assertEquals(201, response.statusCode(), response.body());
        assertTrue(encoder.matches("é".repeat(36), hash("a")));
    }

    @Test void rejectsMalformedJsonWithoutLeakingInput() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request("{\"password\":\"secret-never-echo\","), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, response.statusCode());
            assertFalse(response.body().contains("secret-never-echo"));
            assertFalse(response.body().contains("Exception"));
        }
        assertEquals(0, count());
    }

    @Test void simultaneousDuplicatesCreateExactlyOneAccount() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = request(json.writeValueAsString(valid("a")));
            var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            var statuses = Stream.of(first.join().statusCode(), second.join().statusCode()).sorted().toList();
            assertEquals(java.util.List.of(201, 409), statuses);
        }
        assertEquals(1, count());
    }

    @Test void requestToStringRedactsCredentials() {
        var request = new edu.boisestate.cs408.trenchroster.auth.RegistrationRequest(
                "example", "example@example.invalid", PASSWORD, PASSWORD);
        assertFalse(request.toString().contains(PASSWORD));
        assertFalse(request.toString().contains("example@example.invalid"));
    }
}
