CREATE TABLE task
(
    id            UUID PRIMARY KEY,
    payload       VARCHAR     NOT NULL,
    status        VARCHAR(20) NOT NULL,
    type          VARCHAR     NOT NULL,
    versjon       BIGINT      NOT NULL,
    opprettet_tid TIMESTAMP   NOT NULL,
    metadata      VARCHAR,
    avvikstype    VARCHAR,
    trigger_tid   TIMESTAMP   NOT NULL
);

CREATE TABLE task_logg
(
    id            UUID PRIMARY KEY,
    task_id       UUID REFERENCES task (id) ON DELETE CASCADE,
    type          VARCHAR   NOT NULL,
    node          VARCHAR   NOT NULL,
    opprettet_tid TIMESTAMP NOT NULL,
    endret_av     VARCHAR   NOT NULL,
    melding       VARCHAR
);

CREATE UNIQUE INDEX IF NOT EXISTS task_payload_type_idx ON task (payload, type);
CREATE INDEX IF NOT EXISTS task_status_idx ON task (status);
