-- Keep earlier migrations intact so existing installations can upgrade.
ALTER TABLE users ADD COLUMN username_normalized VARCHAR(50);
ALTER TABLE users ADD COLUMN email_normalized VARCHAR(254);
UPDATE users SET username_normalized = LOWER(TRIM(username)), email_normalized = LOWER(TRIM(email));
ALTER TABLE users ALTER COLUMN username_normalized SET NOT NULL;
ALTER TABLE users ALTER COLUMN email_normalized SET NOT NULL;
CREATE UNIQUE INDEX users_username_normalized_unique ON users(username_normalized);
CREATE UNIQUE INDEX users_email_normalized_unique ON users(email_normalized);
ALTER TABLE users ADD CONSTRAINT users_username_normalized_check
    CHECK (username_normalized = LOWER(TRIM(username)));
ALTER TABLE users ADD CONSTRAINT users_email_normalized_check
    CHECK (email_normalized = LOWER(TRIM(email)));
