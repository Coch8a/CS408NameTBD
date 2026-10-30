package edu.boisestate.cs408.trenchroster.roster;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RosterUnitService {
    private final JdbcTemplate jdbc;
    private final RosterService rosters;

    public RosterUnitService(JdbcTemplate jdbc, RosterService rosters) {
        this.jdbc = jdbc;
        this.rosters = rosters;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public UnitView view(long ownerId, long rosterId) {
        return snapshot(ownerId, rosterId);
    }

    @Transactional
    public UnitView add(long ownerId, long rosterId, long unitId) {
        long factionId = rosters.lock(ownerId, rosterId);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM units WHERE id=? AND faction_id=?", Long.class, unitId, factionId) == 0) {
            throw new RosterService.InvalidRoster("Choose a unit from this roster's faction.",
                    Map.of("unitId", "This unit is not available for this faction."));
        }
        jdbc.update("INSERT INTO roster_units (roster_id, unit_id, quantity) VALUES (?, ?, 1)", rosterId, unitId);
        touch(ownerId, rosterId);
        return snapshot(ownerId, rosterId);
    }

    @Transactional
    public UnitView remove(long ownerId, long rosterId, long entryId) {
        rosters.lock(ownerId, rosterId);
        if (jdbc.update("DELETE FROM roster_units WHERE id=? AND roster_id=?", entryId, rosterId) == 0) {
            throw new MissingRosterUnit();
        }
        touch(ownerId, rosterId);
        return snapshot(ownerId, rosterId);
    }

    private UnitView snapshot(long ownerId, long rosterId) {
        var roster = rosters.get(ownerId, rosterId);
        var units = jdbc.query("""
                SELECT ru.id, ru.unit_id, u.name, u.unit_type, u.base_cost, u.alternate_cost, u.cost_notes
                FROM roster_units ru JOIN units u ON u.id=ru.unit_id
                JOIN rosters r ON r.id=ru.roster_id WHERE r.id=? AND r.user_id=? ORDER BY ru.id
                """, (row, index) -> new SavedUnit(row.getLong("id"), row.getLong("unit_id"),
                row.getString("name"), row.getString("unit_type"), row.getInt("base_cost"),
                row.getInt("alternate_cost"), row.getString("cost_notes")), rosterId, ownerId);
        var available = jdbc.query("""
                SELECT id, name, unit_type, base_cost, alternate_cost, cost_notes
                FROM units WHERE faction_id=? ORDER BY id
                """, (row, index) -> new AvailableUnit(row.getLong("id"), row.getString("name"),
                row.getString("unit_type"), row.getInt("base_cost"), row.getInt("alternate_cost"),
                row.getString("cost_notes")), roster.factionId());
        return new UnitView(roster, units, available);
    }

    private void touch(long ownerId, long rosterId) {
        jdbc.update("UPDATE rosters SET updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?", rosterId, ownerId);
    }

    public record AvailableUnit(long id, String name, String type, int cost, int alternateCost, String notes) {}
    public record SavedUnit(long id, long unitId, String name, String type, int cost, int alternateCost, String notes) {}
    public record UnitView(RosterService.Roster roster, List<SavedUnit> units, List<AvailableUnit> availableUnits) {}
    public static class MissingRosterUnit extends RuntimeException {}
}
