package edu.boisestate.cs408.trenchroster;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import edu.boisestate.cs408.trenchroster.roster.*;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentPersistenceTest {
    ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(TrenchRosterApplication.class).profiles("test")
                .bannerMode(Banner.Mode.OFF).registerShutdownHook(false).run("--server.port=0","--server.address=127.0.0.1");
    }
    @Test void savedLoadoutAndCostsSurviveApplicationRestart() {
        String name="persist_"+UUID.randomUUID().toString().replace("-", "");
        long owner,roster,entry;
        try(var app=start()) {
            var jdbc=app.getBean(JdbcTemplate.class);
            jdbc.update("INSERT INTO users(username,email,password_hash,username_normalized,email_normalized) VALUES (?,?,?,?,?)",name,name+"@example.invalid","unused",name,name+"@example.invalid");
            owner=jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name);
            long faction=jdbc.queryForObject("SELECT id FROM factions WHERE slug='new-antioch'",Long.class);
            roster=app.getBean(RosterService.class).create(owner,new RosterRequest("Persistence",faction,90)).id();
            long unit=jdbc.queryForObject("SELECT id FROM units WHERE name='Mechanized Heavy Infantry'",Long.class);
            entry=app.getBean(RosterUnitService.class).add(owner,roster,unit).units().getFirst().id();
            long armour=jdbc.queryForObject("SELECT id FROM equipment WHERE name='Machine Armour'",Long.class);
            var result=app.getBean(RosterEquipmentService.class).set(owner,roster,entry,armour,1);
            assertEquals(95,result.roster().totalCost());assertEquals(5,result.roster().overBudget());
        }
        try(var app=start()) {
            var jdbc=app.getBean(JdbcTemplate.class);
            try {
                var result=app.getBean(RosterEquipmentService.class).view(owner,roster,entry);
                assertEquals(95,result.unitCost().totalCost());assertEquals(10,result.unitCost().equipmentCost());
                assertEquals("Machine Armour",result.equipment().getFirst().name());
                assertEquals(-5,result.roster().remainingDucats());assertEquals(5,result.roster().overBudget());
                var units=app.getBean(RosterUnitService.class);
                assertEquals(95,units.view(owner,roster).units().getFirst().costs().totalCost());
                assertEquals(0,units.remove(owner,roster,entry).roster().totalCost());
            } finally {
                jdbc.update("DELETE FROM rosters WHERE user_id=?",owner);
                jdbc.update("DELETE FROM users WHERE id=?",owner);
            }
        }
    }
}
