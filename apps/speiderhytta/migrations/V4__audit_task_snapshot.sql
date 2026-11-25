CREATE TABLE audit_task_snapshot (
    commit_id          BIGINT PRIMARY KEY REFERENCES audit_commit (id),
    task_repository    TEXT        NOT NULL,
    task_number        BIGINT      NOT NULL,
    fetch_status       TEXT        NOT NULL CHECK (fetch_status IN ('PRESENT', 'NOT_FOUND')),
    title              TEXT,
    body               TEXT,
    state              TEXT,
    labels             JSONB       NOT NULL,
    captured_at        TIMESTAMPTZ NOT NULL,
    CHECK ((fetch_status = 'PRESENT' AND title IS NOT NULL AND state IS NOT NULL)
        OR (fetch_status = 'NOT_FOUND' AND title IS NULL AND state IS NULL))
);
CREATE INDEX audit_task_snapshot_task_idx ON audit_task_snapshot (task_repository, task_number);

UPDATE audit_commit
SET task_repository = 'navikt/team-helved', task_number = 511
WHERE task_number IS NULL;
