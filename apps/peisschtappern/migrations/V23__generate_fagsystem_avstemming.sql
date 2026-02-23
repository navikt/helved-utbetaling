ALTER TABLE avstemming
    ADD COLUMN fagsystem_new text
        GENERATED ALWAYS AS (
            get_xml_field(
                    record_value,
                    '//ns2:avstemmingsdata/aksjon/avleverendeKomponentKode/text()',
                    ARRAY[ARRAY['ns2','http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1']]
            )
            ) STORED;

ALTER TABLE avstemming
    DROP COLUMN fagsystem;

ALTER TABLE avstemming
    RENAME COLUMN fagsystem_new TO fagsystem;