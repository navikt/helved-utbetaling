create table timer
(
    record_key text primary key,
    timeout    timestamp(3),
    sak_id     text,
    fagsystem  text
);