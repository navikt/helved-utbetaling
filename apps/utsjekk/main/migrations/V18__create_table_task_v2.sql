CREATE TABLE IF NOT EXISTS task_v2
(
    id            UUID PRIMARY KEY,
    kind          VARCHAR     NOT NULL,
    payload       VARCHAR     NOT NULL,
    status        VARCHAR(20) NOT NULL,
    attempt       BIGINT      NOT NULL,
    created_at    TIMESTAMP   NOT NULL,
    updated_at    TIMESTAMP   NOT NULL,
    scheduled_for TIMESTAMP   NOT NULL,
    message       VARCHAR
);

CREATE TABLE IF NOT EXISTS task_v2_history
(
    id           UUID PRIMARY KEY,
    task_id      UUID REFERENCES task_v2 (id) ON DELETE CASCADE,
    created_at   TIMESTAMP NOT NULL,
    triggered_at TIMESTAMP NOT NULL,
    triggered_by TIMESTAMP NOT NULL,
    status       VARCHAR(20)
);

CREATE INDEX IF NOT EXISTS task_v2_status_idx ON task_v2 (status);
