create table historisk
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

create index historisk_key on historisk (record_key);
create index historisk_timestamp_ms_idx on historisk (timestamp_ms DESC);

create table historiskIntern
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

create index historiskIntern_key on historiskIntern (record_key);
create index historiskIntern_timestamp_ms_idx on historiskIntern (timestamp_ms DESC);