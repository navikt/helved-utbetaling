-- Trengs for at markSakerAck (Kafka.kt) skal kunne skille mellom uids som skal
-- være TIL STEDE (CREATE/UPDATE/FAKE_DELETE) og uids som skal være FRAVÆRENDE
-- (DELETE/opphør) i saker-aggregatet før den vurderer barrieren som klar.
-- Default 'CREATE' bevarer gammel oppførsel (krev tilstedeværelse) for rader
-- lagret før denne migreringen.
ALTER TABLE pending_utbetaling ADD COLUMN action TEXT NOT NULL DEFAULT 'CREATE';
