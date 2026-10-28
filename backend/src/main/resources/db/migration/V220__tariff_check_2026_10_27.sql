-- Einmalige Tarifprüfung vom 27.10.2026 gegen die Seiten der Anbieter, danach hält der Tarif-Crawler
-- (V219) die Daten aktuell. Belege: ~/private/app-ci/ladekarten/pruefung-2026-10-27.md (nicht im Repo).
-- Preisänderungen beenden die alte Zeile am 26.10. und legen eine neue ab 27.10. an, damit vergangene
-- Ladungen ihren damaligen Preis behalten. Korrekturen von Seed-Fehlern (falsches Feld, fehlender
-- Betreiber) ändern die Zeile direkt. Aktionspreise sind nicht erfasst.

CREATE TEMP TABLE check_day AS SELECT DATE '2026-10-27' AS today, TIMESTAMP '2026-10-27 12:00:00' AS checked_at;

-- Hilfsfunktion: neue Zeile ab dem Prüftag.
CREATE FUNCTION pg_temp.add_tariff(p_emp TEXT, p_variant TEXT, p_type TEXT, p_price NUMERIC, p_monthly NUMERIC,
                                   p_cpo TEXT, p_tier TEXT, p_elsewhere BOOLEAN, p_min_kw INTEGER,
                                   p_block_min NUMERIC, p_block_after INTEGER, p_block_cap NUMERIC, p_url TEXT)
RETURNS VOID LANGUAGE SQL AS $$
    INSERT INTO charging_provider_tariffs (id, emp_name, tariff_variant, cpo_name, price_tier, charging_type, price_per_kwh,
        session_fee_eur, monthly_fee_eur, blocking_fee_per_min, blocking_fee_after_min, is_dynamic_pricing, valid_from,
        valid_until, source_url, last_verified_at, applies_elsewhere, min_power_kw, blocking_fee_cap_eur)
    SELECT gen_random_uuid(), p_emp, p_variant, p_cpo, p_tier, p_type, p_price, 0, p_monthly, p_block_min, p_block_after,
           FALSE, today, NULL, p_url, checked_at, p_elsewhere, p_min_kw, p_block_cap
    FROM check_day;
$$;

-- Hilfsfunktion: laufende Zeile zum Vortag des Prüftags beenden.
CREATE FUNCTION pg_temp.end_tariff(p_emp TEXT, p_variant TEXT, p_type TEXT, p_tier TEXT)
RETURNS VOID LANGUAGE SQL AS $$
    UPDATE charging_provider_tariffs t SET valid_until = c.today - 1
    FROM check_day c
    WHERE t.emp_name = p_emp AND t.tariff_variant = p_variant AND t.charging_type = p_type
      AND t.price_tier IS NOT DISTINCT FROM p_tier AND t.valid_until IS NULL;
$$;

-- Hilfsfunktion: Zeile ist geprüft und stimmt, Quelle und Stand setzen.
CREATE FUNCTION pg_temp.verified(p_emp TEXT, p_url TEXT)
RETURNS VOID LANGUAGE SQL AS $$
    UPDATE charging_provider_tariffs t SET source_url = p_url, last_verified_at = c.checked_at
    FROM check_day c
    WHERE t.emp_name = p_emp AND t.valid_until IS NULL;
$$;

-- Aral pulse: Preise nach Leistung, im eigenen Netz keine Blockiergebühr. Fremdnetz DE: AC 0,59, DC 0,79.
SELECT pg_temp.end_tariff('Aral Pulse', v, t, NULL) FROM (VALUES ('Klassik'), ('Extra')) AS v(v), (VALUES ('AC'), ('DC')) AS t(t);
SELECT pg_temp.add_tariff('Aral Pulse', 'Klassik', 'AC', 0.47, 0, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, u),
       pg_temp.add_tariff('Aral Pulse', 'Klassik', 'DC', 0.52, 0, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, u),
       pg_temp.add_tariff('Aral Pulse', 'Klassik', 'DC', 0.62, 0, NULL, NULL, FALSE, 50, NULL, NULL, NULL, u),
       pg_temp.add_tariff('Aral Pulse', 'Extra', 'AC', 0.41, 2.99, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, u),
       pg_temp.add_tariff('Aral Pulse', 'Extra', 'DC', 0.46, 2.99, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, u),
       pg_temp.add_tariff('Aral Pulse', 'Extra', 'DC', 0.54, 2.99, NULL, NULL, FALSE, 50, NULL, NULL, NULL, u),
       pg_temp.add_tariff('Aral Pulse', 'Klassik', 'AC', 0.59, 0, NULL, NULL, TRUE, NULL, 0.15, 360, NULL, u),
       pg_temp.add_tariff('Aral Pulse', 'Klassik', 'DC', 0.79, 0, NULL, NULL, TRUE, NULL, 0.15, 45, NULL, u),
       pg_temp.add_tariff('Aral Pulse', 'Extra', 'AC', 0.59, 2.99, NULL, NULL, TRUE, NULL, 0.15, 360, NULL, u),
       pg_temp.add_tariff('Aral Pulse', 'Extra', 'DC', 0.79, 2.99, NULL, NULL, TRUE, NULL, 0.15, 45, NULL, u)
