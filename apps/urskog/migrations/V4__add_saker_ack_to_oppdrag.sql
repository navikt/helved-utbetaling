ALTER TABLE oppdrag ADD COLUMN saker_ack BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE oppdrag ADD COLUMN saker_ack_at TIMESTAMP;

CREATE INDEX oppdrag_sak_id_saker_ack ON oppdrag (sak_id, saker_ack);
