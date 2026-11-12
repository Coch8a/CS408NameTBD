-- Profile facts and concise rule summaries, Warbands and Digital Rulebook 1.0.2.
CREATE TABLE rule_sources (
    code VARCHAR(30) PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    version VARCHAR(20) NOT NULL,
    url VARCHAR(300) NOT NULL
);
INSERT INTO rule_sources VALUES
('warbands', 'Warbands of Trench Crusade', '1.0.2', 'https://www.trenchcrusade.com/app/uploads/2026/09/Warbands-of-Trench-Crusade.pdf'),
('core', 'Trench Crusade Digital Rulebook', '1.0.2', 'https://www.trenchcrusade.com/app/uploads/2026/09/Trench-Crusade-Digital-Rulebook.pdf');

CREATE TABLE profile_rules (
    code VARCHAR(40) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('keyword','ability','restriction','upgrade')),
    summary VARCHAR(700) NOT NULL,
    source_code VARCHAR(30) NOT NULL REFERENCES rule_sources(code),
    source_page INTEGER NOT NULL CHECK (source_page > 0)
);
CREATE TABLE unit_profiles (
    unit_id BIGINT PRIMARY KEY REFERENCES units(id) ON DELETE CASCADE,
    movement_inches INTEGER NOT NULL CHECK (movement_inches > 0),
    movement_type VARCHAR(30) NOT NULL,
    ranged_dice INTEGER NOT NULL,
    melee_dice INTEGER NOT NULL,
    innate_armour INTEGER NOT NULL DEFAULT 0,
    base_mm INTEGER NOT NULL CHECK (base_mm > 0),
    source_code VARCHAR(30) NOT NULL REFERENCES rule_sources(code),
    source_page INTEGER NOT NULL CHECK (source_page > 0)
);
CREATE TABLE equipment_profiles (
    equipment_id BIGINT PRIMARY KEY REFERENCES equipment(id) ON DELETE CASCADE,
    range_inches INTEGER CHECK (range_inches > 0),
    melee BOOLEAN NOT NULL DEFAULT FALSE,
    ranged_dice INTEGER NOT NULL DEFAULT 0,
    melee_dice INTEGER NOT NULL DEFAULT 0,
    armour_modifier INTEGER NOT NULL DEFAULT 0,
    minimum_base_mm INTEGER NOT NULL DEFAULT 0 CHECK (minimum_base_mm >= 0),
    charge_bonus VARCHAR(10),
    source_code VARCHAR(30) NOT NULL REFERENCES rule_sources(code),
    source_page INTEGER NOT NULL CHECK (source_page > 0)
);
CREATE TABLE unit_profile_rules (
    unit_id BIGINT NOT NULL REFERENCES unit_profiles(unit_id) ON DELETE CASCADE,
    rule_code VARCHAR(40) NOT NULL REFERENCES profile_rules(code),
    PRIMARY KEY (unit_id,rule_code)
);
CREATE TABLE equipment_profile_rules (
    equipment_id BIGINT NOT NULL REFERENCES equipment_profiles(equipment_id) ON DELETE CASCADE,
    rule_code VARCHAR(40) NOT NULL REFERENCES profile_rules(code),
    PRIMARY KEY (equipment_id,rule_code)
);

