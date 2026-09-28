CREATE TABLE audit_commit (
    id                    BIGSERIAL PRIMARY KEY,
    repository            TEXT        NOT NULL,
    sha                   TEXT        NOT NULL,
    task_repository       TEXT,
    task_number           BIGINT,
    message               TEXT        NOT NULL,
    author_login          TEXT,
    committer_login       TEXT,
    authored_at           TIMESTAMPTZ NOT NULL,
    committed_at          TIMESTAMPTZ NOT NULL,
    signature_verified    BOOLEAN,
    verification_reason   TEXT,
    parents               JSONB       NOT NULL,
    raw_metadata          JSONB       NOT NULL,
    captured_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (repository, sha)
);
CREATE INDEX audit_commit_task_idx ON audit_commit (task_repository, task_number);
CREATE INDEX audit_commit_committed_idx ON audit_commit (repository, committed_at DESC);

CREATE TABLE audit_task_comment (
    task_repository    TEXT        NOT NULL,
    task_number        BIGINT      NOT NULL,
    github_comment_id  BIGINT,
    rendered_sha256    TEXT,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (task_repository, task_number)
);

CREATE TABLE audit_workflow_execution (
    id                      BIGSERIAL PRIMARY KEY,
    repository              TEXT        NOT NULL,
    app                     TEXT        NOT NULL,
    workflow_file           TEXT        NOT NULL,
    workflow_path           TEXT,
    run_id                  BIGINT      NOT NULL,
    run_attempt             INTEGER     NOT NULL,
    head_sha                TEXT        NOT NULL,
    event                   TEXT        NOT NULL,
    status                  TEXT        NOT NULL,
    conclusion              TEXT,
    actor_login             TEXT,
    triggering_actor_login  TEXT,
    created_at              TIMESTAMPTZ NOT NULL,
    run_started_at          TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ NOT NULL,
    run_url                 TEXT        NOT NULL,
    raw_metadata            JSONB       NOT NULL,
    captured_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (repository, run_id, run_attempt)
);
CREATE INDEX audit_workflow_execution_sha_idx ON audit_workflow_execution (repository, head_sha);
CREATE INDEX audit_workflow_execution_app_idx ON audit_workflow_execution (app, updated_at DESC);

CREATE TABLE audit_workflow_source (
    id                 BIGSERIAL PRIMARY KEY,
    workflow_execution_id BIGINT      NOT NULL REFERENCES audit_workflow_execution (id) ON DELETE CASCADE,
    path               TEXT        NOT NULL,
    source_ref         TEXT        NOT NULL,
    blob_sha           TEXT        NOT NULL,
    content            TEXT        NOT NULL,
    content_sha256     TEXT        NOT NULL,
    raw_metadata       JSONB       NOT NULL,
    UNIQUE (workflow_execution_id)
);

CREATE TABLE audit_workflow_job (
    id                 BIGSERIAL PRIMARY KEY,
    workflow_execution_id BIGINT      NOT NULL REFERENCES audit_workflow_execution (id) ON DELETE CASCADE,
    github_job_id      BIGINT      NOT NULL,
    name               TEXT        NOT NULL,
    status             TEXT        NOT NULL,
    conclusion         TEXT,
    created_at         TIMESTAMPTZ NOT NULL,
    started_at         TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    raw_metadata       JSONB       NOT NULL,
    UNIQUE (workflow_execution_id, github_job_id)
);
CREATE INDEX audit_workflow_job_name_idx ON audit_workflow_job (workflow_execution_id, name);

CREATE TABLE audit_workflow_step (
    id                 BIGSERIAL PRIMARY KEY,
    workflow_job_id    BIGINT      NOT NULL REFERENCES audit_workflow_job (id) ON DELETE CASCADE,
    step_number        INTEGER     NOT NULL,
    name               TEXT        NOT NULL,
    status             TEXT        NOT NULL,
    conclusion         TEXT,
    started_at         TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    raw_metadata       JSONB       NOT NULL,
    UNIQUE (workflow_job_id, step_number)
);

CREATE TABLE audit_workflow_execution_commit (
    workflow_execution_id BIGINT NOT NULL REFERENCES audit_workflow_execution (id) ON DELETE CASCADE,
    commit_id          BIGINT NOT NULL REFERENCES audit_commit (id),
    PRIMARY KEY (workflow_execution_id, commit_id)
);

CREATE TABLE audit_control_snapshot (
    id                        BIGSERIAL PRIMARY KEY,
    repository                TEXT        NOT NULL,
    branch                    TEXT        NOT NULL,
    control_type              TEXT        NOT NULL
        CHECK (control_type IN ('branch_protection', 'repository_rulesets')),
    fetch_status              TEXT        NOT NULL
        CHECK (fetch_status IN ('PRESENT', 'NOT_FOUND', 'FORBIDDEN', 'FAILED')),
    payload                   JSONB       NOT NULL,
    payload_sha256            TEXT        NOT NULL,
    captured_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX audit_control_snapshot_lookup_idx
    ON audit_control_snapshot (repository, branch, control_type, captured_at DESC);
