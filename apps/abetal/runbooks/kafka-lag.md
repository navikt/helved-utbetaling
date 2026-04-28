# Alert: kafka lag

## What it means
Abetal has accumulated Kafka consumer lag above threshold for at least 10
minutes. Messages are arriving faster than the app can process them or the
consumer is blocked.

## What to check
1. Check which topic label is lagging and correlate with logs, rebalance events, and processing errors.
2. Inspect state-store health, downstream slowness, and whether a recent deploy changed throughput or stability.

## How to mitigate
- Fix the blocking error or roll back the change causing slow processing.
- If lag is caused by external dependency degradation, coordinate mitigation and watch lag start draining before closing alert.

## Escalation
Contact: Team Helved / #team-helved
Severity: ticket
Related dashboards: Kafka consumer lag panels, abetal processing metrics, and application logs.
