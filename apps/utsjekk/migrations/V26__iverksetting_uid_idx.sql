-- iverksettingsresultat and iverksetting is queried by utbetaling_id
CREATE INDEX IF NOT EXISTS iverksettingresultat_uid_idx ON iverksettingsresultat (utbetaling_id);
CREATE INDEX IF NOT EXISTS iverksetting_uid_idx ON iverksetting (utbetaling_id);

-- jsonb is improved over json
ALTER TABLE iverksetting ALTER COLUMN data TYPE jsonb USING data::jsonb;

-- expression indexes for used json path queries
CREATE INDEX idx_iverksetting_fagsakid ON iverksetting ((data -> 'fagsak' ->> 'fagsakId'));
CREATE INDEX idx_iverksetting_iverksettingid ON iverksetting ((data -> 'behandling' ->> 'iverksettingId'));
CREATE INDEX idx_iverksetting_fagsystem ON iverksetting ((data -> 'fagsak' ->> 'fagsystem'));
CREATE INDEX idx_iverksetting_behandlingid ON iverksetting (behandling_id);

