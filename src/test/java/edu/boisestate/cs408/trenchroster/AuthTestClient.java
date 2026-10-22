package edu.boisestate.cs408.trenchroster;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import tools.jackson.databind.json.JsonMapper;

/** A separate browser cookie jar per test client; requests use the real security filters. */
final class AuthTestClient implements AutoCloseable {
    static final JsonMapper JSON = JsonMapper.builder().build();
    private final String base;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    AuthTestClient(int port) { base = "http://127.0.0.1:" + port; }

    HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20)).GET().build());
    }

    String csrf() throws Exception {
        var response = get("/api/auth/csrf");
        if (response.statusCode() != 200) throw new AssertionError(response.body());
        return JSON.readTree(response.body()).get("token").asString();
    }

    HttpRequest postRequest(String path, String body, String contentType, String token) {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("X-CSRF-TOKEN", token);
        return request.build();
    }

    HttpResponse<String> postJson(String path, Object body) throws Exception {
        return postJsonRaw(path, JSON.writeValueAsString(body));
    }

    HttpResponse<String> postJsonRaw(String path, String body) throws Exception {
        return send(postRequest(path, body, "application/json", csrf()));
    }

    HttpResponse<String> login(String username, String password) throws Exception {
        String body = form(Map.of("username", username, "password", password));
        return send(postRequest("/api/auth/login", body, "application/x-www-form-urlencoded", csrf()));
    }

    HttpResponse<String> logout() throws Exception {
        return send(postRequest("/api/auth/logout", "", "application/x-www-form-urlencoded", csrf()));
    }

    HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    CompletableFuture<HttpResponse<String>> sendAsync(HttpRequest request) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    String sessionId() {
        return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID"))
                .map(java.net.HttpCookie::getValue).findFirst().orElse("");
    }

    static String form(Map<String, String> fields) {
        return fields.entrySet().stream().map(entry ->
                URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" +
                URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    }

    @Override public void close() { client.close(); }
}
