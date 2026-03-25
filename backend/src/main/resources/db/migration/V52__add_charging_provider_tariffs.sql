-- Phase 2: Charging Provider Tariff Comparison
-- Tables: charging_provider_tariffs, cpo_emp_tier_mapping

CREATE TABLE charging_provider_tariffs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    emp_name                VARCHAR(100) NOT NULL,
    tariff_variant          VARCHAR(100) NULL,          -- z.B. "Gold", "Tariff L", "Standard" - NULL wenn es nur eine Variante gibt
    cpo_name                VARCHAR(100) NULL,          -- NULL = gilt für alle CPOs (Fallback)
    price_tier              VARCHAR(20) NULL,           -- LOW | STANDARD | HIGH | NULL (wenn kein Tier-basiertes Modell)
    charging_type           VARCHAR(10) NOT NULL,       -- AC | DC
    price_per_kwh           NUMERIC(6,4) NOT NULL,
    session_fee_eur         NUMERIC(6,4) NOT NULL DEFAULT 0,
    monthly_fee_eur         NUMERIC(8,2) NOT NULL DEFAULT 0,
    blocking_fee_per_min    NUMERIC(6,4) NULL,
    blocking_fee_after_min  INTEGER NULL,
    is_dynamic_pricing      BOOLEAN NOT NULL DEFAULT false,
    valid_from              DATE NOT NULL,
    valid_until             DATE NULL,
    source_url              VARCHAR(500) NULL,
    last_verified_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tariffs_emp_cpo_type ON charging_provider_tariffs (emp_name, cpo_name, charging_type);
CREATE INDEX idx_tariffs_emp_tier_type ON charging_provider_tariffs (emp_name, price_tier, charging_type);

-- CPO-zu-Tier Mapping für EMPs mit gestaffelten Roaming-Preisen
CREATE TABLE cpo_emp_tier_mapping (
    emp_name    VARCHAR(100) NOT NULL,
    cpo_name    VARCHAR(100) NOT NULL,
    price_tier  VARCHAR(20) NOT NULL,   -- LOW | STANDARD | HIGH
    PRIMARY KEY (emp_name, cpo_name)
);

-- ============================================================
-- SEED DATA: Stand 25.03.2026
-- ============================================================

-- IONITY (nur DC, eigenes Netz - kein Roaming-Problem)
INSERT INTO charging_provider_tariffs (emp_name, tariff_variant, charging_type, price_per_kwh, monthly_fee_eur, valid_from, source_url) VALUES
('IONITY', 'Go',     'DC', 0.3900, 0.00,  '2025-01-01', 'https://ionity.eu/de/abonnements'),
('IONITY', 'Direct', 'DC', 0.6900, 0.00,  '2025-01-01', 'https://ionity.eu/de/abonnements'),
('IONITY', 'Motion', 'DC', 0.4900, 5.99,  '2025-01-01', 'https://ionity.eu/de/abonnements'),
('IONITY', 'Power',  'DC', 0.3900, 11.99, '2025-01-01', 'https://ionity.eu/de/abonnements');

-- EnBW mobility+ (eigenes Netz; Roaming teurer aber unklar -> nicht modelliert)
INSERT INTO charging_provider_tariffs (emp_name, tariff_variant, charging_type, price_per_kwh, monthly_fee_eur, blocking_fee_per_min, blocking_fee_after_min, valid_from, source_url) VALUES
('EnBW', 'Tariff S', 'AC', 0.5600, 0.00,  0.10, 240, '2025-12-01', 'https://www.enbw.com/elektromobilitaet/produkte/ladetarife'),
('EnBW', 'Tariff S', 'DC', 0.5600, 0.00,  0.10, 240, '2025-12-01', 'https://www.enbw.com/elektromobilitaet/produkte/ladetarife'),
('EnBW', 'Tariff M', 'AC', 0.4600, 5.99,  0.10, 240, '2025-12-01', 'https://www.enbw.com/elektromobilitaet/produkte/ladetarife'),
('EnBW', 'Tariff M', 'DC', 0.4600, 5.99,  0.10, 240, '2025-12-01', 'https://www.enbw.com/elektromobilitaet/produkte/ladetarife'),
('EnBW', 'Tariff L', 'AC', 0.3900, 11.99, 0.10, 240, '2025-12-01', 'https://www.enbw.com/elektromobilitaet/produkte/ladetarife'),
('EnBW', 'Tariff L', 'DC', 0.3900, 11.99, 0.10, 240, '2025-12-01', 'https://www.enbw.com/elektromobilitaet/produkte/ladetarife');

