package edu.boisestate.cs408.trenchroster.roster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class RosterTransferService {
    static final int MAX_FILE_BYTES = 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final RosterService rosters;
    private final RosterUnitService units;
    private final RosterEquipmentService equipment;

    public RosterTransferService(JdbcTemplate jdbc, RosterService rosters, RosterUnitService units,
                                 RosterEquipmentService equipment) {
        this.jdbc = jdbc;
        this.rosters = rosters;
        this.units = units;
        this.equipment = equipment;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot snapshot(long owner, long id) {
        var view = units.view(owner, id);
        var roster = view.roster();
        Map<Long, List<Gear>> gear = new HashMap<>();
        Map<Long, Map<String, Integer>> gearPrices = new HashMap<>();
        jdbc.query("""
                SELECT ru.id, e.name, re.quantity, COALESCE(ur.cost_override,e.cost) AS price FROM roster_units ru
                JOIN roster_equipment re ON re.roster_unit_id=ru.id JOIN equipment e ON e.id=re.equipment_id
                LEFT JOIN unit_equipment_rules ur ON ur.unit_id=ru.unit_id AND ur.equipment_id=e.id
                WHERE ru.roster_id=? ORDER BY ru.id, e.name, e.id
                """, (RowCallbackHandler) row -> {
            long entryId = row.getLong("id");
            String name = row.getString("name");
            gear.computeIfAbsent(entryId, key -> new ArrayList<>()).add(new Gear(name, row.getInt("quantity")));
            gearPrices.computeIfAbsent(entryId, key -> new HashMap<>()).put(name, row.getInt("price"));
        }, id);
        String faction = jdbc.queryForObject("SELECT slug FROM factions WHERE id=?", String.class, roster.factionId());
        var entries = view.units().stream()
                .map(unit -> new Entry(unit.name(), List.copyOf(gear.getOrDefault(unit.id(), List.of())))).toList();
        var file = new RosterFile(1, roster.rosterName(), faction, roster.ducatLimit(), entries);
        return new Snapshot(file, roster.factionName(), roster.totalCost(), roster.overBudget(), roster.unitIssues(),
                view.units().stream().map(unit -> new UnitDetail(unit.type(), unit.costs().totalCost(),
                        Map.copyOf(gearPrices.getOrDefault(unit.id(), Map.of())))).toList());
    }

    @Transactional
    public RosterService.Roster importFile(long owner, JsonNode file) {
        if (!file.isObject() || !file.path("version").isIntegralNumber() || !file.path("version").canConvertToInt()
                || file.path("version").asInt() != 1) {
            throw invalid("Use a version 1 roster JSON file.");
        }
        String name = name(file.path("name"), "Roster name");
        String faction = name(file.path("faction"), "Faction");
        int limit = integer(file.path("ducatLimit"), "Ducat limit", 1, 1_000_000);
        var entries = array(file.path("units"), "Units", 500);

        var factions = jdbc.query("SELECT id FROM factions WHERE slug=?",
                (row, index) -> row.getLong("id"), faction);
        if (factions.isEmpty()) throw invalid("Unknown faction: " + faction + ".");
        long factionId = factions.getFirst();
        var unitIds = ids("SELECT id,name FROM units WHERE faction_id=?", factionId);
        var gearIds = ids("SELECT id,name FROM equipment WHERE faction_id=?", factionId);
        var roster = rosters.create(owner, new RosterRequest(name, factionId, limit));

        int index = 0;
        for (var entry : entries) {
            String label = "Unit " + ++index;
            String unit = name(entry.path("name"), label + " name");
            if (!unitIds.containsKey(unit)) throw invalid(label + ": unknown unit " + unit + ".");
            var loadout = new LinkedHashMap<Long, Integer>();
            for (var item : array(entry.path("equipment"), label + " equipment", 100)) {
                String itemName = name(item.path("name"), label + " equipment name");
                Long itemId = gearIds.get(itemName);
                if (itemId == null) throw invalid(label + ": unknown equipment " + itemName + ".");
                int quantity = integer(item.path("quantity"), label + " equipment quantity", 1, 100);
                if (loadout.putIfAbsent(itemId, quantity) != null) {
                    throw invalid(label + ": list each equipment item once.");
                }
            }
            try {
                long entryId = units.insert(roster.id(), factionId, unitIds.get(unit));
                equipment.replaceImported(roster.id(), entryId, loadout);
            } catch (RosterService.InvalidRoster problem) {
                throw invalid(label + " (" + unit + "): " + problem.getMessage());
            }
        }
        return rosters.get(owner, roster.id());
    }

    private Map<String, Long> ids(String sql, long faction) {
        Map<String, Long> result = new HashMap<>();
        jdbc.query(sql, (RowCallbackHandler) row -> result.put(row.getString("name"), row.getLong("id")), faction);
        return result;
    }

    private static String name(JsonNode value, String label) {
        if (!value.isString() || value.asString().isBlank() || value.asString().strip().length() > 120
                || value.asString().codePoints().anyMatch(Character::isISOControl)) {
            throw invalid(label + " must be text between 1 and 120 characters.");
        }
        return value.asString().strip();
    }

    private static int integer(JsonNode value, String label, int min, int max) {
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < min || value.asInt() > max) {
            throw invalid(label + " must be a whole number from " + min + " to " + max + ".");
        }
        return value.asInt();
    }

    private static JsonNode array(JsonNode value, String label, int max) {
        if (!value.isArray() || value.size() > max) throw invalid(label + " must be an array with at most " + max + " entries.");
        return value;
    }

    static RosterService.InvalidRoster invalid(String message) {
        return new RosterService.InvalidRoster(message, Map.of());
    }

    public record Gear(String name, int quantity) {}
    public record Entry(String name, List<Gear> equipment) {}
    public record RosterFile(int version, String name, String faction, int ducatLimit, List<Entry> units) {}
    public record UnitDetail(String type, long totalCost, Map<String, Integer> prices) {}
    public record Snapshot(RosterFile file, String factionName, long totalCost, long overBudget,
                           List<String> unitIssues, List<UnitDetail> unitDetails) {}
}
