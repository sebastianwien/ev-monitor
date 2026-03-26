-- Kanonische Liste bekannter Ladenetzwerke (CPOs).
-- Dient als einzige Quelle für den CPO-Dropdown im Frontend.
-- Muss mit cpo_emp_tier_mapping.cpo_name übereinstimmen.

CREATE TABLE charging_networks (
    name         VARCHAR(100) PRIMARY KEY,
    country_code VARCHAR(2)   NULL   -- NULL = international / DACH-übergreifend
);

INSERT INTO charging_networks (name, country_code) VALUES
-- International / DACH-übergreifend
('IONITY',              NULL),
('Fastned',             NULL),
('Allego',              NULL),
('Mer',                 NULL),
('ChargePoint',         NULL),
('Shell Recharge',      NULL),
('TotalEnergies',       NULL),
('Clever',              NULL),
('Greenway',            NULL),

-- Deutschland
('EnBW',                'DE'),
('Aral Pulse',          'DE'),
('Vattenfall InCharge', 'DE'),
('E.ON Drive',          'DE'),
('EWE Go',              'DE'),
('LichtBlick',          'DE'),
('JET Strom',           'DE'),
('EDEKA',               'DE'),
('REWE',                'DE'),
('Lidl',                'DE'),
('Kaufland',            'DE'),
('Avia',                'DE'),

-- Österreich
('Smatrics',            'AT'),
('Wien Energie',        'AT'),
('Verbund',             'AT'),
('ÖAMTC',              'AT'),
('OMV',                 'AT'),

-- Schweiz
('EWZ',                 'CH'),
('ewb',                 'CH'),

-- Tesla (eigenes Netz, kein freies Roaming)
('Tesla Supercharger',  NULL),

-- Tankstellen mit DC (D/A)
('Q8',                  NULL);
