create table iverksett
(
    behandling_id uuid not null primary key,
    data          json,
    ekstern_id    BIGINT not null
);

create table brev
(
    behandling_id  uuid references iverksett (behandling_id),
    pdf            bytea
);