INSERT INTO profile_rules VALUES
('new-antioch','NEW ANTIOCH','keyword','New Antioch faction.','core',56),
('elite','ELITE','keyword','Elite warband entry; qualifies for ELITE-only equipment.','core',54),
('leader','LEADER','keyword','Morale checks gain +1 die while a friendly Leader remains on the battlefield, neither Down nor Out of Action.','core',56),
('tough','TOUGH','keyword','The first Out of Action injury becomes Down.','core',57),
('strong','STRONG','keyword','Ignores HEAVY effects; may use one two-handed melee weapon in one hand, except CUMBERSOME weapons.','core',57),
('hold-fire','Hold Your Fire!','ability','End this activation to make the opponent activate an eligible enemy in line of sight.','warbands',27),
('trench-moles','Trench Moles','upgrade','Optional +10 ducat upgrade grants INFILTRATOR to up to two Yeomen. A third is permitted when all other models and their kit total at least 1,000 ducats.','warbands',30),
('included-armour','Included armour','restriction','Recruitment includes Reinforced Armour (85 ducats) or Machine Armour (95). This armour cannot be removed or lost.','warbands',33),
('assault','ASSAULT','keyword','Shooting does not rule out charging or fighting during that activation.','core',52),
('automatic-2','AUTOMATIC 2','keyword','Two attacks per Shoot action. Different targets must be within 6 inches of each other.','core',53),
('automatic-3','AUTOMATIC 3','keyword','Three attacks per Shoot action. Different targets must be within 6 inches of each other.','core',53),
('focused-fire','Focused Fire','ability','Both Automatic Rifle attacks must target the same enemy.','core',70),
('quick-bursts','Quick Bursts','ability','Two Shoot actions per activation, both using this gun; other actions may occur between them.','core',74),
('pistol','PISTOL','keyword','Usable at range and in melee in one activation. Melee may use either combat characteristic, including off-hand use.','core',56),
('shotgun','SHOTGUN','keyword','At long range, applies -1 injury die instead of the usual -1 attack die.','core',57),
('critical','CRITICAL','keyword','Critical hits receive two extra injury dice instead of one.','core',54),
('cumbersome','CUMBERSOME','keyword','Always two-handed, including for STRONG models. Shield Combo still applies.','core',54),
('heavy','HEAVY','keyword','One heavy item per model; no charge bonus. Heavy ranged attacks cannot share an activation with movement actions. STRONG negates these effects.','core',55),
('reload','RELOAD','keyword','The activation ends after the action used to attack.','core',56),
('negate-shrapnel','NEGATE SHRAPNEL','keyword','Ignores the SHRAPNEL effect.','core',56),
('negate-gas','NEGATE GAS','keyword','Ignores the GAS effect.','core',56),
('bulky','Bulky','ability','Minimum 40 mm base; D3 charge bonus; cannot use a Trench Shield.','warbands',26),
('standfast','Standfast','ability','Down injuries become Minor Hits.','warbands',26),
('treat','Treat','ability','Risky action: remove one Blood Marker from self or an ally within 1 inch, or stand up a Down ally within 1 inch. Failure ends the activation.','core',84),
('lug','Bayonet Lug','restriction','Allows a bayonet to be fitted.','warbands',11),
('requires-lug','Bayonet mount','restriction','Requires an equipped ranged weapon with a bayonet lug.','warbands',11),
('shield-combo','Shield Combo','restriction','A shield and two-handed weapon can be combined when both have Shield Combo.','warbands',11),
('headgear','Headgear','restriction','One headgear item per model.','warbands',11),
('armour-access','Armour access','restriction','ELITE or Mechanized Heavy Infantry only.','warbands',25),
('machine-limit','Machine Armour limit','restriction','One per warband, excluding Mechanized Heavy Infantry.','warbands',25),
('limit-1','Warband limit 1','restriction','At most one per warband.','warbands',24),
('limit-2','Warband limit 2','restriction','At most two per warband.','warbands',24),
('rifle-discount','Recruitment rifle','restriction','A Yeoman may buy a Bolt-Action Rifle for 5 ducats at recruitment.','warbands',30);

INSERT INTO unit_profiles SELECT u.id,6,'Infantry',2,2,0,32,'warbands',27 FROM units u JOIN factions f ON f.id=u.faction_id WHERE f.slug='new-antioch' AND u.name='Lieutenant';
INSERT INTO unit_profile_rules SELECT p.unit_id,r.code FROM unit_profiles p JOIN units u ON u.id=p.unit_id CROSS JOIN profile_rules r WHERE u.name='Lieutenant' AND r.code IN ('new-antioch','elite','leader','tough','hold-fire');

INSERT INTO unit_profiles SELECT u.id,6,'Infantry',0,0,0,25,'warbands',30 FROM units u JOIN factions f ON f.id=u.faction_id WHERE f.slug='new-antioch' AND u.name='Yeoman';
INSERT INTO unit_profile_rules SELECT p.unit_id,r.code FROM unit_profiles p JOIN units u ON u.id=p.unit_id CROSS JOIN profile_rules r WHERE u.name='Yeoman' AND r.code IN ('new-antioch','rifle-discount','trench-moles');

