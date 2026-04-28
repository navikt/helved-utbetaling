# Alert: oppdrag send latency p95

## What it means
P95 latency for sending oppdrag is above 30 seconds for at least 10 minutes.
Abetal is still working, but payment orders are moving too slowly through the
send path.

## What to check
1. Inspect `helved_abetal_oppdrag_send_seconds` together with Kafka lag and application logs for slow external dependency calls.
2. Check whether slowness started after deploy, scaling change, or issues in downstream MQ/oppdrag systems.

## How to mitigate
- If downstream send path is slow, coordinate with dependency owners and consider throttling or pausing new load.
- If change-induced, roll back recent deploy/config and verify p95 drops below threshold.

## Escalation
Contact: Team Helved / #team-helved
Severity: ticket
Related dashboards: Prometheus graph for `helved_abetal_oppdrag_send_seconds`, abetal logs, and downstream dependency dashboards.
