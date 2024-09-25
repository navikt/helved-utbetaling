create table frittstaende_brev
(
    id                       UUID PRIMARY KEY,
    person_ident             VARCHAR,
    ekstern_fagsak_id        NUMERIC,
    journalforende_enhet     VARCHAR,
    saksbehandler_ident      VARCHAR,
    stonadstype              VARCHAR,
    mottakere                JSON,
    fil                      BYTEA,
    brevtype                 VARCHAR,
    journalpost_resultat     JSON,
    distribuer_brev_resultat JSON,
    opprettet_tid            TIMESTAMP(3) DEFAULT LOCALTIMESTAMP
);
