package edu.boisestate.cs408.trenchroster.roster;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RosterEquipmentService {
    private final JdbcTemplate jdbc;
    private final RosterService rosters;
    private final RosterCostService costs;

    public RosterEquipmentService(JdbcTemplate jdbc, RosterService rosters, RosterCostService costs) {
        this.jdbc = jdbc;
        this.rosters = rosters;
        this.costs = costs;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public EquipmentView view(long ownerId, long rosterId, long entryId) {
        var roster = rosters.get(ownerId, rosterId);
        return snapshot(ownerId, roster, unit(rosterId, entryId));
    }

    @Transactional
    public EquipmentView set(long ownerId, long rosterId, long entryId, long equipmentId, int quantity) {
        rosters.lock(ownerId, rosterId);
        var unit = unit(rosterId, entryId);
        var rules = rules(unit);
        var current = quantities(entryId);
        if (!rules.containsKey(equipmentId) && !current.containsKey(equipmentId)) invalid("Choose available equipment.");
        if (quantity < 0 || quantity > 100) invalid("Enter a valid quantity.");
        var next = changed(current, rules, equipmentId, quantity);
        String problem = validate(unit, next, rules, usage(rosterId, entryId));
        if (!problem.isEmpty()) invalid(problem);
        for (long id : current.keySet()) {
            if (!next.containsKey(id)) jdbc.update("DELETE FROM roster_equipment WHERE roster_unit_id=? AND equipment_id=?", entryId, id);
        }
        if (quantity > 0) {
            int updated = jdbc.update("UPDATE roster_equipment SET quantity=? WHERE roster_unit_id=? AND equipment_id=?",
                    quantity, entryId, equipmentId);
            if (updated == 0) jdbc.update("INSERT INTO roster_equipment (roster_unit_id,equipment_id,quantity) VALUES (?,?,?)",
                    entryId, equipmentId, quantity);
        }
        jdbc.update("UPDATE rosters SET updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?", rosterId, ownerId);
        return snapshot(ownerId, rosters.get(ownerId, rosterId), unit);
    }

    private EquipmentView snapshot(long ownerId, RosterService.Roster roster, Unit unit) {
        var rules = rules(unit);
        var selected = quantities(unit.entryId());
        var usage = usage(roster.id(), unit.entryId());
        var equipped = jdbc.query("""
                SELECT e.id,e.name,e.equipment_type,re.quantity,e.cost,
                       COALESCE(ur.cost_override,e.cost) AS purchase_cost,
                       COALESCE(ur.included_in_recruitment,FALSE) AS included
                FROM roster_equipment re JOIN equipment e ON e.id=re.equipment_id
                LEFT JOIN unit_equipment_rules ur ON ur.unit_id=? AND ur.equipment_id=e.id
                WHERE re.roster_unit_id=? ORDER BY e.equipment_type,e.name,e.id
                """, (row, index) -> new Equipped(row.getLong("id"), row.getString("name"),
                row.getString("equipment_type"), row.getInt("quantity"), row.getInt("cost"),
                row.getInt("purchase_cost"), row.getBoolean("included")), unit.unitId(), unit.entryId());
        var available = rules.values().stream().map(rule -> {
            int quantity = selected.getOrDefault(rule.id(), 0);
            int desired = rule.slot().equals("armour") ? 1 : quantity + 1;
            String reason = validate(unit, changed(selected, rules, rule.id(), desired), rules, usage);
            String decreaseReason = quantity == 0 ? "Not equipped." : validate(unit,
                    changed(selected, rules, rule.id(), quantity - 1), rules, usage);
            return new Option(rule.id(), rule.name(), rule.type(), rule.catalogueCost(), rule.purchaseCost(),
                    quantity, rule.max(), rule.included(), reason.isEmpty(), reason, decreaseReason.isEmpty(), decreaseReason);
        }).toList();
        return new EquipmentView(roster, unit.entryId(), unit.requiresArmour(),
                costs.units(ownerId, roster.id()).get(unit.entryId()), equipped, available);
    }

    private Unit unit(long rosterId, long entryId) {
        var rows = jdbc.query("""
                SELECT ru.id,ru.unit_id,u.faction_id,u.requires_armour
                FROM roster_units ru JOIN units u ON u.id=ru.unit_id WHERE ru.id=? AND ru.roster_id=?
                """, (row, index) -> new Unit(row.getLong("id"), row.getLong("unit_id"),
                row.getLong("faction_id"), row.getBoolean("requires_armour")), entryId, rosterId);
        if (rows.isEmpty()) throw new RosterUnitService.MissingRosterUnit();
        return rows.getFirst();
    }

    private Map<Long, Rule> rules(Unit unit) {
        var rows = jdbc.query("""
                SELECT e.*,ur.unit_id AS allowed_unit,COALESCE(ur.cost_override,e.cost) AS purchase_cost,
                       COALESCE(ur.included_in_recruitment,FALSE) AS included,
                       COALESCE(ur.limit_exempt,FALSE) AS limit_exempt
                FROM equipment e LEFT JOIN unit_equipment_rules ur ON ur.equipment_id=e.id AND ur.unit_id=?
                WHERE e.faction_id=? ORDER BY e.equipment_type,e.name,e.id
                """, (row, index) -> new Rule(row.getLong("id"), row.getString("name"),
                row.getString("equipment_type"), row.getInt("cost"), row.getInt("purchase_cost"),
                row.getInt("max_per_model"), row.getInt("warband_limit"), row.getInt("hands"),
                row.getString("slot"), row.getBoolean("bayonet_lug"), row.getBoolean("requires_lug"),
                row.getBoolean("shield_combo"), row.getBoolean("heavy"), row.getBoolean("blocks_trench_shield"),
                row.getObject("allowed_unit") != null, row.getBoolean("included"), row.getBoolean("limit_exempt")),
                unit.unitId(), unit.factionId());
        var result = new LinkedHashMap<Long, Rule>();
        rows.forEach(rule -> result.put(rule.id(), rule));
        return result;
    }

    private Map<Long, Integer> quantities(long entryId) {
        var result = new LinkedHashMap<Long, Integer>();
        jdbc.query("SELECT equipment_id,quantity FROM roster_equipment WHERE roster_unit_id=? ORDER BY equipment_id",
                (RowCallbackHandler) row -> result.put(row.getLong("equipment_id"), row.getInt("quantity")), entryId);
        return result;
    }

    private Map<Long, Integer> usage(long rosterId, long entryId) {
        var result = new LinkedHashMap<Long, Integer>();
        jdbc.query("""
                SELECT re.equipment_id,SUM(re.quantity) AS used FROM roster_equipment re
                JOIN roster_units ru ON ru.id=re.roster_unit_id
                LEFT JOIN unit_equipment_rules ur ON ur.unit_id=ru.unit_id AND ur.equipment_id=re.equipment_id
                WHERE ru.roster_id=? AND ru.id<>? AND COALESCE(ur.limit_exempt,FALSE)=FALSE
                GROUP BY re.equipment_id
                """, (RowCallbackHandler) row -> result.put(row.getLong("equipment_id"), row.getInt("used")), rosterId, entryId);
        return result;
    }

    private Map<Long, Integer> changed(Map<Long, Integer> current, Map<Long, Rule> rules, long id, int quantity) {
        var next = new LinkedHashMap<>(current);
        var rule = rules.get(id);
        if (quantity > 0 && rule != null && rule.slot().equals("armour")) {
            next.keySet().removeIf(key -> rules.containsKey(key) && rules.get(key).slot().equals("armour"));
        }
        if (quantity == 0) next.remove(id); else next.put(id, quantity);
        return next;
    }

    private String validate(Unit unit, Map<Long, Integer> selected, Map<Long, Rule> rules, Map<Long, Integer> usage) {
        for (var entry : selected.entrySet()) {
            var rule = rules.get(entry.getKey());
            if (rule == null || !rule.allowed()) return "This equipment is not available for this unit.";
            if (entry.getValue() < 1 || entry.getValue() > rule.max()) return rule.name() + ": maximum " + rule.max() + " per unit.";
            if (rule.limit() > 0 && !rule.limitExempt()
                    && usage.getOrDefault(rule.id(), 0) + entry.getValue() > rule.limit()) {
                return rule.name() + ": warband limit " + rule.limit() + ".";
            }
        }
        for (String slot : List.of("armour", "shield", "headgear")) {
            int count = count(selected, rules, rule -> rule.slot().equals(slot));
            if (count > 1) return "Choose one " + slot + " item.";
            if (slot.equals("armour") && unit.requiresArmour() && count != 1) return "This unit requires its included armour.";
        }
        if (count(selected, rules, Rule::requiresLug) > 0 && count(selected, rules, Rule::lug) == 0) {
            return "A Bayonet requires a weapon with a bayonet lug.";
        }
        boolean shield = count(selected, rules, rule -> rule.slot().equals("shield")) > 0;
        if (shield && count(selected, rules, Rule::blocksShield) > 0) return "Machine Armour cannot be combined with a Trench Shield.";
        for (String type : List.of("Ranged weapon", "Melee weapon")) {
            int hands = 0;
            int weapons = 0;
            for (var entry : selected.entrySet()) {
                var rule = rules.get(entry.getKey());
                if (!rule.type().equals(type)) continue;
                hands += rule.hands() * entry.getValue();
                weapons += entry.getValue();
                if (shield && rule.hands() == 2 && (!rule.combo()
                        || count(selected, rules, r -> r.slot().equals("shield") && r.combo()) == 0)) {
                    return "This two-handed weapon cannot be combined with the shield.";
                }
            }
            if (hands > 2 || (shield && weapons > 1)) return "Too many " + type.toLowerCase() + "s for this unit.";
        }
        if (count(selected, rules, Rule::heavy) > 1) return "Choose one heavy weapon.";
        return "";
    }

    private int count(Map<Long, Integer> selected, Map<Long, Rule> rules, Predicate<Rule> filter) {
        return selected.entrySet().stream().filter(entry -> filter.test(rules.get(entry.getKey())))
                .mapToInt(Map.Entry::getValue).sum();
    }

    private void invalid(String message) { throw new RosterService.InvalidRoster(message, Map.of("equipment", message)); }

    private record Unit(long entryId, long unitId, long factionId, boolean requiresArmour) {}
    private record Rule(long id, String name, String type, int catalogueCost, int purchaseCost, int max,
                        int limit, int hands, String slot, boolean lug, boolean requiresLug, boolean combo,
                        boolean heavy, boolean blocksShield, boolean allowed, boolean included, boolean limitExempt) {}
    public record Equipped(long id, String name, String type, int quantity, int catalogueCost,
                           int purchaseCost, boolean includedInRecruitment) {}
    public record Option(long id, String name, String type, int catalogueCost, int purchaseCost, int quantity,
                         int maxQuantity, boolean includedInRecruitment, boolean compatible, String reason, boolean canDecrease, String decreaseReason) {}
    public record EquipmentView(RosterService.Roster roster, long rosterUnitId, boolean requiresArmour, RosterCostService.UnitCost unitCost,
                                List<Equipped> equipment, List<Option> availableEquipment) {}
}
