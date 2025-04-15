create index avstemming_timestamp_ms_idx on avstemming (timestamp_ms DESC); 
create index oppdrag_timestamp_ms_idx on oppdrag (timestamp_ms DESC);
create index oppdragsdata_timestamp_ms_idx on oppdragsdata (timestamp_ms DESC);
create index dryrun_aap_timestamp_ms_idx on dryrun_aap (timestamp_ms DESC);
create index dryrun_tp_timestamp_ms_idx on dryrun_tp (timestamp_ms DESC);
create index dryrun_ts_timestamp_ms_idx on dryrun_ts (timestamp_ms DESC);
create index dryrun_dp_timestamp_ms_idx on dryrun_dp (timestamp_ms DESC);
create index kvittering_timestamp_ms_idx on kvittering (timestamp_ms DESC);
create index simuleringer_timestamp_ms_idx on simuleringer (timestamp_ms DESC);
create index utbetalinger_timestamp_ms_idx on utbetalinger (timestamp_ms DESC);
create index saker_timestamp_ms_idx on saker (timestamp_ms DESC);
create index aap_timestamp_ms_idx on aap (timestamp_ms DESC);

