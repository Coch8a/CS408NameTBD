-- Fictional demonstration data, not official Trench Crusade rules or prices.
-- Flyway runs this once, so restarts do not duplicate sample rows.
INSERT INTO factions (slug, name, description)
VALUES ('checkpoint-demo', 'Training Company', 'Fictional sample faction for testing the roster builder foundation.');
INSERT INTO units (faction_id, name, base_cost, unit_type, unit_limit)
SELECT id, 'Sample Infantry', 20, 'Infantry', NULL FROM factions WHERE slug = 'checkpoint-demo';
INSERT INTO units (faction_id, name, base_cost, unit_type, unit_limit)
SELECT id, 'Sample Scout', 30, 'Specialist', 2 FROM factions WHERE slug = 'checkpoint-demo';
INSERT INTO units (faction_id, name, base_cost, unit_type, unit_limit)
SELECT id, 'Sample Commander', 50, 'Leader', 1 FROM factions WHERE slug = 'checkpoint-demo';
INSERT INTO equipment (name, cost, equipment_type) VALUES
    ('Training Rifle', 10, 'Weapon'), ('Field Kit', 5, 'Utility'), ('Practice Armour', 15, 'Armour');
