CREATE OR REPLACE FUNCTION try_jsonb_get_text(v text, k text)
    RETURNS text
    LANGUAGE plpgsql
    IMMUTABLE
AS $$
BEGIN
    IF v IS NULL OR v = '' THEN
        RETURN NULL;
    END IF;

    RETURN v::jsonb ->> k;
EXCEPTION
    WHEN others THEN
        RETURN NULL;
END;
$$;

ALTER TABLE aap DROP COLUMN sak_id;
ALTER TABLE aap ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (try_jsonb_get_text(record_value, 'sakId')) STORED;

ALTER TABLE aapintern DROP COLUMN sak_id;
ALTER TABLE aapintern ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (try_jsonb_get_text(record_value, 'sakId')) STORED;

ALTER TABLE dp DROP COLUMN sak_id;
ALTER TABLE dp ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (try_jsonb_get_text(record_value, 'sakId')) STORED;

ALTER TABLE dpintern DROP COLUMN sak_id;
ALTER TABLE dpintern ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (try_jsonb_get_text(record_value, 'sakId')) STORED;

ALTER TABLE ts DROP COLUMN sak_id;
ALTER TABLE ts ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (try_jsonb_get_text(record_value, 'sakId')) STORED;

ALTER TABLE tsintern DROP COLUMN sak_id;
ALTER TABLE tsintern ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (try_jsonb_get_text(record_value, 'sakId')) STORED;

ALTER TABLE tp DROP COLUMN sak_id;
ALTER TABLE tp ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (try_jsonb_get_text(record_value, 'sakId')) STORED;

ALTER TABLE tpintern DROP COLUMN sak_id;
ALTER TABLE tpintern ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (try_jsonb_get_text(record_value, 'sakId')) STORED;

ALTER TABLE historisk DROP COLUMN sak_id;
ALTER TABLE historisk ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (try_jsonb_get_text(record_value, 'sakId')) STORED;

ALTER TABLE historiskintern DROP COLUMN sak_id;
ALTER TABLE historiskintern ADD COLUMN sak_id TEXT GENERATED ALWAYS AS (try_jsonb_get_text(record_value, 'sakId')) STORED;
