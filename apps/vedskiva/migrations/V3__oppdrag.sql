create table oppdrag
(
    id               bigserial primary key,
    hash_key         int       unique       not null,
    nokkelAvstemming timestamp              not null,
    kodeFagomraade   text                   not null,
    personident      text                   not null,
    fagsystemId      text                   not null,
    lastDelytelseId  text                   not null,
    tidspktMelding   timestamp              not null,
    sats             bigint                 not null,
    createdAt        timestamp              not null,
    alvorlighetsgrad text,
    kodeMelding      text,
    beskrMelding     text
);

create index nokkelAvstemming_idx on oppdrag (nokkelAvstemming);
create index oppdrag_hash_key on oppdrag (hash_key);

