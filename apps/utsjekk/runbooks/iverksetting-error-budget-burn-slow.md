# Alert: UtsjekkIverksettingErrorBudgetBurnSlow

## What it means
Iverksetting error rate is above full 30-day budget in both 24h and 3d windows. This is slower burn than page alert, but it still predicts SLO miss if left alone.

## What to check
1. Review long-window error ratio and split by time, deploy, and failure pattern to see whether burn comes from chronic low-grade errors or repeated incidents.
2. Check error logs, failed iverksetting outcomes, retry behavior, and whether one dependency or one producer dominates failures.

## How to mitigate
- Plan and ship fix during normal hours if no active incident, but prioritize before budget is exhausted.
- Tighten retries, timeout handling, or dependency safeguards if failures are low-grade but persistent.

## Escalation
Contact: Team Helved / #helved
Severity: ticket
Related dashboards: https://grafana.nav.cloud.nais.io/ and Prometheus for `helved_utsjekk_iverksetting_total`
