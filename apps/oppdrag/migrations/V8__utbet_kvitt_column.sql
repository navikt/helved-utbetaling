ALTER   TABLE  utbetalingsoppdrag
    ADD COLUMN kvittering         JSON,
    ADD COLUMN fagsystem          TEXT NOT NULL,
    ADD COLUMN status             TEXT NOT NULL;