INSERT INTO unit_profiles SELECT u.id,6,'Infantry',1,0,0,40,'warbands',33 FROM units u JOIN factions f ON f.id=u.faction_id WHERE f.slug='new-antioch' AND u.name='Mechanized Heavy Infantry';
INSERT INTO unit_profile_rules SELECT p.unit_id,r.code FROM unit_profiles p JOIN units u ON u.id=p.unit_id CROSS JOIN profile_rules r WHERE u.name='Mechanized Heavy Infantry' AND r.code IN ('new-antioch','strong','included-armour');

INSERT INTO equipment_profiles SELECT e.id,24,FALSE,0,0,0,0,NULL,'core',71 FROM equipment e JOIN factions f ON f.id=e.faction_id WHERE f.slug='new-antioch' AND e.name='Bolt-Action Rifle';
INSERT INTO equipment_profile_rules SELECT p.equipment_id,r.code FROM equipment_profiles p JOIN equipment e ON e.id=p.equipment_id CROSS JOIN profile_rules r WHERE e.name='Bolt-Action Rifle' AND r.code IN ('lug');

INSERT INTO equipment_profiles SELECT e.id,12,TRUE,0,0,0,0,NULL,'core',72 FROM equipment e JOIN factions f ON f.id=e.faction_id WHERE f.slug='new-antioch' AND e.name='Pistol';
INSERT INTO equipment_profile_rules SELECT p.equipment_id,r.code FROM equipment_profiles p JOIN equipment e ON e.id=p.equipment_id CROSS JOIN profile_rules r WHERE e.name='Pistol' AND r.code IN ('pistol');

INSERT INTO equipment_profiles SELECT e.id,12,FALSE,1,0,0,0,NULL,'core',73 FROM equipment e JOIN factions f ON f.id=e.faction_id WHERE f.slug='new-antioch' AND e.name='Shotgun';
INSERT INTO equipment_profile_rules SELECT p.equipment_id,r.code FROM equipment_profiles p JOIN equipment e ON e.id=p.equipment_id CROSS JOIN profile_rules r WHERE e.name='Shotgun' AND r.code IN ('shotgun','lug','shield-combo');

INSERT INTO equipment_profiles SELECT e.id,16,FALSE,0,0,0,0,NULL,'core',74 FROM equipment e JOIN factions f ON f.id=e.faction_id WHERE f.slug='new-antioch' AND e.name='Submachine Gun';
INSERT INTO equipment_profile_rules SELECT p.equipment_id,r.code FROM equipment_profiles p JOIN equipment e ON e.id=p.equipment_id CROSS JOIN profile_rules r WHERE e.name='Submachine Gun' AND r.code IN ('assault','quick-bursts','lug','shield-combo','limit-2');

INSERT INTO equipment_profiles SELECT e.id,24,FALSE,0,0,0,0,NULL,'core',70 FROM equipment e JOIN factions f ON f.id=e.faction_id WHERE f.slug='new-antioch' AND e.name='Automatic Rifle';
INSERT INTO equipment_profile_rules SELECT p.equipment_id,r.code FROM equipment_profiles p JOIN equipment e ON e.id=p.equipment_id CROSS JOIN profile_rules r WHERE e.name='Automatic Rifle' AND r.code IN ('assault','automatic-2','focused-fire','lug','limit-1');

INSERT INTO equipment_profiles SELECT e.id,36,FALSE,0,0,0,0,NULL,'core',72 FROM equipment e JOIN factions f ON f.id=e.faction_id WHERE f.slug='new-antioch' AND e.name='Machine Gun';
INSERT INTO equipment_profile_rules SELECT p.equipment_id,r.code FROM equipment_profiles p JOIN equipment e ON e.id=p.equipment_id CROSS JOIN profile_rules r WHERE e.name='Machine Gun' AND r.code IN ('automatic-3','heavy','reload','limit-2');

INSERT INTO equipment_profiles SELECT e.id,NULL,TRUE,0,0,0,0,NULL,'core',75 FROM equipment e JOIN factions f ON f.id=e.faction_id WHERE f.slug='new-antioch' AND e.name='Bayonet';
INSERT INTO equipment_profile_rules SELECT p.equipment_id,r.code FROM equipment_profiles p JOIN equipment e ON e.id=p.equipment_id CROSS JOIN profile_rules r WHERE e.name='Bayonet' AND r.code IN ('cumbersome','requires-lug','shield-combo');

