DO $$
BEGIN
    -- Only run if category column is still an enum type (not yet migrated)
    IF EXISTS (
        SELECT 1 FROM pg_type t
        JOIN pg_attribute a ON a.atttypid = t.oid
        JOIN pg_class c ON c.oid = a.attrelid
        WHERE c.relname = 'fixed_cost' AND a.attname = 'category' AND t.typtype = 'e'
    ) THEN
        ALTER TABLE fixed_cost DROP CONSTRAINT IF EXISTS fixed_cost_one_time_has_date;
        ALTER TABLE fixed_cost DROP CONSTRAINT IF EXISTS fixed_cost_recurring_has_start;
        ALTER TABLE fixed_cost ALTER COLUMN category TYPE VARCHAR(50) USING category::text;
        ALTER TABLE fixed_cost ALTER COLUMN recurrence TYPE VARCHAR(50) USING recurrence::text;
        DROP TYPE IF EXISTS fixed_cost_category;
        DROP TYPE IF EXISTS fixed_cost_recurrence;
        ALTER TABLE fixed_cost
            ADD CONSTRAINT fixed_cost_category_check CHECK (category IN ('INSURANCE','TAX','TOLL','CLEANING','MAINTENANCE','OTHER')),
            ADD CONSTRAINT fixed_cost_recurrence_check CHECK (recurrence IN ('ONE_TIME','MONTHLY','QUARTERLY','YEARLY')),
            ADD CONSTRAINT fixed_cost_one_time_has_date CHECK (recurrence != 'ONE_TIME' OR date IS NOT NULL),
            ADD CONSTRAINT fixed_cost_recurring_has_start CHECK (recurrence = 'ONE_TIME' OR start_date IS NOT NULL);
    END IF;
END $$;
