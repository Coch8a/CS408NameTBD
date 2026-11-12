package edu.boisestate.cs408.trenchroster;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import edu.boisestate.cs408.trenchroster.roster.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RosterCostsTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired RosterService rosters;
    @Autowired RosterUnitService units;
    @Autowired RosterEquipmentService gear;
    long owner, roster, faction;
    String name="cost_"+UUID.randomUUID().toString().replace("-", "");
    @BeforeEach void setup() {
        jdbc.update("INSERT INTO users(username,email,password_hash,username_normalized,email_normalized) VALUES (?,?,?,?,?)",name,name+"@example.invalid","unused",name,name+"@example.invalid");
        owner=jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name);
        faction=jdbc.queryForObject("SELECT id FROM factions WHERE slug='new-antioch'",Long.class);
        roster=rosters.create(owner,new RosterRequest("Costs",faction,700)).id();
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM rosters WHERE user_id=?",owner);
        jdbc.update("DELETE FROM users WHERE id=?",owner);
        jdbc.update("DELETE FROM units WHERE name=?",name);
    }
    long add(String name) {
        long unit=jdbc.queryForObject("SELECT id FROM units WHERE name=?",Long.class,name);
        var saved=units.add(owner,roster,unit).units();
        return saved.getLast().id();
    }
    RosterEquipmentService.EquipmentView set(long entry,String item,int quantity) {
        long id=jdbc.queryForObject("SELECT id FROM equipment WHERE name=?",Long.class,item);
        return gear.set(owner,roster,entry,id,quantity);
    }
    @Test void emptyRosterHasZeroCostAndFullBudget() {
        var r=rosters.get(owner,roster);
        assertEquals(0,r.totalCost());assertEquals(700,r.remainingDucats());assertEquals(0,r.overBudget());
        assertEquals(0,units.view(owner,roster).units().size());
    }
    @Test void discountsDefaultsAndArmourReplacementProduceExactTotals() {
        long a=add("Yeoman"),b=add("Yeoman"),h=add("Mechanized Heavy Infantry");
        assertEquals(145,rosters.get(owner,roster).totalCost());
        var rifle=set(a,"Bolt-Action Rifle",1);
        assertEquals(30,rifle.unitCost().recruitmentCost());assertEquals(5,rifle.unitCost().equipmentCost());
        assertEquals(35,rifle.unitCost().totalCost());assertEquals(150,rifle.roster().totalCost());
        var machine=set(h,"Machine Armour",1);
        assertEquals(85,machine.unitCost().recruitmentCost());assertEquals(10,machine.unitCost().equipmentCost());
        assertEquals(95,machine.unitCost().totalCost());assertEquals(160,machine.roster().totalCost());
        assertEquals(150,set(h,"Reinforced Armour",1).roster().totalCost());
        units.remove(owner,roster,a);
        assertEquals(115,rosters.get(owner,roster).totalCost());
        assertEquals(30,units.view(owner,roster).units().stream().filter(u->u.id()==b).findFirst().orElseThrow().costs().totalCost());
    }
    @Test void quantitiesAndOrdinaryArmourAreChargedOnce() {
        long entry=add("Lieutenant");
        assertEquals(82,set(entry,"Pistol",2).unitCost().totalCost());
        assertEquals(132,set(entry,"Machine Armour",1).unitCost().totalCost());
        assertEquals(122,set(entry,"Reinforced Armour",1).unitCost().totalCost());
        assertEquals(97,set(entry,"Standard Armour",1).unitCost().totalCost());
        assertEquals(91,set(entry,"Pistol",1).unitCost().totalCost());
        assertEquals(85,set(entry,"Pistol",0).unitCost().totalCost());
        assertEquals(70,set(entry,"Standard Armour",0).unitCost().totalCost());
    }
    @Test void overBudgetIsSavedAndLimitEditsRefreshTheSummary() {
        long entry=add("Lieutenant");
        var exact=rosters.update(owner,roster,new RosterRequest("Costs",faction,70));
        assertEquals(0,exact.remainingDucats());assertEquals(0,exact.overBudget());
        assertTrue(exact.unitIssues().isEmpty());
        var over=set(entry,"Pistol",2).roster();
        assertEquals(82,over.totalCost());assertEquals(-12,over.remainingDucats());assertEquals(12,over.overBudget());
        var fixed=set(entry,"Pistol",0).roster();
        assertEquals(0,fixed.overBudget());assertEquals(70,fixed.totalCost());
        set(entry,"Pistol",2);
        var changed=rosters.update(owner,roster,new RosterRequest("Costs",faction,100));
        assertEquals(18,changed.remainingDucats());assertEquals(0,changed.overBudget());
    }
    @Test void requiredLieutenantWarningClearsAndLegacyDuplicatesCanBeRemoved() {
        assertEquals(java.util.List.of("Add a Lieutenant."), rosters.get(owner, roster).unitIssues());
        long entry = add("Lieutenant");
        assertTrue(units.view(owner, roster).roster().unitIssues().isEmpty());
        var rejected = assertThrows(RosterService.InvalidRoster.class, () -> add("Lieutenant"));
        assertEquals("Lieutenant: maximum 1 per roster.", rejected.getMessage());
        assertEquals(1, units.view(owner, roster).units().size());

        jdbc.update("INSERT INTO roster_units (roster_id, unit_id) SELECT roster_id, unit_id FROM roster_units WHERE id=?", entry);
        assertEquals(java.util.List.of("Lieutenant: 2 selected (maximum 1)."), rosters.get(owner, roster).unitIssues());
        assertTrue(units.remove(owner, roster, entry).roster().unitIssues().isEmpty());
        long remaining = units.view(owner, roster).units().getFirst().id();
        assertEquals(java.util.List.of("Add a Lieutenant."), units.remove(owner, roster, remaining).roster().unitIssues());
    }

    @Test void heavyInfantryCanReachButNotExceedTheCatalogueLimit() {
        for (int i = 0; i < 3; i++) add("Mechanized Heavy Infantry");
        var rejected = assertThrows(RosterService.InvalidRoster.class, () -> add("Mechanized Heavy Infantry"));
        assertEquals("Mechanized Heavy Infantry: maximum 3 per roster.", rejected.getMessage());
        assertEquals(3, rosters.get(owner, roster).unitCount());
    }

    @Test void rejectedLoadoutsDoNotChangeCostsOrEquipment() {
        long entry=add("Mechanized Heavy Infantry");
        set(entry,"Machine Armour",1);
        assertThrows(RosterService.InvalidRoster.class,()->set(entry,"Trench Shield",1));
        assertEquals(95,rosters.get(owner,roster).totalCost());
        var state=gear.view(owner,roster,entry);
        assertEquals(1,state.equipment().size());assertEquals("Machine Armour",state.equipment().getFirst().name());
        assertEquals(95,state.unitCost().totalCost());
    }
    @Test void allReadPathsAgreeAndOtherOwnersCannotReadCosts() {
        long entry=add("Yeoman");set(entry,"Bolt-Action Rifle",1);
        assertEquals(35,rosters.list(owner).getFirst().totalCost());
        assertEquals(35,rosters.get(owner,roster).totalCost());
        assertEquals(35,units.view(owner,roster).units().getFirst().costs().totalCost());
        assertEquals(35,gear.view(owner,roster,entry).unitCost().totalCost());
        assertThrows(RosterService.MissingRoster.class,()->gear.view(Long.MAX_VALUE,roster,entry));
        assertTrue(rosters.list(Long.MAX_VALUE).isEmpty());
    }
    @Test void totalsUseLongArithmetic() {
        jdbc.update("INSERT INTO units(faction_id,name,base_cost,unit_type) VALUES (?,?,?,?)",faction,name,Integer.MAX_VALUE,"Infantry");
        add(name);add(name);
        assertEquals(4294967294L,rosters.get(owner,roster).totalCost());
        assertEquals(4294966594L,rosters.get(owner,roster).overBudget());
    }
}
