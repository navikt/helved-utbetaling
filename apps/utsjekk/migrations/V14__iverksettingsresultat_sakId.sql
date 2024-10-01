ALTER TABLE iverksettingsresultat
    ADD COLUMN sakId VARCHAR NOT NULL default 'UKJENT';

DROP INDEX iverksettingsresultat_behandling_idx;
CREATE INDEX iverksettingsresultat_behandling_idx ON iverksettingsresultat(fagsystem, sakId, behandling_id, iverksetting_id);