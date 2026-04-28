# Alert: kvittering wait stuck

## What it means
P95 kvittering wait time is above 600 seconds for at least 15 minutes. Payments
are likely getting stuck waiting for acknowledgements and finalization is delayed.

## What to check
1. Inspect `helved_abetal_kvittering_wait_seconds` and Kafka lag to confirm backlog in the kvittering path.
2. Check application logs and downstream oppdrag/kvittering systems for missing acknowledgements, retries, or stuck state-store joins.

## How to mitigate
- If downstream acknowledgements are delayed, coordinate with dependency owners and monitor whether backlog starts draining.
- Restart or roll back abetal only if the issue is internal and state is safe to resume without losing correlation.

## Escalation
Contact: Team Helved / #team-helved
Severity: ticket
Related dashboards: Prometheus graph for `helved_abetal_kvittering_wait_seconds`, Kafka lag, and oppdrag/kvittering operational dashboards.