FROM (VALUES ('https://www.aral.de/de/global/retail/pulse/tarife-bezahlmethoden.html')) AS s(u);

-- ADAC e-Charge: 0,55 gilt an Aral pulse (Seed ohne Betreiber, galt nirgends), andere Betreiber 0,75.
UPDATE charging_provider_tariffs SET cpo_name = 'Aral Pulse', blocking_fee_per_min = NULL, blocking_fee_after_min = NULL
WHERE emp_name = 'ADAC e-Charge' AND valid_until IS NULL AND cpo_name IS NULL
  AND EXISTS (SELECT 1 FROM charging_networks WHERE name = 'Aral Pulse');
SELECT pg_temp.verified('ADAC e-Charge', 'https://www.adac.de/rund-ums-fahrzeug/e-angebote/ladekarte/');
SELECT pg_temp.add_tariff('ADAC e-Charge', 'Standard', 'AC', 0.75, 0, NULL, NULL, TRUE, NULL, 0.15, 120, NULL, u),
       pg_temp.add_tariff('ADAC e-Charge', 'Standard', 'DC', 0.75, 0, NULL, NULL, TRUE, NULL, 0.15, 45, NULL, u)
FROM (VALUES ('https://www.adac.de/rund-ums-fahrzeug/e-angebote/ladekarte/')) AS s(u);

-- EnBW: S/M/L stimmen (Stand 01.12.2025). Blockiergebühr höchstens 12 € je Ladung.
UPDATE charging_provider_tariffs SET blocking_fee_cap_eur = 12 WHERE emp_name = 'EnBW' AND valid_until IS NULL;
SELECT pg_temp.verified('EnBW', 'https://www.enbw.com/elektromobilitaet/produkte/ladetarife');

-- IONITY Deutschland: alle vier geändert, Go war im Seed falsch (0,39 statt 0,68).
SELECT pg_temp.end_tariff('IONITY', v, 'DC', NULL) FROM (VALUES ('Direct'), ('Go'), ('Motion'), ('Power')) AS v(v);
SELECT pg_temp.add_tariff('IONITY', 'Direct', 'DC', 0.72, 0, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, u),
       pg_temp.add_tariff('IONITY', 'Go', 'DC', 0.68, 0, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, u),
       pg_temp.add_tariff('IONITY', 'Motion', 'DC', 0.53, 5.99, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, u),
       pg_temp.add_tariff('IONITY', 'Power', 'DC', 0.42, 11.99, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, u)
FROM (VALUES ('https://www.ionity.eu/de/abonnements')) AS s(u);

-- Maingau (Neukundenpreis): AC in allen Stufen geändert, DC stimmt. Standardpreis gilt an allen nicht
-- gelisteten Betreibern. Standzeitzuschlag höchstens 12 €.
SELECT pg_temp.end_tariff('Maingau', 'Standard', 'AC', tier) FROM (VALUES ('LOW'), ('STANDARD'), ('HIGH')) AS v(tier);
SELECT pg_temp.add_tariff('Maingau', 'Standard', 'AC', 0.52, 0, NULL, 'LOW', FALSE, NULL, 0.10, 180, 12, u),
       pg_temp.add_tariff('Maingau', 'Standard', 'AC', 0.72, 0, NULL, 'STANDARD', TRUE, NULL, 0.10, 180, 12, u),
       pg_temp.add_tariff('Maingau', 'Standard', 'AC', 0.82, 0, NULL, 'HIGH', FALSE, NULL, 0.10, 180, 12, u)
FROM (VALUES ('https://www.maingau-energie.de/e-mobilitaet/autostrom')) AS s(u);
UPDATE charging_provider_tariffs SET blocking_fee_cap_eur = 12, applies_elsewhere = (price_tier = 'STANDARD')
WHERE emp_name = 'Maingau' AND charging_type = 'DC' AND valid_until IS NULL;
SELECT pg_temp.verified('Maingau', 'https://www.maingau-energie.de/e-mobilitaet/autostrom');

DELETE FROM cpo_emp_tier_mapping WHERE emp_name = 'Maingau';
INSERT INTO cpo_emp_tier_mapping (emp_name, cpo_name, price_tier)
SELECT 'Maingau', n.name, m.tier
FROM (VALUES ('IONITY', 'LOW'), ('EDEKA', 'LOW'), ('JET Strom', 'LOW'), ('Kaufland', 'LOW'), ('Lidl', 'LOW'),
             ('TankE', 'LOW'), ('Vattenfall InCharge', 'LOW'),
             ('Allego', 'HIGH'), ('E.ON Drive', 'HIGH'), ('EnBW', 'HIGH'), ('EWE Go', 'HIGH'), ('Fastned', 'HIGH'),
             ('LichtBlick', 'HIGH')) AS m(cpo, tier)
JOIN charging_networks n ON n.name = m.cpo;

