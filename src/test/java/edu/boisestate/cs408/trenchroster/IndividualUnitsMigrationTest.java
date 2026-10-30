package edu.boisestate.cs408.trenchroster;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class IndividualUnitsMigrationTest {
    @Test void splitsGroupedRowsPreservingIdsLoadoutsAndTotals() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:individual_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        try (var connection = source.getConnection()) {
            Flyway.configure().dataSource(source).target("4").load().migrate();
            var jdbc = new JdbcTemplate(source);
            jdbc.update("""
                    INSERT INTO users(username,email,password_hash,username_normalized,email_normalized)
                    VALUES ('migration','migration@example.invalid','test-only','migration','migration@example.invalid')
                    """);
            jdbc.update("""
                    INSERT INTO rosters(user_id,faction_id,roster_name,ducat_limit)
                    SELECT u.id,f.id,'Existing',700 FROM users u CROSS JOIN factions f WHERE f.slug='new-antioch'
                    """);
            long roster = jdbc.queryForObject("SELECT id FROM rosters", Long.class);
            long unit = jdbc.queryForObject("SELECT id FROM units WHERE name='Yeoman'", Long.class);
            long gear = jdbc.queryForObject("SELECT MIN(id) FROM equipment", Long.class);
            jdbc.update("INSERT INTO roster_units(roster_id,unit_id,quantity) VALUES (?,?,3)", roster, unit);
            long original = jdbc.queryForObject("SELECT id FROM roster_units", Long.class);
            jdbc.update("INSERT INTO roster_equipment(roster_unit_id,equipment_id,quantity) VALUES (?,?,2)", original, gear);
            long originalGear = jdbc.queryForObject("SELECT id FROM roster_equipment", Long.class);
            jdbc.update("INSERT INTO roster_units(roster_id,unit_id,quantity) VALUES (?,?,1)", roster, unit);
            var flyway = Flyway.configure().dataSource(source).load();
            assertEquals(1, flyway.migrate().migrationsExecuted);
            assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM roster_units", Integer.class));
            assertEquals(4, jdbc.queryForObject("SELECT SUM(quantity) FROM roster_units", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT quantity FROM roster_units WHERE id=?", Integer.class, original));
            assertEquals(original, jdbc.queryForObject("SELECT roster_unit_id FROM roster_equipment WHERE id=?", Long.class, originalGear));
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM roster_equipment WHERE equipment_id=? AND quantity=2", Integer.class, gear));
            assertEquals("Existing", jdbc.queryForObject("SELECT roster_name FROM rosters", String.class));
            assertEquals(0, flyway.migrate().migrationsExecuted);
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE roster_units SET quantity=2 WHERE id=?", original));
            jdbc.update("DELETE FROM roster_units WHERE id=?", original);
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM roster_equipment", Integer.class));
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM roster_units", Integer.class));
        }
    }
}
