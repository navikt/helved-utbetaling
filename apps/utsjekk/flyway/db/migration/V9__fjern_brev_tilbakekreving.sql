alter table iverksett_resultat
    drop column journalpostResultat,
    drop column tilbakekrevingResultat,
    drop column vedtaksBrevResultat;

drop table if exists frittstaende_brev;
drop table if exists brev;