CREATE TABLE IF NOT EXISTS utbetalingsoppdrag
(
    id            UUID        PRIMARY KEY,  -- technical
    utbetaling_id UUID        NOT NULL,
    sak_id        TEXT        NOT NULL,
    behandling_id TEXT        NOT NULL,
    personident   TEXT        NOT NULL,
    klassekode    TEXT        NOT NULL,
    data          JSON        NOT NULL,
    created_at    TIMESTAMP   NOT NULL,     -- technical
    updated_at    TIMESTAMP   NOT NULL      -- technical
);

