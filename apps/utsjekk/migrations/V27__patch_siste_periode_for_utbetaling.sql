-- Patch sistePeriode for a single legacy utbetaling row in prod.
-- This script is safe to run in all environments:
--   - In dev/test: WHERE clause matches 0 rows (id/sak_id won't exist)
--   - In prod: idempotent via "data->'sistePeriode' IS NULL" guard
UPDATE utbetaling
SET data = jsonb_set(
    data,
    '{sistePeriode}',
    '{
        "beløp": 311,
        "betalendeEnhet": null,
        "fastsattDagsats": 1829,
        "fom": [2026, 3, 23],
        "tom": [2026, 4, 3]
    }'::jsonb,
    true
)
WHERE sak_id = '4QiQ31C'
  AND id = 'bed28c76-254a-4a7e-ba0e-6c5673211c1b'
  AND data->'sistePeriode' IS NULL;
