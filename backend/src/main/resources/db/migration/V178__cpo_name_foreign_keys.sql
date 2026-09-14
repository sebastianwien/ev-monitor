-- charging_networks ist der CPO-Katalog (V54). Tarife und Tier-Mapping fuehren den
-- Betreibernamen bisher als freien String - ab hier muss er im Katalog stehen.
-- ON UPDATE CASCADE: eine Umbenennung im Katalog zieht die Referenzen nach.
-- ev_log.cpo_name bleibt bewusst frei (Nutzereingabe, "Andere Ladestation").

ALTER TABLE charging_provider_tariffs
    ADD CONSTRAINT fk_tariffs_cpo_name
    FOREIGN KEY (cpo_name) REFERENCES charging_networks(name) ON UPDATE CASCADE;

ALTER TABLE cpo_emp_tier_mapping
    ADD CONSTRAINT fk_tier_mapping_cpo_name
    FOREIGN KEY (cpo_name) REFERENCES charging_networks(name) ON UPDATE CASCADE;

CREATE INDEX idx_tier_mapping_cpo_name ON cpo_emp_tier_mapping(cpo_name);
