create table dryrun_valp
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
    trace_id         text,
    commit           text,
    status           text,
    headers          text,
    sak_id           text GENERATED ALWAYS AS (try_jsonb_get_text(record_value, 'sakId')) STORED,
    fagsystem        text DEFAULT 'TILSOPP'
);

create index dryrun_valp_key on dryrun_valp (record_key);
create index dryrun_valp_timestamp_ms_idx on dryrun_valp (timestamp_ms DESC);
create index dryrun_valp_system_time_ms_idx on dryrun_valp (system_time_ms DESC);

create table dryrun_historisk
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
    trace_id         text,
    commit           text,
    status           text,
    headers          text,
    sak_id           text GENERATED ALWAYS AS (try_jsonb_get_text(record_value, 'sakId')) STORED,
    fagsystem        text DEFAULT 'HELSREF'
);

create index dryrun_historisk_key on dryrun_historisk (record_key);
create index dryrun_historisk_timestamp_ms_idx on dryrun_historisk (timestamp_ms DESC);
create index dryrun_historisk_system_time_ms_idx on dryrun_historisk (system_time_ms DESC);

