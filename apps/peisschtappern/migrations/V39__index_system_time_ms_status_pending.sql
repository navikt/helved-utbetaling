create index if not exists status_system_time_ms_idx on status (system_time_ms DESC);
create index if not exists pending_utbetalinger_system_time_ms_idx on pending_utbetalinger (system_time_ms DESC);
