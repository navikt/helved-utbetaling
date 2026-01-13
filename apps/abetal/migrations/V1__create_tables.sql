-- Set default grants to IAM users (for new tables as well)
ALTER DEFAULT PRIVILEGES IN SCHEMA PUBLIC GRANT ALL ON TABLES TO CLOUDSQLIAMUSER;

create table fks
(
    id               bigserial  primary key,
    hash_key         int               not null,
    uids             text              not null
);

create index fks_hash_key on fks (hash_key);

