-- Firmierungen aus dem Ladesaeulenregister der Bundesnetzagentur auf unsere Ladenetze abbilden.
--
-- Das Register fuehrt den handelsrechtlichen Betreibernamen ("BP Europa SE"), wir fuehren
-- die Marke, unter der geladen wird ("Aral Pulse"). Wo sich die Marke nicht durch blosses
-- Entfernen der Rechtsform ergibt, braucht es diesen Eintrag.
--
-- Alias immer klein geschrieben und getrimmt - der Abgleich im Code normalisiert genauso.

CREATE TABLE charging_network_alias (
    alias        VARCHAR(150) PRIMARY KEY,
    network_name VARCHAR(100) NOT NULL REFERENCES charging_networks(name) ON DELETE CASCADE
);

CREATE INDEX idx_charging_network_alias_network ON charging_network_alias(network_name);

-- Stand des Registers: 07.2026. Abgeleitet aus den 45 haeufigsten DC-Betreibern,
-- die zusammen 23.821 der 30.628 Schnellladeeinrichtungen stellen.
INSERT INTO charging_network_alias (alias, network_name) VALUES
('enbw mobility+ ag und co.kg',                       'EnBW'),
('tesla germany gmbh',                                'Tesla Supercharger'),
('bp europa se',                                      'Aral Pulse'),
('shell deutschland gmbh',                            'Shell Recharge'),
('mer germany gmbh',                                  'Mer'),
('aldi süd immobilienverwaltungs-gmbh & co. ohg',     'ALDI Süd'),
('aldi nord deutschland stiftung & co. kg',           'ALDI Nord'),
('edeka-miha charge gmbh',                            'EDEKA'),
('jet tankstellen deutschland gmbh',                  'JET Strom'),
('vattenfall smarter living gmbh',                    'Vattenfall InCharge'),
('totalenergies charging solutions deutschland gmbh', 'TotalEnergies'),
('lidl dienstleistung gmbh & co. kg',                 'Lidl'),
('kaufland dienstleistung gmbh & co. kg',             'Kaufland'),
('e.on drive infrastructure gmbh',                    'E.ON Drive'),
('e.on drive infrastructure',                         'E.ON Drive'),
('ewe go hochtief ladepartner gmbh & co. kg',         'EWE Go'),
('fastned deutschland gmbh & co. kg',                 'Fastned');
