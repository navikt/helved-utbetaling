create table oppdrag
(
    id             bigserial primary key,
    version        text,
    key            text,
    value          text,
    partition      int,
    "offset"       bigint,
    timestamp_ms   bigint,
    stream_time_ms bigint,
    system_time_ms bigint
)