create index status_status_system_time_ms_idx on status (status, system_time_ms);

create index pending_utbetalinger_uid_system_time_ms_idx
    on pending_utbetalinger (try_jsonb_get_text(record_value, 'uid'), system_time_ms);
