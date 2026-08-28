-- Woher die Linie in route_polyline stammt. Ohne diese Angabe bedeutet dieselbe Spalte
-- zweierlei: den geratenen Weg zwischen Start- und Zielgegend, oder die entlang der
-- gefahrenen Stuetzpunkte gerechnete Strassenfuehrung. Das eine ist eine Skizze, das andere
-- eine Naeherung der echten Strecke - die Karte zeichnet sie unterschiedlich.
--
--   SKETCH  - Router zwischen zwei Geohash-Mittelpunkten (Start -> Ziel)
--   MATCHED - Router entlang der Stuetzpunkte der gefahrenen Spur
ALTER TABLE ev_trip ADD COLUMN route_kind VARCHAR(10);

-- Alles, was es bisher gibt, ist eine Skizze: Routen entlang einer Spur gibt es erst ab hier.
UPDATE ev_trip SET route_kind = 'SKETCH' WHERE route_polyline IS NOT NULL;