INSERT INTO equipment_profiles SELECT e.id,NULL,TRUE,0,0,0,0,NULL,'core',76 FROM equipment e JOIN factions f ON f.id=e.faction_id WHERE f.slug='new-antioch' AND e.name='Sword/Axe';
INSERT INTO equipment_profile_rules SELECT p.equipment_id,r.code FROM equipment_profiles p JOIN equipment e ON e.id=p.equipment_id CROSS JOIN profile_rules r WHERE e.name='Sword/Axe' AND r.code IN ('critical');

INSERT INTO equipment_profiles SELECT e.id,NULL,FALSE,0,0,-1,0,NULL,'core',80 FROM equipment e JOIN factions f ON f.id=e.faction_id WHERE f.slug='new-antioch' AND e.name='Trench Shield';
INSERT INTO equipment_profile_rules SELECT p.equipment_id,r.code FROM equipment_profiles p JOIN equipment e ON e.id=p.equipment_id CROSS JOIN profile_rules r WHERE e.name='Trench Shield' AND r.code IN ('shield-combo');

INSERT INTO equipment_profiles SELECT e.id,NULL,FALSE,0,0,-1,0,NULL,'core',80 FROM equipment e JOIN factions f ON f.id=e.faction_id WHERE f.slug='new-antioch' AND e.name='Standard Armour';

INSERT INTO equipment_profiles SELECT e.id,NULL,FALSE,0,0,-2,0,NULL,'core',80 FROM equipment e JOIN factions f ON f.id=e.faction_id WHERE f.slug='new-antioch' AND e.name='Reinforced Armour';
INSERT INTO equipment_profile_rules SELECT p.equipment_id,r.code FROM equipment_profiles p JOIN equipment e ON e.id=p.equipment_id CROSS JOIN profile_rules r WHERE e.name='Reinforced Armour' AND r.code IN ('armour-access');

INSERT INTO equipment_profiles SELECT e.id,NULL,FALSE,0,0,-3,40,'D3','warbands',26 FROM equipment e JOIN factions f ON f.id=e.faction_id WHERE f.slug='new-antioch' AND e.name='Machine Armour';
INSERT INTO equipment_profile_rules SELECT p.equipment_id,r.code FROM equipment_profiles p JOIN equipment e ON e.id=p.equipment_id CROSS JOIN profile_rules r WHERE e.name='Machine Armour' AND r.code IN ('armour-access','machine-limit','bulky','standfast');

INSERT INTO equipment_profiles SELECT e.id,NULL,FALSE,0,0,0,0,NULL,'core',82 FROM equipment e JOIN factions f ON f.id=e.faction_id WHERE f.slug='new-antioch' AND e.name='Combat Helmet';
INSERT INTO equipment_profile_rules SELECT p.equipment_id,r.code FROM equipment_profiles p JOIN equipment e ON e.id=p.equipment_id CROSS JOIN profile_rules r WHERE e.name='Combat Helmet' AND r.code IN ('negate-shrapnel','headgear');

INSERT INTO equipment_profiles SELECT e.id,NULL,FALSE,0,0,0,0,NULL,'core',82 FROM equipment e JOIN factions f ON f.id=e.faction_id WHERE f.slug='new-antioch' AND e.name='Gas Mask';
INSERT INTO equipment_profile_rules SELECT p.equipment_id,r.code FROM equipment_profiles p JOIN equipment e ON e.id=p.equipment_id CROSS JOIN profile_rules r WHERE e.name='Gas Mask' AND r.code IN ('negate-gas');

INSERT INTO equipment_profiles SELECT e.id,NULL,FALSE,0,0,0,0,NULL,'core',84 FROM equipment e JOIN factions f ON f.id=e.faction_id WHERE f.slug='new-antioch' AND e.name='Medi-kit';
INSERT INTO equipment_profile_rules SELECT p.equipment_id,r.code FROM equipment_profiles p JOIN equipment e ON e.id=p.equipment_id CROSS JOIN profile_rules r WHERE e.name='Medi-kit' AND r.code IN ('treat');
