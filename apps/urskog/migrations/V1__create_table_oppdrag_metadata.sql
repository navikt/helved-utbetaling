-- Set default grants to IAM users (for new tables as well)
ALTER DEFAULT PRIVILEGES IN SCHEMA PUBLIC GRANT ALL ON TABLES TO CLOUDSQLIAMUSER;

create table oppdrag
(
    id            bigserial    primary key,
    hash_key      int   unique not null,
    kafka_key     text         not null,
    oppdrag       text         not null,
    sak_id        text         not null,
    behandling_id text         not null,
    uids          text         not null,
    sent          boolean      not null,
    sent_at       timestamp
);

create index oppdrag_hash_key on oppdrag (hash_key);

create table pending_utbetaling
(
    id            bigserial    primary key,
    hash_key      int          not null,
    uid           text         not null,
    mottatt       boolean      not null,
    mottatt_at    timestamp,

    constraint unique_pending_utbetaling unique (hash_key, uid)
);

create index pending_utbetaling_uid on pending_utbetaling (uid);
create index pending_utbetaling_hash_key on pending_utbetaling (hash_key);

