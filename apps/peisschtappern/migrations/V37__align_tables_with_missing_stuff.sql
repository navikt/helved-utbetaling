ALTER TABLE tp ADD COLUMN headers TEXT;
ALTER TABLE tp ADD COLUMN status TEXT;

create index aapintern_aap_system_time_ms_idx ON aapintern (system_time_ms DESC);
create index dp_aap_system_time_ms_idx ON dp (system_time_ms DESC);
create index dpintern_aap_system_time_ms_idx ON dpintern (system_time_ms DESC);
create index ts_aap_system_time_ms_idx ON ts (system_time_ms DESC);
create index tsintern_aap_system_time_ms_idx ON tsintern (system_time_ms DESC);

ALTER TABLE dryrun_aap DROP COLUMN sak_id;
ALTER TABLE dryrun_aap ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (try_jsonb_get_text(record_value, 'sakId')) STORED;
ALTER TABLE dryrun_dp DROP COLUMN sak_id;
ALTER TABLE dryrun_dp ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (try_jsonb_get_text(record_value, 'sakId')) STORED;
ALTER TABLE dryrun_ts DROP COLUMN sak_id;
ALTER TABLE dryrun_ts ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (try_jsonb_get_text(record_value, 'sakId')) STORED;
ALTER TABLE dryrun_tp DROP COLUMN sak_id;
ALTER TABLE dryrun_tp ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (try_jsonb_get_text(record_value, 'sakId')) STORED;

CREATE INDEX aap_sak_id_idx ON aap (sak_id);
CREATE INDEX aapintern_sak_id_idx ON aapintern (sak_id);
CREATE INDEX dp_sak_id_idx ON dp (sak_id);
CREATE INDEX dpintern_sak_id_idx ON dpintern (sak_id);
CREATE INDEX ts_sak_id_idx ON ts (sak_id);
CREATE INDEX tsintern_sak_id_idx ON tsintern (sak_id);
CREATE INDEX tpintern_sak_id_idx ON tpintern (sak_id);
CREATE INDEX historisk_sak_id_idx ON historisk (sak_id);
CREATE INDEX historiskintern_sak_id_idx ON historiskintern (sak_id);
CREATE INDEX valp_sak_id_idx ON valp (sak_id);
CREATE INDEX valpintern_sak_id_idx ON valpintern (sak_id);

ALTER TABLE saker DROP COLUMN sak_id;
ALTER TABLE saker DROP COLUMN fagsystem;
ALTER TABLE saker
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (try_jsonb_get_text(record_key, 'sakId')) STORED,
    ADD COLUMN fagsystem TEXT GENERATED ALWAYS AS (try_jsonb_get_text(record_key, 'fagsystem')) STORED;

CREATE INDEX saker_sak_id_fagsystem_idx ON saker (sak_id, fagsystem);