-- Maingau einstrom (Tier-basiert, Stand 13.08.2025)
INSERT INTO charging_provider_tariffs (emp_name, tariff_variant, price_tier, charging_type, price_per_kwh, monthly_fee_eur, blocking_fee_per_min, blocking_fee_after_min, valid_from, source_url) VALUES
('Maingau', 'Standard', 'LOW',      'AC', 0.6200, 0.00, 0.10, 180, '2025-08-13', 'https://www.electrive.net/2025/07/28/maingau-kuendigt-neue-ladetarife-an/'),
('Maingau', 'Standard', 'LOW',      'DC', 0.6200, 0.00, 0.10,  60, '2025-08-13', 'https://www.electrive.net/2025/07/28/maingau-kuendigt-neue-ladetarife-an/'),
('Maingau', 'Standard', 'STANDARD', 'AC', 0.6200, 0.00, 0.10, 180, '2025-08-13', 'https://www.electrive.net/2025/07/28/maingau-kuendigt-neue-ladetarife-an/'),
('Maingau', 'Standard', 'STANDARD', 'DC', 0.7200, 0.00, 0.10,  60, '2025-08-13', 'https://www.electrive.net/2025/07/28/maingau-kuendigt-neue-ladetarife-an/'),
('Maingau', 'Standard', 'HIGH',     'AC', 0.6200, 0.00, 0.10, 180, '2025-08-13', 'https://www.electrive.net/2025/07/28/maingau-kuendigt-neue-ladetarife-an/'),
('Maingau', 'Standard', 'HIGH',     'DC', 0.8200, 0.00, 0.10,  60, '2025-08-13', 'https://www.electrive.net/2025/07/28/maingau-kuendigt-neue-ladetarife-an/');

-- Maingau Tier-Mapping
INSERT INTO cpo_emp_tier_mapping (emp_name, cpo_name, price_tier) VALUES
('Maingau', 'IONITY',         'LOW'),
('Maingau', 'EDEKA',          'LOW'),
('Maingau', 'EWE Go',         'LOW'),
('Maingau', 'JET Strom',      'LOW'),
('Maingau', 'Allego',         'STANDARD'),
('Maingau', 'Mer',            'STANDARD'),
('Maingau', 'Aral Pulse',     'HIGH'),
('Maingau', 'E.ON Drive',     'HIGH'),
('Maingau', 'EnBW',           'HIGH'),
('Maingau', 'LichtBlick',     'HIGH');

-- Fastned (nur DC, eigenes Netz)
INSERT INTO charging_provider_tariffs (emp_name, tariff_variant, charging_type, price_per_kwh, monthly_fee_eur, valid_from, source_url) VALUES
('Fastned', 'Ad-hoc',   'DC', 0.6900, 0.00,  '2026-04-01', 'https://www.electrive.net/2026/03/24/fastned-senkt-ad-hoc-ladepreis-in-deutschland-auf-69-cent-kwh/'),
('Fastned', 'App',      'DC', 0.6200, 0.00,  '2026-01-01', 'https://fastnedcharging.com/en/charging/tariffs'),
('Fastned', 'Gold',     'DC', 0.4800, 11.99, '2026-04-01', 'https://fastnedcharging.com/en/charging/tariffs');

-- ARAL pulse
INSERT INTO charging_provider_tariffs (emp_name, tariff_variant, charging_type, price_per_kwh, monthly_fee_eur, blocking_fee_per_min, blocking_fee_after_min, valid_from, source_url) VALUES
('Aral Pulse', 'Klassik', 'AC', 0.5400, 0.00, 0.15, 120, '2025-01-01', 'https://www.aral.de/de/global/retail/pulse/tarife-bezahlmethoden.html'),
('Aral Pulse', 'Klassik', 'DC', 0.6900, 0.00, 0.15,  45, '2025-01-01', 'https://www.aral.de/de/global/retail/pulse/tarife-bezahlmethoden.html'),
('Aral Pulse', 'Extra',   'AC', 0.5400, 2.99, 0.15, 120, '2025-01-01', 'https://www.aral.de/de/global/retail/pulse/tarife-bezahlmethoden.html'),
('Aral Pulse', 'Extra',   'DC', 0.5400, 2.99, 0.15,  45, '2025-01-01', 'https://www.aral.de/de/global/retail/pulse/tarife-bezahlmethoden.html');

-- ADAC e-Charge (Kooperation mit Aral - AC und DC gleicher Preis)
INSERT INTO charging_provider_tariffs (emp_name, tariff_variant, charging_type, price_per_kwh, monthly_fee_eur, valid_from, source_url) VALUES
('ADAC e-Charge', 'Standard', 'AC', 0.5500, 0.00, '2025-08-01', 'https://www.adac.de/rund-ums-fahrzeug/elektromobilitaet/laden/adac-e-drive/'),
('ADAC e-Charge', 'Standard', 'DC', 0.5500, 0.00, '2025-08-01', 'https://www.adac.de/rund-ums-fahrzeug/elektromobilitaet/laden/adac-e-drive/');

