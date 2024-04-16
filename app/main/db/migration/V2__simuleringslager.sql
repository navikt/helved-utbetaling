CREATE TABLE simulering_lager
(
    id                  UUID PRIMARY KEY,
    fagsak_id           TEXT                                NOT NULL,
    behandling_id       TEXT                                NOT NULL,
    fagsystem           TEXT                                NOT NULL,
    opprettet_tidspunkt TIMESTAMP(3) DEFAULT localtimestamp NOT NULL,
    utbetalingsoppdrag  TEXT                                NOT NULL,
    request_xml         TEXT                                NOT NULL,
    response_xml        TEXT
);

CREATE INDEX simuleringsid_idx ON simulering_lager (behandling_id, fagsak_id, fagsystem);
