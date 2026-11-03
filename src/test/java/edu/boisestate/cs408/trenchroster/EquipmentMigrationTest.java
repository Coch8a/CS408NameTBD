package edu.boisestate.cs408.trenchroster;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentMigrationTest {
    @Test void upgradesExistingHeavyUnitsWithoutReplacingEquipmentAndRerunsSafely() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:gear_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        try (var connection = source.getConnection()) {
            Flyway.configure().dataSource(source).target("5").load().migrate();
            var jdbc = new JdbcTemplate(source);
            jdbc.update("INSERT INTO users(username,email,password_hash,username_normalized,email_normalized) VALUES ('migration','migration@example.invalid','test-only','migration','migration@example.invalid')");
            jdbc.update("INSERT INTO rosters(user_id,faction_id,roster_name,ducat_limit) SELECT u.id,f.id,'Existing',700 FROM users u CROSS JOIN factions f WHERE f.slug='new-antioch'");
            long roster = jdbc.queryForObject("SELECT id FROM rosters", Long.class);
            long heavy = jdbc.queryForObject("SELECT id FROM units WHERE name='Mechanized Heavy Infantry'", Long.class);
            jdbc.update("INSERT INTO roster_units(roster_id,unit_id) VALUES (?,?),(?,?)",roster,heavy,roster,heavy);
            var ids=jdbc.queryForList("SELECT id FROM roster_units ORDER BY id",Long.class);
            long machine=jdbc.queryForObject("SELECT id FROM equipment WHERE name='Machine Armour'",Long.class);
            long gun=jdbc.queryForObject("SELECT id FROM equipment WHERE name='Machine Gun'",Long.class);
            jdbc.update("INSERT INTO roster_equipment(roster_unit_id,equipment_id) VALUES (?,?),(?,?)",ids.get(0),machine,ids.get(1),gun);
            var flyway=Flyway.configure().dataSource(source).load();
            assertEquals(1,flyway.migrate().migrationsExecuted);
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM roster_equipment WHERE roster_unit_id=?",Integer.class,ids.get(0)));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM roster_equipment WHERE roster_unit_id=?",Integer.class,ids.get(1)));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM roster_equipment re JOIN equipment e ON e.id=re.equipment_id WHERE re.roster_unit_id=? AND e.name='Reinforced Armour'",Integer.class,ids.get(1)));
            assertEquals(0,flyway.migrate().migrationsExecuted);
            assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM roster_equipment",Integer.class));
        }
    }
}
