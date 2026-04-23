ALTER DEFAULT PRIVILEGES IN SCHEMA PUBLIC GRANT ALL ON TABLES TO CLOUDSQLIAMUSER;

CREATE TABLE deployment (
    id                 BIGSERIAL PRIMARY KEY,
    app                TEXT        NOT NULL,
    sha                TEXT        NOT NULL,
    env                TEXT        NOT NULL,
    commit_ts          TIMESTAMPTZ NOT NULL,
    deploy_started_ts  TIMESTAMPTZ NOT NULL,
    deploy_finished_ts TIMESTAMPTZ NOT NULL,
    lead_time_seconds  BIGINT      NOT NULL,
    run_url            TEXT,
    UNIQUE (app, sha, env)
);
CREATE INDEX deployment_app_env_finished_idx ON deployment (app, env, deploy_finished_ts DESC);

CREATE TABLE incident (
    id                       BIGSERIAL PRIMARY KEY,
    github_issue             BIGINT      NOT NULL UNIQUE,
    app                      TEXT        NOT NULL,
    title                    TEXT        NOT NULL,
    opened_at                TIMESTAMPTZ NOT NULL,
    resolved_at              TIMESTAMPTZ,
    mttr_seconds             BIGINT,
    caused_by_sha            TEXT,
    caused_by_deployment_id  BIGINT REFERENCES deployment (id)
);
CREATE INDEX incident_app_idx ON incident (app);
CREATE INDEX incident_app_opened_idx ON incident (app, opened_at DESC);

CREATE TABLE poller_cursor (
    poller        TEXT PRIMARY KEY,
    last_seen_ts  TIMESTAMPTZ NOT NULL,
    last_etag     TEXT
);

CREATE TABLE slo_snapshot (
    id                      BIGSERIAL PRIMARY KEY,
    app                     TEXT             NOT NULL,
    slo_name                TEXT             NOT NULL,
    objective               DOUBLE PRECISION NOT NULL,
    sli_value               DOUBLE PRECISION NOT NULL,
    error_budget_remaining  DOUBLE PRECISION NOT NULL,
    burn_rate_1h            DOUBLE PRECISION,
    burn_rate_6h            DOUBLE PRECISION,
    captured_at             TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);
CREATE INDEX slo_snapshot_app_name_time_idx ON slo_snapshot (app, slo_name, captured_at DESC);
