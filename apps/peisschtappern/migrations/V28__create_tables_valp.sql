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

create table valp
(
    id               bigserial primary key,
    version          text,
    topic_name       text,
    record_key       text,
    record_value     text,
    record_partition int,
    record_offset    bigint,
    timestamp_ms     bigint,
    stream_time_ms   bigint,
    system_time_ms   bigint,
    commit           text,
    trace_id         text,
    headers          text,
    sak_id           text generated always as (try_jsonb_get_text(record_value, 'sakId')) stored,
    fagsystem        text default 'TILLSOPP',
    status           text
);

create index valp_key on valp (record_key);
create index valp_timestamp_ms_idx on valp (timestamp_ms DESC);

create table valpIntern
(
    id               bigserial primary key,
    version          text,
    topic_name       text,
    record_key       text,
    record_value     text,
    record_partition int,
    record_offset    bigint,
    timestamp_ms     bigint,
    stream_time_ms   bigint,
    system_time_ms   bigint,
    commit           text,
    trace_id         text,
    headers          text,
    sak_id           text generated always as (try_jsonb_get_text(record_value, 'sakId')) stored,
    fagsystem        text default 'TILLSOPP',
    status           text
);

create index valpIntern_key on valpIntern (record_key);
create index valpIntern_timestamp_ms_idx on valpIntern (timestamp_ms DESC);

create index valp_system_time_ms_idx on valp (system_time_ms DESC);
create index valp_intern_system_time_ms_idx on valpIntern (system_time_ms DESC);