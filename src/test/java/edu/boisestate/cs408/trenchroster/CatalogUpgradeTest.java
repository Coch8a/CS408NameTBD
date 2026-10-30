package edu.boisestate.cs408.trenchroster;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Proves an existing V2 installation upgrades without resetting user data. */
class CatalogUpgradeTest {
    @Test void upgradesExistingSchemaAndPreservesRosterReferences() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:upgrade;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        // Keep this isolated H2 database alive for the duration of the migration test.
        try (var connection = dataSource.getConnection()) {
            Flyway.configure().dataSource(dataSource).target("2").load().migrate();
            var jdbc = new JdbcTemplate(dataSource);
            jdbc.update("INSERT INTO users(username,email,password_hash) VALUES ('Migration-Test','Migration@Example.Invalid','test-only')");
            jdbc.update("INSERT INTO rosters(user_id,faction_id,roster_name,ducat_limit) SELECT u.id,f.id,'Existing roster',700 FROM users u CROSS JOIN factions f WHERE u.username='Migration-Test' AND f.slug='checkpoint-demo'");
            jdbc.update("INSERT INTO roster_units(roster_id,unit_id,quantity) SELECT r.id,u.id,1 FROM rosters r CROSS JOIN units u WHERE r.roster_name='Existing roster' AND u.name='Sample Infantry'");
            jdbc.update("INSERT INTO roster_equipment(roster_unit_id,equipment_id,quantity) SELECT r.id,e.id,1 FROM roster_units r CROSS JOIN equipment e WHERE e.name='Training Rifle'");
            var flyway = Flyway.configure().dataSource(dataSource).load();
            assertEquals(3, flyway.migrate().migrationsExecuted);
            assertEquals("migration-test", jdbc.queryForObject("SELECT username_normalized FROM users", String.class));
            assertEquals("migration@example.invalid", jdbc.queryForObject("SELECT email_normalized FROM users", String.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM roster_equipment", Integer.class));
            assertEquals("Existing roster", jdbc.queryForObject("SELECT roster_name FROM rosters", String.class));
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM units WHERE faction_id IN (SELECT id FROM factions WHERE slug='new-antioch')", Integer.class));
            assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbc.update(
                    "INSERT INTO users(username,email,password_hash,username_normalized,email_normalized) VALUES (?,?,?,?,?)",
                    "MIGRATION-TEST", "another@example.invalid", "test-only", "migration-test", "another@example.invalid"));
            assertEquals(0, flyway.migrate().migrationsExecuted);
        } catch (java.sql.SQLException exception) {
            throw new AssertionError(exception);
        }
    }
}
