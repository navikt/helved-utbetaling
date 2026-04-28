# Alert: payment error budget burn (slow)

## What it means
Abetal is consuming payment-processing error budget steadily across both the
24-hour and 3-day windows. This points to a persistent quality issue rather
than a short incident spike.

## What to check
1. Review deploy history, config changes, and long-running incidents during the last 3 days.
2. Compare successful and failed `helved_abetal_payment_processed_total` counts and check whether one source system or payment type dominates failures.

## How to mitigate
- Fix or revert the long-running source of failures and confirm the error ratio falls below the SLO threshold.
- Create or update incident/ticket with current burn-rate, suspected cause, and follow-up actions.

## Escalation
Contact: Team Helved / #team-helved
Severity: ticket
Related dashboards: Prometheus alert graph for `AbetalPaymentErrorBudgetBurnSlow`, Grafana trend panels for payment processed totals, and deploy timeline.
