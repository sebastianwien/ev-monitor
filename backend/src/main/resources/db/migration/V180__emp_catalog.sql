-- Katalog der Ladekarten-Anbieter (EMP), symmetrisch zu charging_networks (DEV-8).
-- Bisher stand der EMP-Name als freier String in Tarifen, Tier-Mapping und Nutzerkarten.
-- Die Historie "welcher EMP an welchem CPO" bleibt ein Join ueber ev_log
-- (charging_provider_id -> user_charging_providers.emp_name, charging_site_id -> cpo_name).

CREATE TABLE emp (
    name         VARCHAR(100) PRIMARY KEY,
    country_code VARCHAR(2)   NULL   -- NULL = international / DACH-uebergreifend
);

INSERT INTO emp (name)
SELECT DISTINCT emp_name FROM charging_provider_tariffs
UNION
SELECT DISTINCT emp_name FROM cpo_emp_tier_mapping;

ALTER TABLE charging_provider_tariffs
    ADD CONSTRAINT fk_tariffs_emp_name
    FOREIGN KEY (emp_name) REFERENCES emp(name) ON UPDATE CASCADE;

ALTER TABLE cpo_emp_tier_mapping
    ADD CONSTRAINT fk_tier_mapping_emp_name
    FOREIGN KEY (emp_name) REFERENCES emp(name) ON UPDATE CASCADE;

-- Nutzerkarte: Katalogverweis, sofern der frei eingegebene Name einem EMP entspricht.
-- provider_name bleibt, was der Nutzer geschrieben hat.
ALTER TABLE user_charging_providers
    ADD COLUMN emp_name VARCHAR(100) NULL REFERENCES emp(name) ON UPDATE CASCADE;

UPDATE user_charging_providers p
   SET emp_name = e.name
  FROM emp e
 WHERE lower(trim(p.provider_name)) = lower(e.name);

CREATE INDEX idx_user_charging_providers_emp ON user_charging_providers(emp_name)
    WHERE emp_name IS NOT NULL;
