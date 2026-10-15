-- Warbands of Trench Crusade 1.0.2, New Antioch, printed pp. 24-25, 27, 30, 33.
-- Add a sourced catalogue without changing earlier Flyway checksums or deleting
-- existing users, rosters, or the old sample rows that a roster might reference.
ALTER TABLE units ADD COLUMN cost_notes VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE units ADD COLUMN alternate_cost INTEGER CHECK (alternate_cost >= 0);
ALTER TABLE equipment ADD COLUMN faction_id BIGINT REFERENCES factions(id);
ALTER TABLE equipment ADD COLUMN cost_notes VARCHAR(500) NOT NULL DEFAULT '';
CREATE INDEX idx_equipment_faction ON equipment(faction_id);

INSERT INTO factions (slug, name, description)
VALUES ('new-antioch', 'New Antioch', 'The Principality of New Antioch');
INSERT INTO units (faction_id, name, base_cost, unit_type, unit_limit, cost_notes)
SELECT id, 'Lieutenant', 70, 'Leader', 1, 'Required leader. Weapons and armour are purchased separately.'
FROM factions WHERE slug = 'new-antioch';
INSERT INTO units (faction_id, name, base_cost, unit_type, unit_limit, cost_notes)
SELECT id, 'Yeoman', 30, 'Infantry', NULL, 'Bolt-Action Rifle: +5 ducats at recruitment (35 total).'
FROM factions WHERE slug = 'new-antioch';
INSERT INTO units (faction_id, name, base_cost, unit_type, unit_limit, cost_notes, alternate_cost)
SELECT id, 'Mechanized Heavy Infantry', 85, 'Heavy infantry', 3,
       '85 with Reinforced Armour; 95 with Machine Armour. Weapons cost extra.', 95
FROM factions WHERE slug = 'new-antioch';

INSERT INTO equipment (faction_id, name, cost, equipment_type, cost_notes)
SELECT id, 'Bolt-Action Rifle', 10, 'Ranged weapon', 'Yeoman recruitment price: 5 ducats. Bayonet Lug.' FROM factions WHERE slug = 'new-antioch';
INSERT INTO equipment (faction_id, name, cost, equipment_type, cost_notes)
SELECT id, 'Pistol', 6, 'Ranged weapon', '' FROM factions WHERE slug = 'new-antioch';
INSERT INTO equipment (faction_id, name, cost, equipment_type, cost_notes)
SELECT id, 'Shotgun', 10, 'Ranged weapon', 'Bayonet Lug; Shield Combo.' FROM factions WHERE slug = 'new-antioch';
INSERT INTO equipment (faction_id, name, cost, equipment_type, cost_notes)
SELECT id, 'Submachine Gun', 30, 'Ranged weapon', 'Limit 2; Bayonet Lug; Shield Combo.' FROM factions WHERE slug = 'new-antioch';
INSERT INTO equipment (faction_id, name, cost, equipment_type, cost_notes)
SELECT id, 'Automatic Rifle', 40, 'Ranged weapon', 'Limit 1; Bayonet Lug.' FROM factions WHERE slug = 'new-antioch';
INSERT INTO equipment (faction_id, name, cost, equipment_type, cost_notes)
SELECT id, 'Machine Gun', 50, 'Ranged weapon', 'Limit 2.' FROM factions WHERE slug = 'new-antioch';
INSERT INTO equipment (faction_id, name, cost, equipment_type, cost_notes)
SELECT id, 'Bayonet', 2, 'Melee weapon', 'Shield Combo.' FROM factions WHERE slug = 'new-antioch';
INSERT INTO equipment (faction_id, name, cost, equipment_type, cost_notes)
SELECT id, 'Sword/Axe', 4, 'Melee weapon', '' FROM factions WHERE slug = 'new-antioch';
INSERT INTO equipment (faction_id, name, cost, equipment_type, cost_notes)
SELECT id, 'Trench Shield', 10, 'Shield', 'Shield Combo.' FROM factions WHERE slug = 'new-antioch';
INSERT INTO equipment (faction_id, name, cost, equipment_type, cost_notes)
SELECT id, 'Machine Armour', 50, 'Armour', 'ELITE and Mechanized Heavy Infantry only. Limit 1 excluding Heavy Infantry; their 95-ducat profile already includes this armour.' FROM factions WHERE slug = 'new-antioch';
INSERT INTO equipment (faction_id, name, cost, equipment_type, cost_notes)
SELECT id, 'Standard Armour', 15, 'Armour', '' FROM factions WHERE slug = 'new-antioch';
INSERT INTO equipment (faction_id, name, cost, equipment_type, cost_notes)
SELECT id, 'Combat Helmet', 5, 'Equipment', 'Headgear.' FROM factions WHERE slug = 'new-antioch';
INSERT INTO equipment (faction_id, name, cost, equipment_type, cost_notes)
SELECT id, 'Gas Mask', 5, 'Equipment', '' FROM factions WHERE slug = 'new-antioch';
INSERT INTO equipment (faction_id, name, cost, equipment_type, cost_notes)
SELECT id, 'Medi-kit', 5, 'Equipment', '' FROM factions WHERE slug = 'new-antioch';
