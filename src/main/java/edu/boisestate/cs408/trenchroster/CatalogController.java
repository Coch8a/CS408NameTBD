package edu.boisestate.cs408.trenchroster;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only New Antioch catalogue and database diagnostics. */
@RestController
public class CatalogController {
    private static final List<String> TABLES = List.of(
            "users", "factions", "units", "equipment", "rosters", "roster_units", "roster_equipment");
    private final JdbcTemplate jdbc;
    public CatalogController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/api/catalog")
    public ResponseEntity<?> catalog() {
        try {
            List<Map<String, Object>> factions = jdbc.query(
                    "SELECT id, name, description FROM factions WHERE slug = 'new-antioch' ORDER BY id",
                    (row, index) -> Map.of("id", row.getLong("id"), "name", row.getString("name"),
                            "description", row.getString("description")));
            List<Map<String, Object>> units = jdbc.query(
                    "SELECT id, faction_id, name, base_cost, unit_type, unit_limit, cost_notes, alternate_cost FROM units WHERE faction_id IN (SELECT id FROM factions WHERE slug = 'new-antioch') ORDER BY id",
                    (row, index) -> Map.of("id", row.getLong("id"), "factionId", row.getLong("faction_id"),
                            "name", row.getString("name"), "cost", row.getInt("base_cost"),
                            "type", row.getString("unit_type"), "limit", row.getInt("unit_limit"),
                            "notes", row.getString("cost_notes"), "alternateCost", row.getInt("alternate_cost")));
            List<Map<String, Object>> equipment = jdbc.query(
                    "SELECT id, name, cost, equipment_type, cost_notes FROM equipment WHERE faction_id IN (SELECT id FROM factions WHERE slug = 'new-antioch') ORDER BY id",
                    (row, index) -> Map.of("id", row.getLong("id"), "name", row.getString("name"),
                            "cost", row.getInt("cost"), "type", row.getString("equipment_type"), "notes", row.getString("cost_notes")));
            return ResponseEntity.ok(Map.of("sampleData", false, "rulesVersion", "1.0.2",
                    "sourceUrl", "https://www.trenchcrusade.com/app/uploads/2026/09/Warbands-of-Trench-Crusade.pdf",
                    "factions", factions,
                    "units", units, "equipment", equipment));
        } catch (DataAccessException exception) {
            return ResponseEntity.status(503).body(Map.of("message", "The catalogue is temporarily unavailable."));
        }
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
