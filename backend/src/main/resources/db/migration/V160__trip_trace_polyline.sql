-- Die tatsaechlich gefahrene Linie einer Fahrt, aus den Location-Beacons der Telemetrie.
-- Bewusst neben route_polyline und nicht darin: die eine Spalte ist gemessen, die andere
-- vom Router geraten. Wer beides in einem Feld ablegt, weiss spaeter nicht mehr, was er
-- anzeigt - und die Namensnennung von openrouteservice haengt genau an dieser Unterscheidung.
ALTER TABLE ev_trip ADD COLUMN trace_polyline TEXT;
