create table korrigerte_feilet_utbetalinger
(
    topic_name    text   not null,
    record_key    text   not null,
    registered_at bigint not null,
    primary key (topic_name, record_key)
);
