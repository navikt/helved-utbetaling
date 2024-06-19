CREATE TABLE task
(
    id            UUID PRIMARY KEY,
    payload       VARCHAR     NOT NULL,
    status        VARCHAR(20) NOT NULL,
    attempt       BIGINT      NOT NULL,
    created_at    TIMESTAMP   NOT NULL,
    updated_at    TIMESTAMP   NOT NULL,
    scheduled_for TIMESTAMP   NOT NULL,
    message       VARCHAR
);

CREATE TABLE task_history
(
    id           UUID PRIMARY KEY,
    task_id      UUID REFERENCES task (id) ON DELETE CASCADE,
    created_at   TIMESTAMP NOT NULL,
    triggered_at TIMESTAMP NOT NULL,
    triggered_by TIMESTAMP NOT NULL,
    status       VARCHAR(20)
);

CREATE INDEX IF NOT EXISTS task_status_idx ON task (status);
