create table iverksett_resultat
(
    behandling_id                       uuid primary key,
    tilkjentYtelseForUtbetaling         json,
    oppdragResultat                     json,
    journalpostResultat                 json,
    vedtaksBrevResultat                 json,
    tilbakekrevingResultat              json
);