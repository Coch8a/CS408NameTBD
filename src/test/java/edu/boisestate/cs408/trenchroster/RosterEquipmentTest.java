package edu.boisestate.cs408.trenchroster;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RosterEquipmentTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    final String name="gear_"+UUID.randomUUID().toString().replace("-", "");
    final String password="Equipment verification 123!";
    @AfterEach void cleanup() {
        for(String suffix:new String[]{"a","b"}) {
            jdbc.update("DELETE FROM rosters WHERE user_id IN (SELECT id FROM users WHERE username=?)",name+suffix);
            jdbc.update("DELETE FROM users WHERE username=?",name+suffix);
        }
    }
    void signIn(AuthTestClient c,String suffix) throws Exception {
        assertEquals(201,c.postJson("/api/auth/register",Map.of("username",name+suffix,"email",name+suffix+"@example.invalid","password",password,"confirmPassword",password)).statusCode());
        assertEquals(200,c.login(name+suffix,password).statusCode());
    }
    long roster(AuthTestClient c) throws Exception {
        long faction=jdbc.queryForObject("SELECT id FROM factions WHERE slug='new-antioch'",Long.class);
        return json(c.postJson("/api/rosters",Map.of("rosterName","Equipment check","factionId",faction,"ducatLimit",700))).get("id").asLong();
    }
    long add(AuthTestClient c,long roster,String unit) throws Exception {
        long id=jdbc.queryForObject("SELECT id FROM units WHERE name=?",Long.class,unit);
        var response=c.postJson("/api/rosters/"+roster+"/units",Map.of("unitId",id));
        assertEquals(201,response.statusCode(),response.body());
        var units=json(response).get("units");
        return units.get(units.size()-1).get("id").asLong();
    }
    long id(String gear) { return jdbc.queryForObject("SELECT id FROM equipment WHERE name=?",Long.class,gear); }
    String path(long r,long u) { return "/api/rosters/"+r+"/units/"+u+"/equipment"; }
    JsonNode json(HttpResponse<String> r) { return AuthTestClient.JSON.readTree(r.body()); }
    HttpRequest request(AuthTestClient c,String path,String body,boolean csrf) throws Exception {
        var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString(body));
        if(csrf) r.header("X-CSRF-TOKEN",c.csrf());
        return r.build();
    }
    HttpResponse<String> put(AuthTestClient c,long r,long u,String gear,int quantity) throws Exception {
        return c.send(request(c,path(r,u)+"/"+id(gear),"{\"quantity\":"+quantity+"}",true));
    }
    JsonNode ok(HttpResponse<String> r) { assertEquals(200,r.statusCode(),r.body());return json(r); }
    JsonNode find(JsonNode list,String name) {
        for(var value:list) if(value.get("name").asString().equals(name)) return value;
        throw new AssertionError(name);
    }
    @Test void loadoutsAreIndependentPersistentAndUseRecruitmentPrices() throws Exception {
        try(var c=new AuthTestClient(port)) {
            signIn(c,"a");long r=roster(c),u=add(c,r,"Yeoman"),second=add(c,r,"Yeoman");
            var saved=ok(put(c,r,u,"Bolt-Action Rifle",1));
            assertEquals(5,find(saved.get("equipment"),"Bolt-Action Rifle").get("purchaseCost").asInt());
            assertEquals(10,find(saved.get("equipment"),"Bolt-Action Rifle").get("catalogueCost").asInt());
            assertEquals(1,ok(put(c,r,u,"Bolt-Action Rifle",1)).get("equipment").size());
            assertEquals(0,ok(c.get(path(r,second))).get("equipment").size());
            assertEquals(204,c.logout().statusCode());assertEquals(200,c.login(name+"a",password).statusCode());
            assertEquals(1,ok(c.get(path(r,u))).get("equipment").size());
            assertEquals(0,ok(put(c,r,u,"Bolt-Action Rifle",0)).get("equipment").size());
        }
    }
    @Test void heavyArmourDefaultsAndReplacesAtomicallyWithoutFullPrice() throws Exception {
        try(var c=new AuthTestClient(port)) {
            signIn(c,"a");long r=roster(c),u=add(c,r,"Mechanized Heavy Infantry");
            var initial=ok(c.get(path(r,u))).get("equipment");assertEquals(1,initial.size());
            assertEquals(0,find(initial,"Reinforced Armour").get("purchaseCost").asInt());
            assertTrue(initial.get(0).get("includedInRecruitment").asBoolean());
            var machine=ok(put(c,r,u,"Machine Armour",1)).get("equipment");
            assertEquals(1,machine.size());assertEquals(10,find(machine,"Machine Armour").get("purchaseCost").asInt());
            assertEquals(400,put(c,r,u,"Machine Armour",0).statusCode());
            assertEquals(400,put(c,r,u,"Standard Armour",1).statusCode());
            assertEquals("Machine Armour",ok(c.get(path(r,u))).get("equipment").get(0).get("name").asString());
            ok(put(c,r,u,"Reinforced Armour",1));ok(put(c,r,u,"Trench Shield",1));
            assertEquals(400,put(c,r,u,"Machine Armour",1).statusCode());
            assertEquals("Reinforced Armour",find(ok(c.get(path(r,u))).get("equipment"),"Reinforced Armour").get("name").asString());
            long y=add(c,r,"Yeoman");assertEquals(400,put(c,r,y,"Machine Armour",1).statusCode());
            assertEquals(400,put(c,r,y,"Reinforced Armour",1).statusCode());
        }
    }
    @Test void prerequisitesHandsShieldsAndQuantitiesAreChecked() throws Exception {
        try(var c=new AuthTestClient(port)) {
            signIn(c,"a");long r=roster(c),u=add(c,r,"Yeoman");
            assertEquals(400,put(c,r,u,"Bayonet",1).statusCode());
            ok(put(c,r,u,"Bolt-Action Rifle",1));ok(put(c,r,u,"Bayonet",1));
            assertEquals(400,put(c,r,u,"Bolt-Action Rifle",0).statusCode());
            assertEquals(400,put(c,r,u,"Trench Shield",1).statusCode());
            assertEquals(400,put(c,r,u,"Sword/Axe",1).statusCode());
            ok(put(c,r,u,"Bayonet",0));ok(put(c,r,u,"Bolt-Action Rifle",0));
            ok(put(c,r,u,"Shotgun",1));ok(put(c,r,u,"Bayonet",1));ok(put(c,r,u,"Trench Shield",1));
            assertEquals(400,put(c,r,u,"Pistol",1).statusCode());
            ok(put(c,r,u,"Combat Helmet",1));assertEquals(400,put(c,r,u,"Combat Helmet",2).statusCode());
            long v=add(c,r,"Lieutenant");ok(put(c,r,v,"Pistol",2));ok(put(c,r,v,"Sword/Axe",2));
            assertEquals(400,put(c,r,v,"Trench Shield",1).statusCode());
            assertEquals(400,put(c,r,v,"Pistol",3).statusCode());
        }
    }
    @Test void warbandLimitsAndHeavyArmourExemptionAreEnforced() throws Exception {
        try(var c=new AuthTestClient(port)) {
            signIn(c,"a");long r=roster(c),a=add(c,r,"Yeoman"),b=add(c,r,"Yeoman"),d=add(c,r,"Yeoman");
            ok(put(c,r,a,"Submachine Gun",1));ok(put(c,r,b,"Submachine Gun",1));
            assertEquals(400,put(c,r,d,"Submachine Gun",1).statusCode());
            assertFalse(find(ok(c.get(path(r,d))).get("availableEquipment"),"Submachine Gun").get("compatible").asBoolean());
            ok(put(c,r,a,"Submachine Gun",0));ok(put(c,r,d,"Submachine Gun",1));
            long l=add(c,r,"Lieutenant"),h=add(c,r,"Mechanized Heavy Infantry");
            // An older roster can still contain a duplicate Lieutenant.
            jdbc.update("INSERT INTO roster_units (roster_id, unit_id) SELECT roster_id, unit_id FROM roster_units WHERE id=?",l);
            long l2=jdbc.queryForObject("SELECT MAX(id) FROM roster_units WHERE roster_id=?",Long.class,r);
            ok(put(c,r,l,"Machine Armour",1));ok(put(c,r,h,"Machine Armour",1));
            assertEquals(400,put(c,r,l2,"Machine Armour",1).statusCode());
            assertEquals(50,find(ok(c.get(path(r,l))).get("equipment"),"Machine Armour").get("purchaseCost").asInt());
        }
    }
    @Test void otherOwnersCrossRosterIdsAndCsrfCannotChangeEquipment() throws Exception {
        try(var c=new AuthTestClient(port);var other=new AuthTestClient(port);var anon=new AuthTestClient(port)) {
            signIn(c,"a");signIn(other,"b");long r=roster(c),u=add(c,r,"Yeoman"),r2=roster(c);
            assertEquals(404,other.get(path(r,u)).statusCode());
            assertEquals(404,put(other,r,u,"Pistol",1).statusCode());
            assertEquals(404,put(c,r2,u,"Pistol",1).statusCode());
            assertEquals(401,anon.get(path(r,u)).statusCode());
            assertEquals(401,put(anon,r,u,"Pistol",1).statusCode());
            assertEquals(403,c.send(request(c,path(r,u)+"/"+id("Pistol"),"{\"quantity\":1}",false)).statusCode());
            assertEquals(0,ok(c.get(path(r,u))).get("equipment").size());
        }
    }
    @Test void malformedUnknownAndForeignEquipmentAreRejected() throws Exception {
        try(var c=new AuthTestClient(port)) {
            signIn(c,"a");long r=roster(c),u=add(c,r,"Yeoman");
            for(String value:new String[]{"-1","101","1.5","\"1\"","null"}) {
                assertEquals(400,c.send(request(c,path(r,u)+"/"+id("Pistol"),"{\"quantity\":"+value+"}",true)).statusCode());
            }
            for(String body:new String[]{"{}","null","{bad"})
                assertEquals(400,c.send(request(c,path(r,u)+"/"+id("Pistol"),body,true)).statusCode());
            assertEquals(400,put(c,r,u,"Training Rifle",1).statusCode());
            assertEquals(400,c.send(request(c,path(r,u)+"/99999999","{\"quantity\":1}",true)).statusCode());
            assertEquals(0,ok(c.get(path(r,u))).get("equipment").size());
        }
    }
    @Test void simultaneousRequestsCannotExceedWarbandLimit() throws Exception {
        try(var c=new AuthTestClient(port)) {
            signIn(c,"a");long r=roster(c),a=add(c,r,"Yeoman"),b=add(c,r,"Yeoman");
            var first=request(c,path(r,a)+"/"+id("Automatic Rifle"),"{\"quantity\":1}",true);
            var second=request(c,path(r,b)+"/"+id("Automatic Rifle"),"{\"quantity\":1}",true);
            var f=c.sendAsync(first);var s=c.sendAsync(second);
            int fs=f.get().statusCode(),ss=s.get().statusCode();
            assertTrue((fs==200 && ss==400)||(fs==400 && ss==200),fs+" "+ss);
            assertEquals(1,jdbc.queryForObject("SELECT SUM(re.quantity) FROM roster_equipment re JOIN roster_units ru ON ru.id=re.roster_unit_id WHERE ru.roster_id=?",Integer.class,r));
        }
    }
    @Test void deleteEndpointRequiresOwnershipAndCsrfAndRemovesTheSavedItem() throws Exception {
        try (var owner = new AuthTestClient(port); var other = new AuthTestClient(port)) {
            signIn(owner, "a"); signIn(other, "b");
            long roster = roster(owner), entry = add(owner, roster, "Lieutenant");
            ok(put(owner, roster, entry, "Pistol", 2));
            URI uri = URI.create("http://127.0.0.1:" + port + path(roster, entry) + "/" + id("Pistol"));
            assertEquals(403, owner.send(HttpRequest.newBuilder(uri).DELETE().build()).statusCode());
            assertEquals(404, other.send(HttpRequest.newBuilder(uri).header("X-CSRF-TOKEN", other.csrf()).DELETE().build()).statusCode());
            var response = owner.send(HttpRequest.newBuilder(uri).header("X-CSRF-TOKEN", owner.csrf()).DELETE().build());
            assertEquals(0, ok(response).get("equipment").size());
            assertEquals("no-store", response.headers().firstValue("cache-control").orElseThrow());
            assertEquals(70, json(response).get("unitCost").get("totalCost").asLong());
        }
    }

}
