CREATE TABLE mellomlagring_konsistensavstemming
(
    id                  UUID PRIMARY KEY,
    fagsystem           VARCHAR                             NOT NULL,
    transaksjons_id     UUID                                NOT NULL,
    antall_oppdrag      INTEGER                             NOT NULL,
    total_belop         BIGINT                              NOT NULL,
    opprettet_tidspunkt TIMESTAMP(3) DEFAULT localtimestamp NOT NULL
);

CREATE INDEX mellomlagring_konsistensavstemming_transaksjonsid_idx ON mellomlagring_konsistensavstemming (transaksjons_id);
