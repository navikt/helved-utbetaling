ALTER TABLE oppdrag
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (
        (xpath('//ns2:oppdrag/oppdrag-110/fagsystemId/text()',
               NULLIF(record_value, '')::xml,
               ARRAY [ARRAY ['ns2', 'http://www.trygdeetaten.no/skjema/oppdrag']]))[1]::text)
        STORED,
    ADD COLUMN fagsystem TEXT GENERATED ALWAYS AS (
        (xpath('//ns2:oppdrag/oppdrag-110/kodeFagomraade/text()',
               NULLIF(record_value, '')::xml,
               ARRAY [ARRAY ['ns2', 'http://www.trygdeetaten.no/skjema/oppdrag']]))[1]::text)
        STORED;

ALTER TABLE utbetalinger
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (record_value::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT GENERATED ALWAYS AS (record_value::jsonb ->> 'fagsystem') STORED;

ALTER TABLE pending_utbetalinger
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (record_value::jsonb ->> 'sakId') STORED,
    ADD COLUMN fagsystem TEXT GENERATED ALWAYS AS (record_value::jsonb ->> 'fagsystem') STORED;

ALTER TABLE simuleringer
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (
        (xpath('/ns3:simulerBeregningRequest/request/oppdrag/fagsystemId/text()',
               NULLIF(record_value, '')::xml,
               ARRAY[ARRAY['ns3', 'http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt']]))[1]::text)
        STORED,
    ADD COLUMN fagsystem TEXT GENERATED ALWAYS AS (
        (xpath('/ns3:simulerBeregningRequest/request/oppdrag/kodeFagomraade/text()',
               NULLIF(record_value, '')::xml,
               ARRAY[ARRAY['ns3', 'http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt']]))[1]::text)
        STORED;

ALTER TABLE kvittering
    ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (
        (xpath('//ns2:oppdrag/oppdrag-110/fagsystemId/text()',
               NULLIF(record_value, '')::xml,
               ARRAY [ARRAY ['ns2', 'http://www.trygdeetaten.no/skjema/oppdrag']]))[1]::text)
        STORED,
    ADD COLUMN fagsystem TEXT GENERATED ALWAYS AS (
        (xpath('//ns2:oppdrag/oppdrag-110/kodeFagomraade/text()',
               NULLIF(record_value, '')::xml,
               ARRAY [ARRAY ['ns2', 'http://www.trygdeetaten.no/skjema/oppdrag']]))[1]::text)
        STORED;


CREATE INDEX oppdrag_lookup_idx ON oppdrag(sak_id, fagsystem);
CREATE INDEX kvittering_lookup_idx ON kvittering(sak_id, fagsystem);
CREATE INDEX simuleringer_lookup_idx ON simuleringer(sak_id, fagsystem);
CREATE INDEX utbetalinger_lookup_idx ON utbetalinger(sak_id, fagsystem);
CREATE INDEX pending_utbetalinger_lookup_idx ON pending_utbetalinger(sak_id, fagsystem);