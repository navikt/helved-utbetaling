create table oppdragsdata
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
    system_time_ms   bigint
);

create index oppdragsdata_key on oppdragsdata (record_key);
