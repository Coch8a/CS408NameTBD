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
    @Test void catalogueCombinesUnitFiltersAndRespectsEquipmentEligibility() throws Exception {
        long armour = jdbc.queryForObject("SELECT id FROM equipment WHERE name='Machine Armour'", Long.class);
        var response = get("/api/catalog?faction=new-antioch&unitType=Heavy+infantry&unitMaxCost=85&equipmentId=" + armour);
        assertEquals(200, response.statusCode(), response.body());
        var result = AuthTestClient.JSON.readTree(response.body());
        assertEquals(1, result.get("units").size());
        assertEquals("Mechanized Heavy Infantry", result.get("units").get(0).get("name").asString());
        assertEquals(15, result.get("equipment").size());

        var eligible = get("/api/catalog?equipmentId=" + armour);
        assertEquals(200, eligible.statusCode(), eligible.body());
        var units = AuthTestClient.JSON.readTree(eligible.body()).get("units");
        assertEquals(2, units.size());
        assertEquals("Lieutenant", units.get(0).get("name").asString());
        assertEquals("Mechanized Heavy Infantry", units.get(1).get("name").asString());
    }
    @Test void catalogueMatchesEquipmentNameWithoutCaseAndCombinesTypeAndCost() throws Exception {
        var response = get("/api/catalog?q=RIFlE&equipmentType=Ranged+weapon&equipmentMaxCost=10");
        assertEquals(200, response.statusCode(), response.body());
        var result = AuthTestClient.JSON.readTree(response.body());
        assertEquals(0, result.get("units").size());
        assertEquals(1, result.get("equipment").size());
        assertEquals("Bolt-Action Rifle", result.get("equipment").get(0).get("name").asString());

        var unitResponse = get("/api/catalog?q=yeOMAN&unitMaxCost=30");
        assertEquals(200, unitResponse.statusCode(), unitResponse.body());
        var units = AuthTestClient.JSON.readTree(unitResponse.body()).get("units");
        assertEquals(1, units.size());
        assertEquals("Yeoman", units.get(0).get("name").asString());
    }
    @Test void catalogueReturnsEmptyResultsForLiteralSearchesAndUnsupportedFactions() throws Exception {
        for (String query : new String[]{"q=%25", "faction=training-company"}) {
            var response = get("/api/catalog?" + query);
            assertEquals(200, response.statusCode(), response.body());
            var result = AuthTestClient.JSON.readTree(response.body());
            assertEquals(0, result.get("units").size());
            assertEquals(0, result.get("equipment").size());
        }
        assertEquals(400, get("/api/catalog?unitMaxCost=-1").statusCode());
    }
    @Test void catalogueCostsMatchPublishedRules() {
        assertEquals(70, jdbc.queryForObject("SELECT base_cost FROM units WHERE name='Lieutenant'", Integer.class));
        assertEquals(30, jdbc.queryForObject("SELECT base_cost FROM units WHERE name='Yeoman'", Integer.class));
        assertEquals(85, jdbc.queryForObject("SELECT base_cost FROM units WHERE name='Mechanized Heavy Infantry'", Integer.class));
        assertEquals(95, jdbc.queryForObject("SELECT alternate_cost FROM units WHERE name='Mechanized Heavy Infantry'", Integer.class));
        assertEquals(30, jdbc.queryForObject("SELECT cost FROM equipment WHERE name='Submachine Gun'", Integer.class));
        assertEquals(50, jdbc.queryForObject("SELECT cost FROM equipment WHERE name='Machine Armour'", Integer.class));
        assertEquals(15, jdbc.queryForObject("SELECT COUNT(*) FROM equipment WHERE faction_id IN (SELECT id FROM factions WHERE slug='new-antioch')", Integer.class));
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
