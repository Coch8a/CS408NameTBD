package edu.boisestate.cs408.trenchroster;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the actual HTTP responses the browser and deployment script need. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebEndpointsTest {
    @Value("${local.server.port}")
    private int port;

    private HttpResponse<String> get(String path) throws Exception {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void landingPageIsPublicAndShowsHelloWorld() throws Exception {
        HttpResponse<String> response = get("/");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Hello World!"));
    }

    @Test
    void healthEndpointIsPublicAndIdentifiesTheApp() throws Exception {
        HttpResponse<String> response = get("/api/health");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""));
        assertTrue(response.body().contains("\"app\":\"trench-roster\""));
    }
}
