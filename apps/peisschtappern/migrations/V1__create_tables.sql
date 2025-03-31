create table oppdrag
(
    id             bigserial primary key,
    version        text,
    topic_name     text,
    key            text,
    value          text,
    partition      int,
    "offset"       bigint,
    timestamp_ms   bigint,
    stream_time_ms bigint,
    system_time_ms bigint
);

create index oppdrag_key on oppdrag (key);

