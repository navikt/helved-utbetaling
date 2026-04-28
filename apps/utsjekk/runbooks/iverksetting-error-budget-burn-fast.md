# Alert: UtsjekkIverksettingErrorBudgetBurnFast

## What it means
Iverksetting error rate is burning more than 14.4x of 30-day error budget in both 1h and 6h windows. This is page-level because budget can disappear quickly if issue continues.

## What to check
1. Confirm current error ratio for `helved_utsjekk_iverksetting_total{result="error"}` versus total traffic over 1h and 6h, and check whether one caller, route variant, or deploy caused spike.
2. Check utsjekk logs, recent deploys, Kafka `helved.status.v1` flow, DB connectivity, and downstream dependencies used during iverksetting.

## How to mitigate
- Roll back latest utsjekk deploy or disable/change recent release if burn started after deploy.
- Reduce incoming failure mode by fixing dependency outage, restarting stuck pods, or routing around broken external path while keeping payment safety first.

## Escalation
Contact: Team Helved / #helved
Severity: page
Related dashboards: https://grafana.nav.cloud.nais.io/ and Prometheus for `helved_utsjekk_iverksetting_total`
