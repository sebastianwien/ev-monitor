-- Ladekarten heißen in der App anders als im Katalog ("EnBW mobility+" statt "EnBW"). Ohne Verweis
-- zählt eine Nutzerkarte nicht für "von N Fahrern hier bezahlt" im Preisvergleich (lokal 27.10.2026:
-- 0 von 83 "EnBW mobility+", 0 von 22 "IONITY Passport"). Alias wie bei den Ladenetzen (V170).
-- Nur Expand: neue Tabelle, neue Katalogeinträge, Verweise auf vorhandene Nutzerkarten gesetzt.

CREATE TABLE emp_alias (
    alias    VARCHAR(100) PRIMARY KEY,      -- klein, ohne Rand-Leerraum
    emp_name VARCHAR(100) NOT NULL REFERENCES emp(name) ON UPDATE CASCADE ON DELETE CASCADE
);

-- Häufige Kartenanbieter ohne gepflegten Tarif, damit Fahrer-Preise für sie erscheinen können.
INSERT INTO emp (name) VALUES
    ('EWE Go'), ('Tesla'), ('Shell Recharge'), ('Electra'), ('Elli'), ('Plugsurfing'), ('E.ON Drive'),
    ('Lidl'), ('Kaufland'), ('Octopus Electroverse'), ('Mercedes me Charge'), ('Lichtblick')
ON CONFLICT (name) DO NOTHING;

-- Namen aus der Anbieterliste der App (KNOWN_EMPS) und häufige freie Eingaben.
INSERT INTO emp_alias (alias, emp_name)
SELECT a.alias, a.emp
FROM (VALUES
    ('enbw mobility+', 'EnBW'), ('enbw mobility', 'EnBW'),
    ('ionity passport', 'IONITY'),
    ('maingau energie', 'Maingau'), ('maingau autostrom', 'Maingau'),
    ('vattenfall', 'Vattenfall InCharge'),
    ('smatrics enbw (at)', 'Smatrics'), ('smatrics enbw', 'Smatrics'),
    ('bp pulse', 'Aral Pulse'),
    ('fastned gold', 'Fastned'),
    ('elli (vw)', 'Elli'),
    ('newmotion', 'Shell Recharge'),
    ('electroverse', 'Octopus Electroverse'), ('octopus', 'Octopus Electroverse'),
    ('tanke wien kwh', 'Wien Energie'), ('wien energie tanke', 'Wien Energie')
) AS a(alias, emp)
JOIN emp e ON e.name = a.emp;

-- Bestehende Nutzerkarten: erst Katalogname, dann Alias. provider_name bleibt, wie der Nutzer ihn schrieb.
UPDATE user_charging_providers p
   SET emp_name = e.name
  FROM emp e
 WHERE p.emp_name IS NULL
   AND lower(trim(p.provider_name)) = lower(e.name);

UPDATE user_charging_providers p
   SET emp_name = a.emp_name
  FROM emp_alias a
 WHERE p.emp_name IS NULL
   AND lower(trim(p.provider_name)) = a.alias;
