-- Nimmt is_home zurueck. Die Ladekarte war als Fallback fuer Nutzer ohne bepreiste
-- Heimladungen gedacht - der Fallback selbst bleibt noetig, die Karte dafuer nicht.
--
-- Der Heimpreis kommt jetzt als gewichteter Durchschnitt direkt aus den Logs
-- (Summe Kosten / Summe kWh). Auf Prod haben 176 von 218 Heimladern mindestens eine
-- bepreiste Heimladung, ein einziges Log genuegt bereits. Fuer die verbleibenden 42
-- ist der Leerzustand der Kachel die ehrlichere Antwort als eine zweite Stelle, an der
-- ein Strompreis gepflegt werden will.
--
-- Der Unique-Index war ausserdem falsch: er schluesselte auf (user_id, active_from) und
-- verhinderte damit nur zwei Heimkarten mit demselben Startdatum, nicht zwei
-- gleichzeitig gueltige. Bei gleichem Datum lief er in eine 500 statt in eine Meldung.
--
-- home_investment_eur bleibt - die Wallbox-Investition traegt weiterhin die
-- Amortisationsschiene.

DROP INDEX IF EXISTS uq_user_charging_providers_one_home;

ALTER TABLE user_charging_providers DROP COLUMN IF EXISTS is_home;
