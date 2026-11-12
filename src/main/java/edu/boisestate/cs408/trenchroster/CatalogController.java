package edu.boisestate.cs408.trenchroster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only New Antioch catalogue and database diagnostics. */
@RestController
public class CatalogController {
    private static final List<String> TABLES = List.of(
            "users", "factions", "units", "equipment", "rosters", "roster_units", "roster_equipment");
    private final JdbcTemplate jdbc;
    public CatalogController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/api/catalog")
    public ResponseEntity<?> catalog(
            @RequestParam(defaultValue = "new-antioch") String faction,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String unitType,
            @RequestParam(required = false) Integer unitMaxCost,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(defaultValue = "") String equipmentType,
            @RequestParam(required = false) Integer equipmentMaxCost,
            @RequestParam(defaultValue = "false") boolean alphabetical) {
        if ((unitMaxCost != null && unitMaxCost < 0) || (equipmentMaxCost != null && equipmentMaxCost < 0)
                || (equipmentId != null && equipmentId < 1)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Enter nonnegative costs and a valid equipment ID."));
        }
        String slug = faction.strip().toLowerCase(Locale.ROOT);
        String name = q.strip().toLowerCase(Locale.ROOT);
        try {
            List<Map<String, Object>> factions = jdbc.query(
                    "SELECT id, name, description FROM factions WHERE slug = 'new-antioch' ORDER BY id",
                    (row, index) -> Map.of("id", row.getLong("id"), "name", row.getString("name"),
                            "description", row.getString("description")));

            var unitSql = new StringBuilder("""
                    SELECT u.id, u.faction_id, u.name, u.base_cost, u.unit_type, u.unit_limit, u.cost_notes, u.alternate_cost
                    FROM units u JOIN factions f ON f.id = u.faction_id
                    WHERE f.slug = 'new-antioch' AND f.slug = ?
                    """);
            var unitArgs = new ArrayList<Object>(List.of(slug));
            filter(unitSql, unitArgs, "POSITION(? IN LOWER(u.name)) > 0", name.isEmpty() ? null : name);
            filter(unitSql, unitArgs, "u.unit_type = ?", unitType.isBlank() ? null : unitType.strip());
            filter(unitSql, unitArgs, "u.base_cost <= ?", unitMaxCost);
            filter(unitSql, unitArgs, """
                    EXISTS (SELECT 1 FROM unit_equipment_rules r WHERE r.unit_id = u.id AND r.equipment_id = ?)
                    """, equipmentId);
            unitSql.append(alphabetical ? " ORDER BY LOWER(u.name), u.id" : " ORDER BY u.id").append(" LIMIT 200");
            List<Map<String, Object>> units = jdbc.query(unitSql.toString(),
                    (row, index) -> Map.of("id", row.getLong("id"), "factionId", row.getLong("faction_id"),
                            "name", row.getString("name"), "cost", row.getInt("base_cost"),
                            "type", row.getString("unit_type"), "limit", row.getInt("unit_limit"),
                            "notes", row.getString("cost_notes"), "alternateCost", row.getInt("alternate_cost")),
                    unitArgs.toArray());

            var equipmentSql = new StringBuilder("""
                    SELECT e.id, e.name, e.cost, e.equipment_type, e.cost_notes
                    FROM equipment e JOIN factions f ON f.id = e.faction_id
                    WHERE f.slug = 'new-antioch' AND f.slug = ?
                    """);
            var equipmentArgs = new ArrayList<Object>(List.of(slug));
            filter(equipmentSql, equipmentArgs, "POSITION(? IN LOWER(e.name)) > 0", name.isEmpty() ? null : name);
            filter(equipmentSql, equipmentArgs, "e.equipment_type = ?", equipmentType.isBlank() ? null : equipmentType.strip());
            filter(equipmentSql, equipmentArgs, "e.cost <= ?", equipmentMaxCost);
            equipmentSql.append(alphabetical ? " ORDER BY LOWER(e.name), e.id" : " ORDER BY e.id").append(" LIMIT 200");
            List<Map<String, Object>> equipment = jdbc.query(equipmentSql.toString(),
                    (row, index) -> Map.of("id", row.getLong("id"), "name", row.getString("name"),
                            "cost", row.getInt("cost"), "type", row.getString("equipment_type"),
                            "notes", row.getString("cost_notes")),
                    equipmentArgs.toArray());
            return ResponseEntity.ok(Map.of("sampleData", false, "rulesVersion", "1.0.2",
                    "sourceUrl", "https://www.trenchcrusade.com/app/uploads/2026/09/Warbands-of-Trench-Crusade.pdf",
                    "factions", factions,
                    "units", units, "equipment", equipment));
        } catch (DataAccessException exception) {
            return ResponseEntity.status(503).body(Map.of("message", "The catalogue is temporarily unavailable."));
        }
    }

    private static void filter(StringBuilder sql, List<Object> args, String condition, Object value) {
        if (value == null) return;
        sql.append(" AND ").append(condition);
        args.add(value);
    }

    @GetMapping("/api/checkpoint")
    public ResponseEntity<?> checkpoint() {
        try {
            Map<String, Long> counts = new LinkedHashMap<>();
            // Identifiers come only from this fixed list, never from user input.
            for (String table : TABLES) {
                counts.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
            }
            return ResponseEntity.ok(Map.of("checkpoint", 1, "tables", counts,
                    "authentication", "Planned for Checkpoint 2"));
        } catch (DataAccessException exception) {
            return ResponseEntity.status(503).body(Map.of("message", "Database verification is unavailable."));
        }
    }
}
