-- Die 15 groessten Ladenetze, die im Ladesaeulenregister vorkommen, uns aber fehlten.
--
-- Ermittelt am 31.08.2026 durch Abgleich aller Betreibernamen des Registers
-- (Schnellladeeinrichtungen, nach Anzahl sortiert) gegen charging_networks.
-- Vorher wurden 65,4 % der Schnellladeeinrichtungen einem bekannten Netz zugeordnet.
--
-- Der Name ist der, den ein Fahrer an der Saeule liest (Feld "Anzeigename (Karte)"),
-- nicht die Firmierung. Zwei Faelle weichen ab:
--   Bilkraft GmbH      -> Saeule zeigt "Eviny"
--   Comfortcharge GmbH -> Saeule zeigt "Comfort Charge"
--
-- Hinweis: ohne Zeile in cpo_emp_tier_mapping fallen diese Netze in der Tarifaufloesung
-- auf das STANDARD-Tier zurueck, also auf dasselbe wie "kein CPO angegeben". Der Gewinn
-- liegt hier in der Datenerfassung, nicht im berechneten Preis. Tier-Mappings folgen separat.

INSERT INTO charging_networks (name, country_code) VALUES
('Pfalzwerke',          'DE'),   -- 951 Schnellladeeinrichtungen
('TEAG Mobil',          'DE'),   -- 398
('Circle K',            'DE'),   -- 330
('Citywatt',            'DE'),   -- 271
('Comfort Charge',      'DE'),   -- 234
('Eviny',               'DE'),   -- 209
('Energie Südbayern',   'DE'),   -- 163
('Mercedes-Benz HPC',   'DE'),   -- 128
('EAM',                 'DE'),   -- 128
('autostrom.plus',      'DE'),   -- 127
('SachsenEnergie',      'DE'),   -- 113
('Electra',             'DE'),   -- 112
('TankE',               'DE'),   -- 109
('enercity',            'DE'),   -- 101
('amperio',             'DE');   -- 101

-- Nur wo der Registername sich nicht durch Entfernen der Rechtsform ergibt.
INSERT INTO charging_network_alias (alias, network_name) VALUES
('circle k deutschland gmbh',                     'Circle K'),
('circle k deutschland',                          'Circle K'),
('comfortcharge gmbh',                            'Comfort Charge'),
('bilkraft gmbh',                                 'Eviny'),
('mercedes-benz high-power charging europe gmbh', 'Mercedes-Benz HPC'),
-- Schreibfehler im Register, Stand 07.2026
('merdedes-benz high power charging network',     'Mercedes-Benz HPC'),
('eam natur energie gmbh',                        'EAM'),
('autostrom plus gmbh',                           'autostrom.plus'),
('electra germany gmbh',                          'Electra'),
('amperio gmbh (deamp)',                          'amperio');
