# Alert: UtsjekkStatusConsumerLagHigh

## What it means
utsjekk has not consumed status updates for more than 10 minutes. Iverksetting state can stay stale even if downstream processing continues.

## What to check
1. Check current value of `helved_utsjekk_status_consumer_lag_seconds` and verify whether `helved.status.v1` traffic is flowing while utsjekk lag keeps increasing.
2. Inspect utsjekk pod health, Kafka consumer errors, rebalance loops, and any blocked processing around status handling.

## How to mitigate
- Restart stuck utsjekk pod if consumer thread is wedged and no safer targeted fix exists.
- Fix broker connectivity, auth, or deserialization problems before scaling traffic back to normal.

## Escalation
Contact: Team Helved / #helved
Severity: page
Related dashboards: https://grafana.nav.cloud.nais.io/ and Prometheus for `helved_utsjekk_status_consumer_lag_seconds`
