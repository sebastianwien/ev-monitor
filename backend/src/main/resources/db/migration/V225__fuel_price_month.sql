-- Benzinpreis je Monat: der tägliche Tankerkönig-Abruf überschreibt die Zeile des laufenden Monats,
-- mit dem Monatswechsel bleibt der Wert vom letzten Abruf stehen. Monatsmail und Story-Rückblick
-- rechnen die Ersparnis eines Monats damit, statt mit dem Tagespreis beim Öffnen.
CREATE TABLE fuel_price_month (
    month            DATE          PRIMARY KEY,
    benzin_eur_per_l NUMERIC(5, 3) NOT NULL,
    updated_at       TIMESTAMP     NOT NULL
);
