-- Fixkosten koennen auch Einnahmen sein (z.B. THG-Quote, Untermiete Stellplatz):
-- negative Betraege erlauben und die Kategorie-Liste an das Java-Enum angleichen.
-- LEASING/FINANCING/TIRES/TUNING existierten bereits im Enum, fehlten aber im CHECK.

ALTER TABLE fixed_cost DROP CONSTRAINT IF EXISTS fixed_cost_amount_check;
ALTER TABLE fixed_cost DROP CONSTRAINT IF EXISTS fixed_cost_category_check;

ALTER TABLE fixed_cost
    ADD CONSTRAINT fixed_cost_category_check CHECK (category IN (
        'INSURANCE','TAX','TOLL','CLEANING','MAINTENANCE',
        'LEASING','FINANCING','TIRES','TUNING',
        'INCOME','COMPENSATION','OTHER'
    ));
