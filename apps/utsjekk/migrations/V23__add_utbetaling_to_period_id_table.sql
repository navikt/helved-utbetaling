CREATE TABLE utbetaling_to_period_id
(
    utbetaling_id UUID PRIMARY KEY REFERENCES utbetaling (utbetaling_id),
    period_id     INT
)