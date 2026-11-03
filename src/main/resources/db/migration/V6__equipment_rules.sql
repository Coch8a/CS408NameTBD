-- Warbands 1.0.2 pp. 11, 24-27, 30, 33; Digital Rulebook 1.0.2 pp. 68-77.
ALTER TABLE units ADD COLUMN requires_armour BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE equipment ADD COLUMN max_per_model INTEGER NOT NULL DEFAULT 1 CHECK (max_per_model > 0);
ALTER TABLE equipment ADD COLUMN warband_limit INTEGER CHECK (warband_limit > 0);
ALTER TABLE equipment ADD COLUMN hands INTEGER NOT NULL DEFAULT 0 CHECK (hands BETWEEN 0 AND 2);
ALTER TABLE equipment ADD COLUMN bayonet_lug BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE equipment ADD COLUMN requires_lug BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE equipment ADD COLUMN shield_combo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE equipment ADD COLUMN heavy BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE equipment ADD COLUMN slot VARCHAR(30) NOT NULL DEFAULT '';
ALTER TABLE equipment ADD COLUMN blocks_trench_shield BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO equipment (faction_id, name, cost, equipment_type)
SELECT id, 'Reinforced Armour', 40, 'Armour' FROM factions WHERE slug='new-antioch';
UPDATE equipment SET hands=2 WHERE faction_id IN (SELECT id FROM factions WHERE slug='new-antioch')
AND equipment_type='Ranged weapon' AND name<>'Pistol';
UPDATE equipment SET hands=1, max_per_model=2 WHERE name IN ('Pistol','Sword/Axe');
UPDATE equipment SET hands=2, requires_lug=TRUE WHERE name='Bayonet';
UPDATE equipment SET bayonet_lug=TRUE WHERE name IN ('Bolt-Action Rifle','Shotgun','Submachine Gun','Automatic Rifle');
UPDATE equipment SET shield_combo=TRUE WHERE name IN ('Shotgun','Submachine Gun','Bayonet','Trench Shield');
UPDATE equipment SET heavy=TRUE WHERE name='Machine Gun';
UPDATE equipment SET warband_limit=2 WHERE name IN ('Submachine Gun','Machine Gun');
UPDATE equipment SET warband_limit=1 WHERE name IN ('Automatic Rifle','Machine Armour');
UPDATE equipment SET slot='armour' WHERE equipment_type='Armour';
UPDATE equipment SET slot='shield' WHERE equipment_type='Shield';
UPDATE equipment SET slot='headgear' WHERE name='Combat Helmet';
UPDATE equipment SET blocks_trench_shield=TRUE WHERE name='Machine Armour';
UPDATE units SET requires_armour=TRUE WHERE name='Mechanized Heavy Infantry'
AND faction_id IN (SELECT id FROM factions WHERE slug='new-antioch');

CREATE TABLE unit_equipment_rules (
    unit_id BIGINT NOT NULL REFERENCES units(id),
    equipment_id BIGINT NOT NULL REFERENCES equipment(id),
    cost_override INTEGER CHECK (cost_override >= 0),
    included_in_recruitment BOOLEAN NOT NULL DEFAULT FALSE,
    default_selected BOOLEAN NOT NULL DEFAULT FALSE,
    limit_exempt BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (unit_id, equipment_id)
);
INSERT INTO unit_equipment_rules (unit_id, equipment_id)
SELECT u.id,e.id FROM units u JOIN equipment e ON e.faction_id=u.faction_id
JOIN factions f ON f.id=u.faction_id WHERE f.slug='new-antioch'
AND NOT (u.name='Yeoman' AND e.name IN ('Machine Armour','Reinforced Armour'))
AND NOT (u.name='Mechanized Heavy Infantry' AND e.name='Standard Armour');
UPDATE unit_equipment_rules SET cost_override=5
WHERE unit_id IN (SELECT id FROM units WHERE name='Yeoman')
AND equipment_id IN (SELECT id FROM equipment WHERE name='Bolt-Action Rifle');
UPDATE unit_equipment_rules SET cost_override=0, included_in_recruitment=TRUE, default_selected=TRUE
WHERE unit_id IN (SELECT id FROM units WHERE name='Mechanized Heavy Infantry')
AND equipment_id IN (SELECT id FROM equipment WHERE name='Reinforced Armour');
UPDATE unit_equipment_rules SET cost_override=10, included_in_recruitment=TRUE, limit_exempt=TRUE
WHERE unit_id IN (SELECT id FROM units WHERE name='Mechanized Heavy Infantry')
AND equipment_id IN (SELECT id FROM equipment WHERE name='Machine Armour');

INSERT INTO roster_equipment (roster_unit_id, equipment_id, quantity)
SELECT ru.id, rule.equipment_id, 1 FROM roster_units ru
JOIN unit_equipment_rules rule ON rule.unit_id=ru.unit_id AND rule.default_selected=TRUE
WHERE NOT EXISTS (SELECT 1 FROM roster_equipment re JOIN equipment e ON e.id=re.equipment_id
                  WHERE re.roster_unit_id=ru.id AND e.slot='armour');
