CREATE TABLE oppdrag_lager
(
    id                   UUID PRIMARY KEY,
    utgaaende_oppdrag    TEXT                                NOT NULL,
    status               VARCHAR(150) DEFAULT 'LAGT_PAA_KOE':: character varying NOT NULL,
    opprettet_tidspunkt  TIMESTAMP(6) DEFAULT LOCALTIMESTAMP NOT NULL,
    person_ident         VARCHAR(50)                         NOT NULL,
    fagsak_id            VARCHAR(50)                         NOT NULL,
    behandling_id        VARCHAR(50)                         NOT NULL,
    fagsystem            VARCHAR(10)                         NOT NULL,
    avstemming_tidspunkt TIMESTAMP(6)                        NOT NULL,
    utbetalingsoppdrag   JSON                                NOT NULL,
    kvitteringsmelding   JSON,
    versjon              BIGINT       DEFAULT 0              NOT NULL
);

CREATE UNIQUE INDEX oppdragsid_idx ON oppdrag_lager (person_ident, behandling_id, fagsystem, versjon);

CREATE INDEX status_idx ON oppdrag_lager (status);

CREATE INDEX behandling_person_idx ON oppdrag_lager (behandling_id, person_ident);
