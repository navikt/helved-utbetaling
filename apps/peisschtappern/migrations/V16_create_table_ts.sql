create table ts
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

create index ts_key on ts (record_key);
create index ts_timestamp_ms_idx on ts (timestamp_ms DESC);

create table tsIntern
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

create index tsIntern_key on tsIntern (record_key);
create index tsIntern_timestamp_ms_idx on tsIntern (timestamp_ms DESC);
