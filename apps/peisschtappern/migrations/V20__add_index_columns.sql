CREATE OR REPLACE FUNCTION get_xml_field(xml_data TEXT, xpath_query TEXT, ns_prefixes TEXT[][])
    RETURNS TEXT AS $$
BEGIN
    RETURN (xpath(xpath_query, NULLIF(xml_data, '')::xml, ns_prefixes))[1]::text;
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

ALTER TABLE oppdrag
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (
        get_xml_field(record_value, '//ns2:oppdrag/oppdrag-110/fagsystemId/text()', ARRAY [ARRAY ['ns2', 'http://www.trygdeetaten.no/skjema/oppdrag']])
        ) STORED,
    ADD COLUMN fagsystem TEXT GENERATED ALWAYS AS (
        get_xml_field(record_value, '//ns2:oppdrag/oppdrag-110/kodeFagomraade/text()',ARRAY [ARRAY ['ns2', 'http://www.trygdeetaten.no/skjema/oppdrag']])
        ) STORED;

ALTER TABLE utbetalinger
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (record_value::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT GENERATED ALWAYS AS (record_value::jsonb ->> 'fagsystem') STORED;

ALTER TABLE simuleringer
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (
        get_xml_field(record_value, '/ns3:simulerBeregningRequest/request/oppdrag/fagsystemId/text()',ARRAY [ARRAY ['ns3', 'http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt']])
        ) STORED,
    ADD COLUMN fagsystem TEXT GENERATED ALWAYS AS (
        get_xml_field(record_value, '/ns3:simulerBeregningRequest/request/oppdrag/kodeFagomraade/text()',  ARRAY[ARRAY['ns3', 'http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt']])
      ) STORED;

ALTER TABLE kvittering
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (
        get_xml_field(record_value, '//ns2:oppdrag/oppdrag-110/fagsystemId/text()',
                      ARRAY [ARRAY ['ns2', 'http://www.trygdeetaten.no/skjema/oppdrag']])
        ) STORED,
    ADD COLUMN fagsystem TEXT GENERATED ALWAYS AS (
        get_xml_field(record_value, '//ns2:oppdrag/oppdrag-110/kodeFagomraade/text()', ARRAY [ARRAY ['ns2', 'http://www.trygdeetaten.no/skjema/oppdrag']])
        ) STORED;

ALTER TABLE pending_utbetalinger
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (NULLIF(record_value, '')::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT GENERATED ALWAYS AS (NULLIF(record_value, '')::jsonb ->> 'fagsystem') STORED;

ALTER TABLE aap
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (NULLIF(record_value, '')::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE aapIntern
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (NULLIF(record_value, '')::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE dp
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (NULLIF(record_value, '')::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE dpIntern
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (NULLIF(record_value, '')::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE ts
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (NULLIF(record_value, '')::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE tsIntern
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (NULLIF(record_value, '')::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE tp
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (NULLIF(record_value, '')::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE tpIntern
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (NULLIF(record_value, '')::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE historisk
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (NULLIF(record_value, '')::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE historiskIntern
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (NULLIF(record_value, '')::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE fk
    ADD COLUMN sak_id TEXT,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE status
    ADD COLUMN sak_id TEXT,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE avstemming
    ADD COLUMN sak_id TEXT,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE dryrun_aap
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (NULLIF(record_value, '')::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE dryrun_tp
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (NULLIF(record_value, '')::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE dryrun_ts
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (NULLIF(record_value, '')::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE dryrun_dp
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (NULLIF(record_value, '')::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT;

ALTER TABLE saker
    ADD COLUMN sak_id TEXT,
    ADD COLUMN fagsystem TEXT;

CREATE INDEX oppdrag_lookup_idx ON oppdrag(sak_id, fagsystem);
CREATE INDEX kvittering_lookup_idx ON kvittering(sak_id, fagsystem);
CREATE INDEX simuleringer_lookup_idx ON simuleringer(sak_id, fagsystem);
CREATE INDEX utbetalinger_lookup_idx ON utbetalinger(sak_id, fagsystem);
CREATE INDEX pending_utbetalinger_lookup_idx ON pending_utbetalinger(sak_id, fagsystem);

