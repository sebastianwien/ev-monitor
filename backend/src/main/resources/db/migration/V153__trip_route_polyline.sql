-- Gerechnete Strassenverbindung zwischen Start- und Zielgegend einer Fahrt.
-- Das ist NICHT die gefahrene Strecke: gespeichert wird die Route, die ein Router
-- zwischen den beiden gerundeten Geohash-Mittelpunkten vorschlaegt.
ALTER TABLE ev_trip ADD COLUMN route_polyline TEXT;

-- Cache auf dem Geohash-Paar. Pendler fahren dieselbe Relation taeglich - ohne Cache
-- wuerde jede Fahrt denselben Router-Aufruf ausloesen.
CREATE TABLE route_sketch (
    start_geohash VARCHAR(12) NOT NULL,
    end_geohash   VARCHAR(12) NOT NULL,
    polyline      TEXT        NOT NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (start_geohash, end_geohash)
);
