# Alert: unavailable

## What it means
Abetal has had zero available replicas for at least 5 minutes. Payment
processing is unavailable until pod health is restored.

## What to check
1. Run `kubectl describe pod -l app=abetal -n helved` and inspect recent restarts, readiness, and crash reasons.
2. Check current logs and recent deploy history to see whether startup validation, Kafka, or config changes broke the pod.

## How to mitigate
- Roll back the latest deploy or restore broken config if availability dropped after a change.
- If pod is stuck on an external dependency, coordinate with owner and monitor readiness before declaring recovery.

## Escalation
Contact: Team Helved / #team-helved
Severity: page
Related dashboards: NAIS pod status for abetal, application logs, and deploy timeline.
