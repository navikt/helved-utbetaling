CREATE TABLE task
(
    id            UUID PRIMARY KEY,
    payload       VARCHAR     NOT NULL,
    status        VARCHAR(20) NOT NULL,
    type          VARCHAR     NOT NULL,
    versjon       BIGINT      NOT NULL,
    opprettet_tid TIMESTAMP   NOT NULL,
    metadata      VARCHAR     NOT NULL,
    avvikstype    VARCHAR     NOT NULL,
    trigger_tid   TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS task_payload_type_idx ON task (payload, type);
CREATE INDEX IF NOT EXISTS task_status_idx ON task (status);
