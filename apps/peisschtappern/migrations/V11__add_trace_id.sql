ALTER TABLE avstemming ADD COLUMN trace_id TEXT;
ALTER TABLE oppdrag ADD COLUMN trace_id TEXT;
ALTER TABLE dryrun_aap ADD COLUMN trace_id TEXT;
ALTER TABLE dryrun_tp ADD COLUMN trace_id TEXT;
ALTER TABLE dryrun_ts ADD COLUMN trace_id TEXT;
ALTER TABLE dryrun_dp ADD COLUMN trace_id TEXT;
ALTER TABLE kvittering ADD COLUMN trace_id TEXT;
ALTER TABLE simuleringer ADD COLUMN trace_id TEXT;
ALTER TABLE utbetalinger ADD COLUMN trace_id TEXT;
ALTER TABLE saker ADD COLUMN trace_id TEXT;
ALTER TABLE aap ADD COLUMN trace_id TEXT;
ALTER TABLE status ADD COLUMN trace_id TEXT;
ALTER TABLE pending_utbetalinger ADD COLUMN trace_id TEXT;
ALTER TABLE fk ADD COLUMN trace_id TEXT;

