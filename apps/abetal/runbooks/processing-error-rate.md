# Alert: processing error rate

## What it means
Abetal is emitting processing errors above 0.1 errors per second for at least
5 minutes. Something in intake, aggregation, or transformation is failing often
enough to impact payment flow.

## What to check
1. Inspect application logs around the first alert firing and identify dominant `kind` label values on `helved_abetal_processing_error_total`.
2. Verify whether failures line up with malformed input, Kafka/state-store issues, or downstream oppdrag/kvittering handling.

## How to mitigate
- Stop or reduce bad input if one producer/system is sending broken messages.
- Roll back recent changes or restart abetal only after cause is understood and repeated failures are unlikely.

## Escalation
Contact: Team Helved / #team-helved
Severity: page
Related dashboards: Prometheus graph for `helved_abetal_processing_error_total`, abetal logs, and Kafka consumer lag panels.
