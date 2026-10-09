ALTER TABLE oppdrag
    ADD COLUMN nokkel_avstemming TEXT GENERATED ALWAYS AS (
        btrim(get_xml_field(record_value,
            '//ns2:oppdrag/oppdrag-110/avstemming-115/nokkelAvstemming/text()',
            ARRAY[ARRAY['ns2', 'http://www.trygdeetaten.no/skjema/oppdrag']]))
    ) STORED;

CREATE INDEX oppdrag_nokkel_avstemming_idx ON oppdrag(nokkel_avstemming);
