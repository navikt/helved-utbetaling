CREATE TABLE IF NOT EXISTS utbetaling
(
    id            UUID        PRIMARY KEY,  -- technical
    utbetaling_id UUID        NOT NULL,
    sak_id        TEXT        NOT NULL,
    behandling_id TEXT        NOT NULL,
    personident   TEXT        NOT NULL,
    stønad        TEXT        NOT NULL,
    data          JSON        NOT NULL,
    created_at    TIMESTAMP   NOT NULL,     -- technical
    updated_at    TIMESTAMP   NOT NULL      -- technical
);

CREATE INDEX IF NOT EXISTS utbetaling_uid_idx ON utbetaling (utbetaling_id);

CREATE TABLE IF NOT EXISTS utbetaling_status
(
    id            UUID        PRIMARY KEY,  -- technical
    utbetaling_id UUID        NOT NULL,
    created_at    TIMESTAMP   NOT NULL,     -- technical
    updated_at    TIMESTAMP   NOT NULL,     -- technical
    status        TEXT        NOT NULL 
);

CREATE INDEX IF NOT EXISTS utbetaling_status_uid_idx ON utbetaling_status (utbetaling_id);
