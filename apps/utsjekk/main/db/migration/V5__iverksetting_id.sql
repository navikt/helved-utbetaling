ALTER TABLE oppdrag_lager
    ADD COLUMN iverksetting_id VARCHAR;

CREATE UNIQUE INDEX iverksetting_idx ON oppdrag_lager (fagsystem, fagsak_id, behandling_id, iverksetting_id, versjon) NULLS NOT DISTINCT;

DROP INDEX oppdragsid_idx;