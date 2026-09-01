-- is_public_charging wird dreiwertig: oeffentlich / daheim / unbekannt.
--
-- Ursache: die Spalte war NOT NULL DEFAULT false. Jedes Log ohne explizite Angabe
-- galt damit als Heimladung. Die Connectors schicken fuer AC-Ladungen bereits
-- bewusst NULL ("AC/Home stays neutral"), weil eine AC-Ladung genauso an einer
-- oeffentlichen Saeule stattfinden kann - der Core hat dieses Wissen an der
-- Persistenzgrenze auf false geplaettet.
--
-- Wirkung: ausschliesslich vorwaerts. Bestandsdaten behalten ihren Wert, weil
-- rueckwirkend nicht mehr feststellbar ist, ob eine AC-Ladung daheim war.
-- Neue Logs ohne Angabe stehen ab jetzt auf NULL statt falsch auf "daheim".
--
-- Lesende Seite: isPublicChargingConfirmed() / isHomeChargingConfirmed() im Domain-
-- Modell. Unbekannt zaehlt weder als oeffentlich noch als Heimladung; die Statistik
-- weist es als eigenen Topf aus.

ALTER TABLE ev_log ALTER COLUMN is_public_charging DROP NOT NULL;
ALTER TABLE ev_log ALTER COLUMN is_public_charging DROP DEFAULT;

COMMENT ON COLUMN ev_log.is_public_charging IS
    'TRUE = oeffentlich, FALSE = daheim, NULL = unbekannt. Vor V166 NOT NULL DEFAULT false.';
