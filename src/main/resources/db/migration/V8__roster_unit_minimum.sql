ALTER TABLE units ADD COLUMN unit_minimum INTEGER NOT NULL DEFAULT 0
    CHECK (unit_minimum >= 0 AND (unit_limit IS NULL OR unit_minimum <= unit_limit));

UPDATE units SET unit_minimum = 1
WHERE name = 'Lieutenant' AND faction_id IN (SELECT id FROM factions WHERE slug = 'new-antioch');
