package db.migration;

import java.sql.Connection;
import java.util.ArrayList;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V5__individual_roster_units extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        var groups = new ArrayList<Group>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT id, roster_id, unit_id, quantity FROM roster_units WHERE quantity > 1")) {
            while (rows.next()) groups.add(new Group(rows.getLong(1), rows.getLong(2), rows.getLong(3), rows.getInt(4)));
        }
        for (var group : groups) {
            // Keep the original row and equipment IDs; copy its loadout for each extra individual.
            for (int count = 1; count < group.quantity(); count++) {
                long id;
                try (var insert = connection.prepareStatement(
                        "INSERT INTO roster_units (roster_id, unit_id, quantity) VALUES (?, ?, 1)", new String[]{"id"})) {
                    insert.setLong(1, group.rosterId());
                    insert.setLong(2, group.unitId());
                    insert.executeUpdate();
                    try (var keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) throw new IllegalStateException("Missing roster unit ID.");
                        id = keys.getLong(1);
                    }
                }
                try (var copy = connection.prepareStatement("""
                        INSERT INTO roster_equipment (roster_unit_id, equipment_id, quantity)
                        SELECT ?, equipment_id, quantity FROM roster_equipment WHERE roster_unit_id=?
                        """)) {
                    copy.setLong(1, id);
                    copy.setLong(2, group.id());
                    copy.executeUpdate();
                }
            }
            try (var update = connection.prepareStatement("UPDATE roster_units SET quantity=1 WHERE id=?")) {
                update.setLong(1, group.id());
                update.executeUpdate();
            }
        }
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE roster_units ADD CONSTRAINT roster_units_individual_check CHECK (quantity = 1)");
        }
    }

    private record Group(long id, long rosterId, long unitId, int quantity) {}
}
