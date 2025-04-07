ALTER DEFAULT PRIVILEGES IN SCHEMA PUBLIC GRANT ALL ON TABLES TO CLOUDSQLIAMUSER;

create table scheduled
(
    id          bigserial primary key,
    created_at  date not null,
    avstemt_fom date not null,
    avstemt_tom date not null
);

create index created_at_idx on scheduled (created_at);

