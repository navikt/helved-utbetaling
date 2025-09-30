create table tp
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

create index tp_key on tp (record_key);
create index tp_timestamp_ms_idx on tp (timestamp_ms DESC);

create table tpIntern
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

create index tpIntern_key on tpIntern (record_key);
create index tpIntern_timestamp_ms_idx on tpIntern (timestamp_ms DESC);
