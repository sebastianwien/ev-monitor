-- SoC-Hub, auf dem ein geschaetzter SoH-Wert beruht.
--
-- Im Median-Fenster liegen bis zu fuenf Ladungen mit unterschiedlichen Huben. Gespeichert
-- wird der Hub genau der Ladung, die den gewichteten Median getragen hat - also der
-- Messung, aus der der angezeigte Wert stammt. Ein Bereich ueber alle Fenster-Ladungen
-- waere unschaerfer und wuerde die konkrete Zahl nicht erklaeren.
--
-- Nur fuer CHARGE_LOG definiert. BMS-Werte brauchen keinen Hub (ein einzelner Messpunkt
-- genuegt dort), manuelle Eintraege haben keinen.

ALTER TABLE car_battery_soh_log
    ADD COLUMN soc_hub_percent NUMERIC(5,2);

-- NULL bleibt auch fuer CHARGE_LOG zulaessig: Eintraege, die zwischen V147 und dieser
-- Migration entstanden sind, kennen ihren Hub nicht mehr.
ALTER TABLE car_battery_soh_log
    ADD CONSTRAINT chk_car_battery_soh_log_soc_hub
    CHECK (soc_hub_percent IS NULL OR (source = 'CHARGE_LOG' AND soc_hub_percent > 0));

COMMENT ON COLUMN car_battery_soh_log.soc_hub_percent IS
    'SoC-Hub der Ladung, die den gewichteten Median getragen hat; nur bei source = CHARGE_LOG';
