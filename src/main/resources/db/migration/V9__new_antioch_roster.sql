ALTER TABLE roster_units ADD COLUMN trench_mole BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE unit_equipment_rules ADD COLUMN fixed_loadout BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO units (faction_id,name,base_cost,unit_type,unit_limit,requires_armour)
SELECT f.id,v.name,v.cost,v.role,v.cap,v.armour FROM factions f CROSS JOIN (VALUES
    ('Sniper Priest',50,'Elite',2,FALSE),
    ('Trench Cleric',60,'Elite',1,FALSE),
    ('Shock Trooper',45,'Infantry',5,FALSE),
    ('Combat Engineer',80,'Infantry',2,TRUE),
    ('Combat Medic',65,'Infantry',1,TRUE)
) v(name,cost,role,cap,armour) WHERE f.slug='new-antioch';

INSERT INTO equipment (faction_id,name,cost,equipment_type,slot,hands,warband_limit)
SELECT f.id,v.name,v.cost,v.kind,v.slot,v.hands,v.cap FROM factions f CROSS JOIN (VALUES
    ('Engineer Body Armour',45,'Armour','armour',0,CAST(NULL AS INTEGER)),
    ('Shovel',5,'Equipment','',0,NULL),
    ('Misericordia',15,'Melee weapon','',1,1)
) v(name,cost,kind,slot,hands,cap) WHERE f.slug='new-antioch';

INSERT INTO unit_equipment_rules (unit_id,equipment_id)
SELECT u.id,e.id FROM units u JOIN equipment e ON e.faction_id=u.faction_id
JOIN factions f ON f.id=u.faction_id WHERE f.slug='new-antioch'
AND (u.name IN ('Sniper Priest','Trench Cleric','Shock Trooper','Combat Engineer','Combat Medic')
     OR e.name='Shovel')
AND (e.name<>'Engineer Body Armour' OR u.name='Combat Engineer')
AND (e.name<>'Misericordia' OR u.name='Combat Medic')
AND (e.name NOT IN ('Machine Armour','Reinforced Armour') OR u.unit_type='Elite')
AND (e.slot<>'armour' OR u.name NOT IN ('Combat Engineer','Combat Medic')
     OR (u.name='Combat Engineer' AND e.name='Engineer Body Armour')
     OR (u.name='Combat Medic' AND e.name='Standard Armour'));

UPDATE unit_equipment_rules SET cost_override=0,included_in_recruitment=TRUE,default_selected=TRUE,fixed_loadout=TRUE
WHERE EXISTS (SELECT 1 FROM units u JOIN factions f ON f.id=u.faction_id
    JOIN equipment e ON e.faction_id=f.id WHERE f.slug='new-antioch'
    AND u.id=unit_equipment_rules.unit_id AND e.id=unit_equipment_rules.equipment_id
    AND ((u.name='Combat Engineer' AND e.name IN ('Engineer Body Armour','Shovel'))
      OR (u.name='Combat Medic' AND e.name IN ('Standard Armour','Gas Mask','Medi-kit','Misericordia'))));

INSERT INTO unit_profiles (unit_id,movement_inches,movement_type,ranged_dice,melee_dice,base_mm,source_code,source_page)
SELECT u.id,6,'Infantry',v.ranged,v.melee,v.base,'warbands',v.page
FROM units u JOIN factions f ON f.id=u.faction_id JOIN (VALUES
    ('Sniper Priest',2,-1,25,28),
    ('Trench Cleric',1,1,32,29),
    ('Shock Trooper',0,1,25,31),
    ('Combat Engineer',0,1,25,32),
    ('Combat Medic',0,0,25,34)
) v(name,ranged,melee,base,page) ON v.name=u.name WHERE f.slug='new-antioch';

INSERT INTO equipment_profiles (equipment_id,melee,armour_modifier,source_code,source_page)
SELECT e.id,v.melee,v.armour,v.source_code,v.page
FROM equipment e JOIN factions f ON f.id=e.faction_id JOIN (VALUES
    ('Engineer Body Armour',FALSE,-2,'warbands',26),
    ('Shovel',FALSE,0,'core',85),
    ('Misericordia',TRUE,0,'core',76)
) v(name,melee,armour,source_code,page) ON v.name=e.name WHERE f.slug='new-antioch';

