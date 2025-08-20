create table dp
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
    trace_id         text
);

create index dp_key on dp (record_key);
create index dp_timestamp_ms_idx on dp (timestamp_ms DESC);

create table dpIntern
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
    trace_id         text
);

create index dpIntern_key on dpIntern (record_key);
create index dpIntern_timestamp_ms_idx on dpIntern (timestamp_ms DESC);
