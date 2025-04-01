-- Set default grants to IAM users (for new tables as well)
ALTER DEFAULT PRIVILEGES IN SCHEMA PUBLIC GRANT ALL ON TABLES TO CLOUDSQLIAMUSER;

create table oppdrag
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

create index oppdrag_key on oppdrag (record_key);

create table kvittering
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

create index kvittering_key on kvittering (record_key);
