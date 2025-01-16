ALTER TABLE utbetaling
    ALTER COLUMN data TYPE jsonb USING data::text::jsonb;

ALTER TABLE utbetaling_status 
    ALTER COLUMN status TYPE jsonb USING status::text::jsonb;

