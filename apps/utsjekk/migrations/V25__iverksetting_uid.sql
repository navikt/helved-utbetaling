ALTER TABLE iverksetting
    ADD COLUMN utbetaling_id UUID;

ALTER TABLE iverksettingsresultat
    ADD COLUMN utbetaling_id UUID;
