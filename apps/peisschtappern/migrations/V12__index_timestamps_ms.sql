create index status_timestamp_ms_idx on status (timestamp_ms DESC);
create index pending_utbetalinger_timestamp_ms_idx on pending_utbetalinger (timestamp_ms DESC);
create index fk_timestamp_ms_idx on fk (timestamp_ms DESC);
