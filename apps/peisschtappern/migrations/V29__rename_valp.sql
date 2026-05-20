BEGIN;

ALTER TABLE valp
    ALTER COLUMN fagsystem SET DEFAULT 'TILSOPP';

ALTER TABLE valpIntern
    ALTER COLUMN fagsystem SET DEFAULT 'TILSOPP';

UPDATE valp
SET fagsystem = 'TILSOPP'
WHERE fagsystem = 'TILLSOPP';

UPDATE valpIntern
SET fagsystem = 'TILSOPP'
WHERE fagsystem = 'TILLSOPP';

COMMIT;