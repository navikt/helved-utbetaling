ALTER TABLE kjent_dobbeltutbetaling
    ADD COLUMN slukket_at TIMESTAMP,
    ADD COLUMN handtert_at TIMESTAMP;

UPDATE kjent_dobbeltutbetaling
SET handtert_at = registrert_at;
