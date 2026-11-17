package edu.boisestate.cs408.trenchroster.roster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RosterValidation {
    private static final String COUNTS = """
            SELECT u.name, u.unit_minimum, u.unit_limit, COUNT(ru.id) AS selected
            FROM units u LEFT JOIN roster_units ru ON ru.unit_id=u.id AND ru.roster_id=?
            WHERE u.faction_id=?
            """;
    private static final String GROUP = " GROUP BY u.id, u.name, u.unit_minimum, u.unit_limit ORDER BY u.name, u.id";
    private static final String TRENCH_MOLE_ISSUE =
            "A third Trench Mole requires at least 1,000 ducats in other models and equipment.";
    private final JdbcTemplate jdbc;

    public RosterValidation(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    List<String> unitIssues(long rosterId, long factionId) {
        var issues = new ArrayList<String>();
        for (var rule : rules(" AND (u.unit_minimum>0 OR u.unit_limit IS NOT NULL)", rosterId, factionId)) {
            long missing = rule.minimum() - rule.selected();
            if (missing > 0) {
                issues.add("Add " + (missing == 1 ? "a " : missing + " more ") + rule.name() + ".");
            }
            if (rule.maximum() != null && rule.selected() > rule.maximum()) {
                issues.add(rule.name() + ": " + rule.selected() + " selected (maximum " + rule.maximum() + ").");
            }
        }
        String upgradeIssue = trenchMoleIssue(rosterId);
        if (!upgradeIssue.isEmpty()) issues.add(upgradeIssue);
        return List.copyOf(issues);
    }

    void checkTrenchMoles(long rosterId) {
        String issue = trenchMoleIssue(rosterId);
        if (!issue.isEmpty()) throw new RosterService.InvalidRoster(issue, Map.of("trenchMole", issue));
    }

    private String trenchMoleIssue(long rosterId) {
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM roster_units WHERE roster_id=? AND trench_mole=TRUE",
                Long.class, rosterId);
        if (count <= 2) return "";
        if (count > 3) return "A roster can have at most three Trench Moles.";
        long otherModels = jdbc.queryForObject("""
                SELECT SUM(c.recruitment_cost+c.equipment_cost) -
                       MIN(CASE WHEN ru.trench_mole THEN c.recruitment_cost+c.equipment_cost END) FROM (
                """ + RosterCostService.UNIT_COSTS + """
                ) c JOIN roster_units ru ON ru.id=c.id WHERE c.roster_id=?
                """, Long.class, rosterId);
        return otherModels < 1_000 ? TRENCH_MOLE_ISSUE : "";
    }

    void checkAdd(long rosterId, long factionId, long unitId) {
        var matches = rules(" AND u.id=?", rosterId, factionId, unitId);
        if (matches.isEmpty()) {
            throw new RosterService.InvalidRoster("Choose a unit from this roster's faction.",
                    Map.of("unitId", "This unit is not available for this faction."));
        }
        var rule = matches.getFirst();
        if (rule.maximum() != null && rule.selected() >= rule.maximum()) {
            String message = rule.name() + ": maximum " + rule.maximum() + " per roster.";
            throw new RosterService.InvalidRoster(message, Map.of("unitId", message));
        }
    }

    private List<UnitRule> rules(String filter, Object... params) {
        return jdbc.query(COUNTS + filter + GROUP, (row, index) -> new UnitRule(row.getString("name"),
                row.getInt("unit_minimum"), row.getObject("unit_limit", Integer.class), row.getLong("selected")), params);
    }

    private record UnitRule(String name, int minimum, Integer maximum, long selected) {}
}