INSERT INTO profile_rules VALUES
('absolute-faith','Absolute Faith','ability','Enemies cannot spend Blood Markers to penalize this model''s ranged attacks.','warbands',28),
('aim','Aim','ability','Risky action with +2 dice. On success, gain +2 success dice on ranged attacks for this activation.','warbands',28),
('god-is-with-us','God is With Us!','ability','Risky action. On success, place a Blessing Marker on this model or an ally within 6 inches.','warbands',29),
('onward-christian-soldiers','Onward Christian Soldiers!','ability','Friendly New Antioch models within 8 inches gain NEGATE FEAR.','warbands',29),
('negate-fear','NEGATE FEAR','keyword','Ignores the effects of FEAR.','core',56),
('assault-drill','Assault Drill','ability','Ignore HEAVY effects on one melee weapon. The one-heavy-weapon limit still applies.','warbands',31),
('shock-charge','Shock Charge','ability','Roll an extra D6 for the charge bonus and use the higher die.','warbands',31),
('battlefield-demolition','Battlefield Demolition','ability','Ignore HEAVY effects on one Satchel Charge. The one-heavy-weapon limit still applies.','warbands',32),
('set-mine','Set Mine','ability','While touching a terrain piece up to 8 inches square, roll with +2 success dice to make it MINED.','warbands',32),
('defuse-mine','Defuse Mine','ability','On contact with mined terrain or a mine marker, a risky roll can remove the mine; failure triggers it.','warbands',32),
('fortify','Fortify','ability','A successful risky action grants COVER until the engineer moves.','warbands',32),
('negate-mined','NEGATE MINED','keyword','Ignores the effects of MINED terrain.','warbands',32),
('expert-medic','Expert Medic','ability','Gain +2 dice on Treat rolls made using a Medi-kit.','warbands',34),
('finish-the-fallen','Finish the Fallen','ability','Gain +1 injury die in melee against a Down enemy without BLACK GRAIL or DEMONIC.','warbands',34),
('ballistic-box-armour','Ballistic Box Armour','ability','Reduce SHRAPNEL injury rolls by one die, including against IGNORE ARMOUR.','warbands',26),
('dug-in','Dug In','ability','Start on open terrain with COVER until moving. The shovel can also replace other melee weapons as a two-handed attack.','core',85),
('despatch','Despatch','ability','Ignore armour when the target is Down.','core',76);

INSERT INTO unit_profile_rules (unit_id,rule_code)
SELECT u.id,v.code FROM units u JOIN factions f ON f.id=u.faction_id JOIN (VALUES
    ('Sniper Priest','elite'),('Sniper Priest','absolute-faith'),('Sniper Priest','aim'),
    ('Trench Cleric','elite'),('Trench Cleric','negate-fear'),('Trench Cleric','god-is-with-us'),
    ('Trench Cleric','onward-christian-soldiers'),('Shock Trooper','assault-drill'),('Shock Trooper','shock-charge'),
    ('Combat Engineer','battlefield-demolition'),('Combat Engineer','set-mine'),('Combat Engineer','defuse-mine'),
    ('Combat Engineer','fortify'),('Combat Engineer','negate-mined'),
    ('Combat Medic','negate-fear'),('Combat Medic','expert-medic'),('Combat Medic','finish-the-fallen')
) v(name,code) ON v.name=u.name WHERE f.slug='new-antioch';

INSERT INTO unit_profile_rules (unit_id,rule_code)
SELECT p.unit_id,'new-antioch' FROM unit_profiles p JOIN units u ON u.id=p.unit_id
JOIN factions f ON f.id=u.faction_id WHERE f.slug='new-antioch'
AND u.name IN ('Sniper Priest','Trench Cleric','Shock Trooper','Combat Engineer','Combat Medic');

INSERT INTO equipment_profile_rules (equipment_id,rule_code)
SELECT e.id,v.code FROM equipment e JOIN factions f ON f.id=e.faction_id JOIN (VALUES
    ('Engineer Body Armour','negate-shrapnel'),('Engineer Body Armour','ballistic-box-armour'),
    ('Shovel','dug-in'),('Misericordia','despatch')
) v(name,code) ON v.name=e.name WHERE f.slug='new-antioch';