-- MER (Standard DC bis 50 kW, HPC ab 50 kW)
INSERT INTO charging_provider_tariffs (emp_name, tariff_variant, charging_type, price_per_kwh, monthly_fee_eur, blocking_fee_per_min, blocking_fee_after_min, valid_from, source_url) VALUES
('Mer', 'Standard', 'AC', 0.4800, 0.00, 0.10, 120, '2026-01-01', 'https://de.mer.eco/mer/faq/wie-viel-kostet-das-laden-an-mer-ladestationen/'),
('Mer', 'Standard', 'DC', 0.5900, 0.00, 0.10, 120, '2026-01-01', 'https://de.mer.eco/mer/faq/wie-viel-kostet-das-laden-an-mer-ladestationen/'),
('Mer', 'HPC',      'DC', 0.6900, 0.00, 0.10, 120, '2026-01-01', 'https://de.mer.eco/mer/faq/wie-viel-kostet-das-laden-an-mer-ladestationen/');

-- Vattenfall InCharge (gültig ab 23.03.2026)
INSERT INTO charging_provider_tariffs (emp_name, tariff_variant, charging_type, price_per_kwh, monthly_fee_eur, valid_from, source_url) VALUES
('Vattenfall InCharge', 'Standard', 'AC', 0.4700, 0.00, '2026-03-23', 'https://incharge.vattenfall.de/fair-laden'),
('Vattenfall InCharge', 'Standard', 'DC', 0.4400, 0.00, '2026-03-23', 'https://incharge.vattenfall.de/fair-laden');

-- ÖAMTC ePower (Österreich)
INSERT INTO charging_provider_tariffs (emp_name, tariff_variant, charging_type, price_per_kwh, monthly_fee_eur, blocking_fee_per_min, blocking_fee_after_min, valid_from, source_url) VALUES
('ÖAMTC ePower', 'Eigenes Netz', 'AC', 0.4400, 0.00, 0.10, 240, '2026-01-01', 'https://www.oeamtc.at/oeamtc-epower/'),
('ÖAMTC ePower', 'Eigenes Netz', 'DC', 0.5400, 0.00, 0.10, 120, '2026-01-01', 'https://www.oeamtc.at/oeamtc-epower/'),
('ÖAMTC ePower', 'Partnernetz',  'AC', 0.5400, 0.00, 0.10, 240, '2026-01-01', 'https://www.oeamtc.at/oeamtc-epower/'),
('ÖAMTC ePower', 'Partnernetz',  'DC', 0.6400, 0.00, 0.10, 120, '2026-01-01', 'https://www.oeamtc.at/oeamtc-epower/');

-- Smatrics (Österreich)
INSERT INTO charging_provider_tariffs (emp_name, tariff_variant, charging_type, price_per_kwh, monthly_fee_eur, session_fee_eur, blocking_fee_per_min, blocking_fee_after_min, valid_from, source_url) VALUES
('Smatrics', 'Charge Simple',  'AC', 0.5500, 0.00, 19.90, 0.10, 481, '2026-01-01', 'https://www.smatrics.com/tarife'),
('Smatrics', 'Charge Simple',  'DC', 0.6500, 0.00, 19.90, 0.10, 181, '2026-01-01', 'https://www.smatrics.com/tarife'),
('Smatrics', 'Charge & Roam',  'AC', 0.5500, 4.90, 19.90, 0.10, 481, '2026-01-01', 'https://www.smatrics.com/tarife'),
('Smatrics', 'Charge & Roam',  'DC', 0.6500, 4.90, 19.90, 0.10, 181, '2026-01-01', 'https://www.smatrics.com/tarife'),
('Smatrics', 'Charge & Roam+', 'AC', 0.5000, 8.90, 19.90, 0.10, 481, '2026-01-01', 'https://www.smatrics.com/tarife'),
('Smatrics', 'Charge & Roam+', 'DC', 0.5500, 8.90, 19.90, 0.10, 181, '2026-01-01', 'https://www.smatrics.com/tarife');

-- Wien Energie (Österreich)
INSERT INTO charging_provider_tariffs (emp_name, tariff_variant, charging_type, price_per_kwh, monthly_fee_eur, valid_from, source_url) VALUES
('Wien Energie', 'Direct', 'AC', 0.5600, 0.00, '2026-01-01', 'https://www.wienenergie.at/produkte/elektromobilitaet/laden-unterwegs'),
('Wien Energie', 'Direct', 'DC', 0.5600, 0.00, '2026-01-01', 'https://www.wienenergie.at/produkte/elektromobilitaet/laden-unterwegs');
