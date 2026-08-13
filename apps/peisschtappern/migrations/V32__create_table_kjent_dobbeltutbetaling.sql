CREATE TABLE kjent_dobbeltutbetaling (
    behandling_id TEXT NOT NULL,
    klassekode    TEXT NOT NULL,
    fom           DATE NOT NULL,
    tom           DATE NOT NULL,
    registrert_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (behandling_id, klassekode, fom, tom)
);
