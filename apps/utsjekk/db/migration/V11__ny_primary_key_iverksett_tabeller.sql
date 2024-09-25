ALTER TABLE iverksetting
    DROP CONSTRAINT iverksett_pkey;

ALTER TABLE iverksettingsresultat
    DROP CONSTRAINT iverksett_resultat_pkey;

ALTER TABLE iverksetting
    ADD COLUMN id bigserial primary key;

ALTER TABLE iverksettingsresultat
    ADD COLUMN id bigserial primary key;