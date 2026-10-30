package edu.boisestate.cs408.trenchroster.roster;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists roster settings, scoping every roster lookup and mutation to its owner. */
@Service
public class RosterService {
    private final JdbcTemplate jdbc;
    private static final String SELECT = """
            SELECT r.id, r.roster_name, r.faction_id, f.name AS faction_name,
                   r.ducat_limit, r.created_at, r.updated_at,
                   (SELECT COUNT(*) FROM roster_units ru WHERE ru.roster_id=r.id) AS unit_count
            FROM rosters r JOIN factions f ON f.id=r.faction_id
            """;
    private static final RowMapper<Roster> MAPPER = (row, index) -> new Roster(
            row.getLong("id"), row.getString("roster_name"), row.getLong("faction_id"),
            row.getString("faction_name"), row.getInt("ducat_limit"), row.getLong("unit_count"),
            row.getObject("created_at", OffsetDateTime.class).toInstant().toString(),
            row.getObject("updated_at", OffsetDateTime.class).toInstant().toString());

    public RosterService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Faction> factions() {
        // Match the currently supported catalogue; exclude the legacy demo faction.
        return jdbc.query("SELECT id, name FROM factions WHERE slug='new-antioch' ORDER BY name",
                (row, index) -> new Faction(row.getLong("id"), row.getString("name")));
    }

    public List<Roster> list(long ownerId) {
        return jdbc.query(SELECT + " WHERE r.user_id=? ORDER BY r.updated_at DESC, r.id DESC", MAPPER, ownerId);
    }

    public Roster get(long ownerId, long id) {
        var rows = jdbc.query(SELECT + " WHERE r.user_id=? AND r.id=?", MAPPER, ownerId, id);
        if (rows.isEmpty()) throw new MissingRoster();
        return rows.getFirst();
    }

    @Transactional
    public Roster create(long ownerId, RosterRequest request) {
        checkFaction(request.factionId());
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO rosters (user_id, faction_id, roster_name, ducat_limit) VALUES (?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, ownerId);
            statement.setLong(2, request.factionId());
            statement.setString(3, request.rosterName());
            statement.setInt(4, request.ducatLimit());
            return statement;
        }, key);
        return get(ownerId, key.getKey().longValue());
    }

    @Transactional
    public Roster update(long ownerId, long id, RosterRequest request) {
        long factionId = lock(ownerId, id);
        if (factionId != request.factionId().longValue()) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM roster_units WHERE roster_id=?", Long.class, id) > 0) {
                throw new InvalidRoster("Remove the roster's units before changing its faction.",
                        Map.of("factionId", "The faction cannot change while this roster contains units."));
            }
        }
        checkFaction(request.factionId());
        jdbc.update("""
                UPDATE rosters SET roster_name=?, faction_id=?, ducat_limit=?, updated_at=CURRENT_TIMESTAMP
                WHERE user_id=? AND id=?
                """, request.rosterName(), request.factionId(), request.ducatLimit(), ownerId, id);
        return get(ownerId, id);
    }

    @Transactional
    public void delete(long ownerId, long id) {
        // Existing foreign keys cascade only this roster's unit and equipment rows.
        if (jdbc.update("DELETE FROM rosters WHERE user_id=? AND id=?", ownerId, id) == 0) {
            throw new MissingRoster();
        }
    }

    long lock(long ownerId, long id) {
        var rows = jdbc.query("SELECT faction_id FROM rosters WHERE user_id=? AND id=? FOR UPDATE",
                (row, index) -> row.getLong("faction_id"), ownerId, id);
        if (rows.isEmpty()) throw new MissingRoster();
        return rows.getFirst();
    }

    private void checkFaction(long factionId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM factions WHERE id=? AND slug='new-antioch'",
                Long.class, factionId) == 0) {
            throw new InvalidRoster("Choose an available faction.", Map.of("factionId", "Choose an available faction."));
        }
    }

    public record Faction(long id, String name) {}
    public record Roster(long id, String rosterName, long factionId, String factionName,
                         int ducatLimit, long unitCount, String createdAt, String updatedAt) {}
    public static class MissingRoster extends RuntimeException {}
    public static class InvalidRoster extends RuntimeException {
        private final Map<String, String> fields;
        public InvalidRoster(String message, Map<String, String> fields) { super(message); this.fields = fields; }
        public Map<String, String> fields() { return fields; }
    }
}
