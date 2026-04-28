# Alert: UtsjekkIverksettingLatencyP95High

## What it means
p95 latency for iverksetting is above 5 seconds for at least 10 minutes. Most requests may still succeed, but user experience and throughput are degrading.

## What to check
1. Check `histogram_quantile(0.95, sum(rate(helved_utsjekk_iverksetting_seconds_bucket[10m])) by (le))` and correlate with pod CPU, DB latency, Kafka pressure, and external dependency latency.
2. Inspect slow-request logs and verify whether latency comes from startup validation, DB calls, status waits, or downstream service time.

## How to mitigate
- Roll back recent change if latency jump matches deploy or configuration change.
- Reduce expensive path work, scale pods if resource saturation is real, or mitigate slow dependency causing queueing.

## Escalation
Contact: Team Helved / #helved
Severity: ticket
Related dashboards: https://grafana.nav.cloud.nais.io/ and Prometheus for `helved_utsjekk_iverksetting_seconds_bucket`