-- Fastned Deutschland: kein App-Rabatt in DE, Gold 0,49 bei 5,99 €/Monat, Ad-hoc 0,69 stimmt.
SELECT pg_temp.end_tariff('Fastned', v, 'DC', NULL) FROM (VALUES ('App'), ('Gold')) AS v(v);
SELECT pg_temp.add_tariff('Fastned', 'Gold', 'DC', 0.49, 5.99, NULL, NULL, FALSE, NULL, NULL, NULL, NULL,
                          'https://www.fastnedcharging.com/de/laden/tarife');
SELECT pg_temp.verified('Fastned', 'https://www.fastnedcharging.com/de/laden/tarife');

-- Vattenfall InCharge: DC über 50 kW "0,44 oder 0,49" je Station, der höhere Wert. AC steht nicht auf der
-- Seite und bleibt ungeprüft (der Crawler meldet ihn als fehlend, der Admin entscheidet).
SELECT pg_temp.end_tariff('Vattenfall InCharge', 'Standard', 'DC', NULL);
SELECT pg_temp.add_tariff('Vattenfall InCharge', 'Standard', 'DC', 0.49, 0, NULL, NULL, FALSE, 50, NULL, NULL, NULL,
                          'https://incharge.vattenfall.de/fair-laden');

-- Smatrics (AT): 19,90 € ist die einmalige Freischaltgebühr, keine Gebühr je Ladung. Roaming fehlte.
UPDATE charging_provider_tariffs SET session_fee_eur = 0 WHERE emp_name = 'Smatrics' AND valid_until IS NULL;
SELECT pg_temp.verified('Smatrics', 'https://www.smatrics.com/de/laden/tarife');
SELECT pg_temp.add_tariff('Smatrics', 'Charge & Roam', 'AC', 0.79, 4.90, NULL, NULL, TRUE, NULL, NULL, NULL, NULL, u),
       pg_temp.add_tariff('Smatrics', 'Charge & Roam', 'DC', 0.89, 4.90, NULL, NULL, TRUE, NULL, NULL, NULL, NULL, u),
       pg_temp.add_tariff('Smatrics', 'Charge & Roam+', 'AC', 0.69, 8.90, NULL, NULL, TRUE, NULL, NULL, NULL, NULL, u),
       pg_temp.add_tariff('Smatrics', 'Charge & Roam+', 'DC', 0.79, 8.90, NULL, NULL, TRUE, NULL, NULL, NULL, NULL, u)
FROM (VALUES ('https://www.smatrics.com/de/laden/tarife')) AS s(u);

-- ÖAMTC ePower (AT): Partnernetz ist Roaming, ab 150 kW eigene Preisstufe.
UPDATE charging_provider_tariffs SET applies_elsewhere = TRUE
WHERE emp_name = 'ÖAMTC ePower' AND tariff_variant = 'Partnernetz' AND valid_until IS NULL;
SELECT pg_temp.verified('ÖAMTC ePower', 'https://www.oeamtc.at/oeamtc-epower/');
SELECT pg_temp.add_tariff('ÖAMTC ePower', 'Eigenes Netz', 'DC', 0.64, 0, NULL, NULL, FALSE, 149, 0.10, 120, NULL, u),
       pg_temp.add_tariff('ÖAMTC ePower', 'Partnernetz', 'DC', 0.74, 0, NULL, NULL, TRUE, 149, 0.10, 120, NULL, u)
FROM (VALUES ('https://www.oeamtc.at/oeamtc-epower/')) AS s(u);

UPDATE emp SET country_code = 'AT' WHERE name IN ('Smatrics', 'Wien Energie', 'ÖAMTC ePower');

-- Quellen des Crawlers. EnBW zeigt Preise nur als Bild: WATCH. Mer (Preise nur in der App) und
-- Wien Energie (nur PDF, Links liefern 404) haben keine lesbare Quelle.
INSERT INTO tariff_source (id, emp_name, url, mode)
SELECT gen_random_uuid(), s.emp, s.url, s.mode
FROM (VALUES
    ('Aral Pulse', 'https://www.aral.de/de/global/retail/pulse/tarife-bezahlmethoden.html', 'EXTRACT'),
    ('ADAC e-Charge', 'https://www.adac.de/rund-ums-fahrzeug/e-angebote/ladekarte/', 'EXTRACT'),
    ('EnBW', 'https://www.enbw.com/elektromobilitaet/produkte/ladetarife', 'WATCH'),
    ('IONITY', 'https://www.ionity.eu/de/abonnements', 'EXTRACT'),
    ('Maingau', 'https://www.maingau-energie.de/e-mobilitaet/autostrom', 'EXTRACT'),
    ('Fastned', 'https://www.fastnedcharging.com/de/laden/tarife', 'EXTRACT'),
    ('Vattenfall InCharge', 'https://incharge.vattenfall.de/fair-laden', 'EXTRACT'),
    ('Smatrics', 'https://www.smatrics.com/de/laden/tarife', 'EXTRACT'),
    ('ÖAMTC ePower', 'https://www.oeamtc.at/oeamtc-epower/', 'EXTRACT')
) AS s(emp, url, mode)
JOIN emp e ON e.name = s.emp;

DROP TABLE check_day;
