package edu.boisestate.cs408.trenchroster.roster;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RosterCostService {
    static final String UNIT_COSTS = """
            SELECT ru.id,ru.roster_id,CAST(u.base_cost AS BIGINT)*ru.quantity AS recruitment_cost,
                   COALESCE(SUM(CAST(COALESCE(rule.cost_override,e.cost) AS BIGINT)*re.quantity),0)*ru.quantity AS equipment_cost
            FROM roster_units ru JOIN units u ON u.id=ru.unit_id
            LEFT JOIN roster_equipment re ON re.roster_unit_id=ru.id
            LEFT JOIN equipment e ON e.id=re.equipment_id
            LEFT JOIN unit_equipment_rules rule ON rule.unit_id=u.id AND rule.equipment_id=e.id
            GROUP BY ru.id,ru.roster_id,u.base_cost,ru.quantity
            """;
    private final JdbcTemplate jdbc;

    public RosterCostService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Map<Long, UnitCost> units(long ownerId, long rosterId) {
        var rows = jdbc.query("SELECT c.* FROM (" + UNIT_COSTS + """
                ) c JOIN rosters r ON r.id=c.roster_id WHERE r.user_id=? AND r.id=? ORDER BY c.id
                """, (row, index) -> new UnitCost(row.getLong("id"), row.getLong("recruitment_cost"),
                row.getLong("equipment_cost"), Math.addExact(row.getLong("recruitment_cost"), row.getLong("equipment_cost"))),
                ownerId, rosterId);
        var result = new LinkedHashMap<Long, UnitCost>();
        rows.forEach(cost -> result.put(cost.rosterUnitId(), cost));
        return result;
    }

    public record UnitCost(long rosterUnitId, long recruitmentCost, long equipmentCost, long totalCost) {}
}
