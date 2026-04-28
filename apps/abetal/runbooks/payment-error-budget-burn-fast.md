# Alert: payment error budget burn (fast)

## What it means
Abetal is spending payment-processing error budget too quickly across both the
1-hour and 6-hour windows. This usually means many payments are failing right
now and needs immediate investigation.

## What to check
1. Check recent application logs for spikes in `helved_abetal_processing_error_total` and failed payment-processing paths.
2. Inspect Prometheus/Grafana for `helved_abetal_payment_processed_total{result="error"}` split by result and correlate with oppdrag send and kvittering wait latency.

## How to mitigate
- Roll back or pause recent abetal changes if failures started after deploy.
- If failures come from downstream dependencies, reduce input pressure and coordinate with owning team while monitoring error rate recovery.

## Escalation
Contact: Team Helved / #team-helved
Severity: page
Related dashboards: Prometheus alert graph for `AbetalPaymentErrorBudgetBurnFast`, abetal application logs, and Grafana panels for payment processed totals.
