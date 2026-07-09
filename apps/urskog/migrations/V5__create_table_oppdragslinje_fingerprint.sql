CREATE TABLE oppdragslinje_fingerprint (
    id           BIGSERIAL PRIMARY KEY,
    fingerprint  TEXT NOT NULL,
    sak_id       TEXT NOT NULL,
    oppdrag_hash TEXT NOT NULL,
    delytelse_id TEXT,
    cancelled    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT unique_linje_fingerprint UNIQUE (fingerprint)
);

CREATE INDEX idx_linje_fp_sak_id ON oppdragslinje_fingerprint (sak_id);
