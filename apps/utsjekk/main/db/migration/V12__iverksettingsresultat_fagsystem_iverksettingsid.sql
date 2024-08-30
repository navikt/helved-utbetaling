ALTER TABLE iverksettingsresultat
    ADD COLUMN fagsystem VARCHAR NOT NULL default 'DAGPENGER',
    ADD COLUMN iverksetting_id VARCHAR;

CREATE INDEX iverksettingsresultat_behandling_idx ON iverksettingsresultat(fagsystem, behandling_id, iverksetting_id);