-- Switch deploy source from NAIS Deploy API (which doesn't actually exist) to
-- GitHub Actions workflow runs. Adds outcome (success|failure|cancelled) so
-- deploy failures count towards CFR, and run_id so re-runs of the same commit
-- are tracked as separate attempts.

ALTER TABLE deployment
    ADD COLUMN outcome TEXT   NOT NULL DEFAULT 'success'
        CHECK (outcome IN ('success', 'failure', 'cancelled')),
    ADD COLUMN run_id  BIGINT NOT NULL DEFAULT 0;

-- Replace the (app, sha, env) uniqueness with (app, sha, env, run_id) so a
-- re-run of the same commit produces a second row with its own outcome.
ALTER TABLE deployment
    DROP CONSTRAINT deployment_app_sha_env_key;

ALTER TABLE deployment
    ADD CONSTRAINT deployment_app_sha_env_run_key UNIQUE (app, sha, env, run_id);

CREATE INDEX deployment_app_finished_outcome_idx
    ON deployment (app, deploy_finished_ts DESC, outcome);
