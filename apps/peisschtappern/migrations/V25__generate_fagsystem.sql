-- status

ALTER TABLE status
    ADD COLUMN fagsystem_new text
        GENERATED ALWAYS AS (
            record_value::jsonb -> 'detaljer' ->> 'ytelse'
        ) STORED;

ALTER TABLE status
    DROP COLUMN fagsystem;

ALTER TABLE status
    RENAME COLUMN fagsystem_new TO fagsystem;

-- saker

ALTER TABLE saker
    ADD COLUMN fagsystem_new text
        GENERATED ALWAYS AS (
            record_key::jsonb ->> 'fagsystem'
        ) STORED;

ALTER TABLE saker
    DROP COLUMN fagsystem;

ALTER TABLE saker
    RENAME COLUMN fagsystem_new to fagsystem;

-- hard-kodete konsument-topics

UPDATE aap SET fagsystem = 'AAP' WHERE fagsystem is null;
ALTER TABLE aap ALTER COLUMN fagsystem SET DEFAULT 'AAP';
UPDATE aapIntern SET fagsystem = 'AAP' WHERE fagsystem is null;
ALTER TABLE aapIntern ALTER COLUMN fagsystem SET DEFAULT 'AAP';
UPDATE dryrun_aap SET fagsystem = 'AAP' WHERE fagsystem is null;
ALTER TABLE dryrun_aap ALTER COLUMN fagsystem SET DEFAULT 'AAP';

UPDATE dp SET fagsystem = 'DP' WHERE fagsystem is null;
ALTER TABLE dp ALTER COLUMN fagsystem SET DEFAULT 'DP';
UPDATE dpIntern SET fagsystem = 'DP' WHERE fagsystem is null;
ALTER TABLE dpIntern ALTER COLUMN fagsystem SET DEFAULT 'DP';
UPDATE dryrun_dp SET fagsystem = 'DP' WHERE fagsystem is null;
ALTER TABLE dryrun_dp ALTER COLUMN fagsystem SET DEFAULT 'DP';

UPDATE ts SET fagsystem = 'TILLST' WHERE fagsystem is null;
ALTER TABLE ts ALTER COLUMN fagsystem SET DEFAULT 'TILLST';
UPDATE tsIntern SET fagsystem = 'TILLST' WHERE fagsystem is null;
ALTER TABLE tsIntern ALTER COLUMN fagsystem SET DEFAULT 'TILLST';
UPDATE dryrun_ts SET fagsystem = 'TILLST' WHERE fagsystem is null;
ALTER TABLE dryrun_ts ALTER COLUMN fagsystem SET DEFAULT 'TILLST';

UPDATE tp SET fagsystem = 'TILTPENG' WHERE fagsystem is null;
ALTER TABLE tp ALTER COLUMN fagsystem SET DEFAULT 'TILTPENG';
UPDATE tpIntern SET fagsystem = 'TILTPENG' WHERE fagsystem is null;
ALTER TABLE tpIntern ALTER COLUMN fagsystem SET DEFAULT 'TILTPENG';
UPDATE dryrun_tp SET fagsystem = 'TILTPENG' WHERE fagsystem is null;
ALTER TABLE dryrun_tp ALTER COLUMN fagsystem SET DEFAULT 'TILTPENG';

UPDATE historisk SET fagsystem = 'HELSREF' WHERE fagsystem is null;
ALTER TABLE historisk ALTER COLUMN fagsystem SET DEFAULT 'HELSREF';
UPDATE historiskIntern SET fagsystem = 'HELSREF' WHERE fagsystem is null;
ALTER TABLE historiskIntern ALTER COLUMN fagsystem SET DEFAULT 'HELSREF';
