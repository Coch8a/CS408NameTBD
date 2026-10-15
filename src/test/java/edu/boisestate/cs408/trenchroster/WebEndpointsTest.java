package edu.boisestate.cs408.trenchroster;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises HTTP routes, schema constraints, and repeatable migrations. */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebEndpointsTest {
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;

    private HttpResponse<String> get(String path) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    @Test void landingPageServesTheReactBuild() throws Exception {
        var response = get("/");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("id=\"root\""));
        assertTrue(response.body().contains("/assets/"));
    }
    @Test void healthChecksTheDatabase() throws Exception {
        var response = get("/api/health");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"database\":\"UP\""));
    }
    @Test void catalogueReadsNewAntiochAndHidesOldSamples() throws Exception {
        var response = get("/api/catalog");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("New Antioch"));
        assertFalse(response.body().contains("Training Company"));
        assertTrue(response.body().contains("Lieutenant"));
        assertTrue(response.body().contains("Bolt-Action Rifle"));
        assertTrue(response.body().contains("\"sampleData\":false"));
    }
    @Test void catalogueCostsMatchPublishedRules() {
        assertEquals(70, jdbc.queryForObject("SELECT base_cost FROM units WHERE name='Lieutenant'", Integer.class));
        assertEquals(30, jdbc.queryForObject("SELECT base_cost FROM units WHERE name='Yeoman'", Integer.class));
        assertEquals(85, jdbc.queryForObject("SELECT base_cost FROM units WHERE name='Mechanized Heavy Infantry'", Integer.class));
        assertEquals(95, jdbc.queryForObject("SELECT alternate_cost FROM units WHERE name='Mechanized Heavy Infantry'", Integer.class));
        assertEquals(30, jdbc.queryForObject("SELECT cost FROM equipment WHERE name='Submachine Gun'", Integer.class));
        assertEquals(50, jdbc.queryForObject("SELECT cost FROM equipment WHERE name='Machine Armour'", Integer.class));
        assertEquals(14, jdbc.queryForObject("SELECT COUNT(*) FROM equipment WHERE faction_id IN (SELECT id FROM factions WHERE slug='new-antioch')", Integer.class));
    }
    @Test void schemaReportsEveryProposalTableWithoutUserData() throws Exception {
        var response = get("/api/checkpoint");
        assertEquals(200, response.statusCode());
        for (String table : new String[]{"users", "factions", "units", "equipment", "rosters", "roster_units", "roster_equipment"}) {
            assertTrue(response.body().contains("\"" + table + "\":"));
        }
        assertFalse(response.body().contains("password_hash"));
    }
    @Test void migrationsDoNotDuplicateSeedData() {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM units", Long.class);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM units", Long.class));
    }
    @Test void databaseRejectsNegativeCosts() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO equipment (name, cost, equipment_type) VALUES (?, ?, ?)",
                "Invalid test equipment", -1, "Utility"));
    }
    @Test void databaseRejectsMissingFactionReferences() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO units (faction_id, name, base_cost, unit_type) VALUES (?, ?, ?, ?)",
                -999, "Orphan test unit", 10, "Infantry"));
    }
    @Test void testDatabaseMatchesRequestedEngine() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DB_URL") != null,
                "Run ./test.sh for the real PostgreSQL integration suite");
        assertTrue(jdbc.queryForObject("SELECT version()", String.class).contains("PostgreSQL"));
    }
}
