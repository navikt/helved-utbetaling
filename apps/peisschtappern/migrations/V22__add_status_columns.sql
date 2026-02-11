ALTER TABLE oppdrag
    ADD COLUMN status TEXT GENERATED ALWAYS AS (
        CASE
            WHEN get_xml_field(
                         record_value,
                         '//ns2:oppdrag/mmel/alvorlighetsgrad/text()',
                         ARRAY[ARRAY['ns2', 'http://www.trygdeetaten.no/skjema/oppdrag']]
                 ) = '00'
                THEN 'OK'
            WHEN get_xml_field(
                    record_value,
                    '//ns2:oppdrag/mmel/alvorlighetsgrad/text()',
                    ARRAY[ARRAY['ns2', 'http://www.trygdeetaten.no/skjema/oppdrag']]
                 ) IS NOT NULL
                THEN 'FEILET'
            END
        ) STORED;

ALTER TABLE status
    ADD COLUMN status TEXT GENERATED ALWAYS AS (
        record_value::jsonb ->> 'status'
        ) STORED;

CREATE INDEX idx_your_table_alvorlighetsgrad ON oppdrag(status);
CREATE INDEX idx_your_table_status ON status(status);

ALTER TABLE avstemming ADD COLUMN status TEXT;

ALTER TABLE simuleringer ADD COLUMN status TEXT;
ALTER TABLE utbetalinger ADD COLUMN status TEXT;
ALTER TABLE saker ADD COLUMN status TEXT;
ALTER TABLE aapintern ADD COLUMN status TEXT;
ALTER TABLE dryrun_aap ADD COLUMN status TEXT;
ALTER TABLE dryrun_tp ADD COLUMN status TEXT;
ALTER TABLE dryrun_ts ADD COLUMN status TEXT;
ALTER TABLE dryrun_dp ADD COLUMN status TEXT;

ALTER TABLE pending_utbetalinger ADD COLUMN status TEXT;
ALTER TABLE fk ADD COLUMN status TEXT;
ALTER TABLE dpintern ADD COLUMN status TEXT;
ALTER TABLE dp ADD COLUMN status TEXT;
ALTER TABLE tsintern ADD COLUMN status TEXT;
ALTER TABLE tpintern ADD COLUMN status TEXT;
ALTER TABLE ts ADD COLUMN status TEXT;
ALTER TABLE aap ADD COLUMN status TEXT;
ALTER TABLE historiskintern ADD COLUMN status TEXT;
ALTER TABLE historisk ADD COLUMN status TEXT;
