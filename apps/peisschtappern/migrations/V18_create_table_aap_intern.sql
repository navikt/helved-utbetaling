create table aapIntern
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
    trace_id         text
);

create index aapIntern_key on aapIntern (record_key);
create index aapIntern_timestamp_ms_idx on aapIntern (timestamp_ms DESC);
