-- Heimlade-Ersparnis: zeigt, was das Laden daheim gegenueber oeffentlichem Laden spart.
--
-- Der Heimstrompreis bekommt bewusst kein eigenes Feld am Nutzer, sondern wird eine
-- Ladekarte mit is_home. Gruende:
--   - user_charging_providers ist die einzige Stelle, an der gepflegt wird, was Laden
--     kostet. 15 Nutzer haben ihren Heimstrom dort ohnehin schon angelegt ("Zuhause",
--     "PV-Ueberschuss", "Hausstrom").
--   - Zeitgueltigkeit gibt es dort bereits ueber active_from / active_until. Strompreise
--     aendern sich, ein einzelnes Feld am Nutzer koennte das nicht abbilden.
--
-- Die Investition gehoert dagegen an den Nutzer: eine Wallbox haengt am Haushalt, nicht
-- an einer Ladekarte und nicht am Auto.

ALTER TABLE user_charging_providers
    ADD COLUMN is_home BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN user_charging_providers.is_home IS
    'Diese Karte bildet den Heimstrom ab. Setzt der Nutzer selbst - aus den Logs ist es '
    'nicht ableitbar, weil is_public_charging vor V165/V166 unzuverlaessig war.';

-- Nur eine Heimstrom-Karte je Nutzer und Zeitraum. Ohne das waere unklar, welcher Preis
-- gilt, wenn zwei Karten als Heimstrom markiert sind.
CREATE UNIQUE INDEX uq_user_charging_providers_one_home
    ON user_charging_providers (user_id, active_from)
    WHERE is_home AND deleted_at IS NULL;

ALTER TABLE app_user
    ADD COLUMN home_investment_eur NUMERIC(10,2);

COMMENT ON COLUMN app_user.home_investment_eur IS
    'Wallbox samt Installation. Optional - ohne den Wert zeigt die Kachel nur die '
    'laufende Ersparnis und keine Amortisation.';
