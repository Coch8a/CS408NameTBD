package edu.boisestate.cs408.trenchroster.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {
    private final JdbcTemplate jdbc;

    public ProfileService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Profile<UnitStats>> unit(long id) {
        var rows = jdbc.query("""
                SELECT p.*,s.title,s.version,s.url,
                       COALESCE(SUM(ep.armour_modifier),0) AS armour,
                       COALESCE(MAX(ep.minimum_base_mm),0) AS minimum_base_mm,
                       MIN(ep.charge_bonus) AS charge_bonus,FALSE AS heavy,FALSE AS strong
                FROM unit_profiles p JOIN rule_sources s ON s.code=p.source_code
                LEFT JOIN unit_equipment_rules ur ON ur.unit_id=p.unit_id AND ur.default_selected=TRUE
                LEFT JOIN equipment_profiles ep ON ep.equipment_id=ur.equipment_id
                WHERE p.unit_id=?
                GROUP BY p.unit_id,p.movement_inches,p.movement_type,p.ranged_dice,p.melee_dice,
                         p.innate_armour,p.base_mm,p.source_code,p.source_page,s.title,s.version,s.url
                """, (row, index) -> new Profile<>(unitStats(row), List.of(), source(row)), id);
        return rows.stream().findFirst().map(profile -> new Profile<>(profile.stats(), rules("""
                SELECT r.*,s.title,s.version,s.url FROM unit_profile_rules link
                JOIN profile_rules r ON r.code=link.rule_code JOIN rule_sources s ON s.code=r.source_code
                WHERE link.unit_id=? ORDER BY r.kind,r.name,r.code
                """, id), profile.source()));
    }

    public Optional<Profile<EquipmentStats>> equipment(long id) {
        var rows = jdbc.query("""
                SELECT p.*,e.hands,s.title,s.version,s.url FROM equipment_profiles p
                JOIN equipment e ON e.id=p.equipment_id JOIN rule_sources s ON s.code=p.source_code
                WHERE p.equipment_id=?
                """, (row, index) -> new Profile<>(new EquipmentStats(row.getObject("range_inches", Integer.class),
                row.getBoolean("melee"), row.getInt("hands"), row.getInt("ranged_dice"), row.getInt("melee_dice"),
                row.getInt("armour_modifier")), List.of(), source(row)), id);
        return rows.stream().findFirst().map(profile -> new Profile<>(profile.stats(), rules("""
                SELECT r.*,s.title,s.version,s.url FROM equipment_profile_rules link
                JOIN profile_rules r ON r.code=link.rule_code JOIN rule_sources s ON s.code=r.source_code
                WHERE link.equipment_id=? ORDER BY r.kind,r.name,r.code
                """, id), profile.source()));
    }

    public Map<Long, UnitStats> rosterStats(long ownerId, long rosterId, long entryId) {
        Map<Long, UnitStats> result = new HashMap<>();
        jdbc.query("""
                SELECT ru.id,p.movement_inches,p.movement_type,p.ranged_dice,p.melee_dice,p.innate_armour,p.base_mm,
                       COALESCE(SUM(ep.armour_modifier*re.quantity),0) AS armour,
                       COALESCE(MAX(ep.minimum_base_mm),0) AS minimum_base_mm,MIN(ep.charge_bonus) AS charge_bonus,
                       MAX(CASE WHEN e.heavy THEN 1 ELSE 0 END)>0 AS heavy,
                       EXISTS (SELECT 1 FROM unit_profile_rules link
                               WHERE link.unit_id=ru.unit_id AND link.rule_code='strong') AS strong
                FROM roster_units ru JOIN rosters r ON r.id=ru.roster_id JOIN unit_profiles p ON p.unit_id=ru.unit_id
                LEFT JOIN roster_equipment re ON re.roster_unit_id=ru.id
                LEFT JOIN equipment e ON e.id=re.equipment_id
                LEFT JOIN equipment_profiles ep ON ep.equipment_id=re.equipment_id
                WHERE ru.roster_id=? AND r.user_id=? AND (?=0 OR ru.id=?)
                GROUP BY ru.id,ru.unit_id,p.movement_inches,p.movement_type,p.ranged_dice,p.melee_dice,p.innate_armour,p.base_mm
                """, (RowCallbackHandler) row -> result.put(row.getLong("id"), unitStats(row)), rosterId, ownerId, entryId, entryId);
        return result;
    }

    private List<Rule> rules(String sql, long id) {
        return jdbc.query(sql, (row, index) -> new Rule(row.getString("code"), row.getString("name"),
                row.getString("kind"), row.getString("summary"), source(row)), id);
    }

    private static UnitStats unitStats(ResultSet row) throws SQLException {
        String charge = row.getString("charge_bonus");
        if (row.getBoolean("heavy") && !row.getBoolean("strong")) charge = "0";
        return new UnitStats(row.getInt("movement_inches"), row.getString("movement_type"),
                row.getInt("ranged_dice"), row.getInt("melee_dice"), row.getInt("innate_armour") + row.getInt("armour"),
                Math.max(row.getInt("base_mm"), row.getInt("minimum_base_mm")), charge == null ? "D6" : charge);
    }

    private static Source source(ResultSet row) throws SQLException {
        return new Source(row.getString("title"), row.getString("version"), row.getString("url"), row.getInt("source_page"));
    }

    public record Source(String title, String version, String url, int page) {}
    public record Rule(String code, String name, String kind, String summary, Source source) {}
    public record Profile<T>(T stats, List<Rule> rules, Source source) {}
    public record UnitStats(int movementInches, String movementType, int rangedDice, int meleeDice,
                            int armourModifier, int baseMm, String chargeBonus) {}
    public record EquipmentStats(Integer rangeInches, boolean melee, int hands, int rangedDice, int meleeDice,
                                 int armourModifier) {}
}
