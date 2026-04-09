ALTER TABLE oppdrag ALTER COLUMN hash_key TYPE TEXT USING hash_key::text;
ALTER TABLE pending_utbetaling ALTER COLUMN hash_key TYPE TEXT USING hash_key::text;
