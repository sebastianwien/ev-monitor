package com.evmonitor.domain;

import java.util.List;
import java.util.Optional;

public interface ChargingProviderTariffRepository {

    List<ChargingProviderTariff> findAllCurrentTariffs();

    List<ChargingProviderTariff> findCurrentTariffsByEmp(String empName);

    /**
     * Lookup-Reihenfolge für den Vergleichsrechner:
     * 1. Exakter Match: emp + cpo + type
     * 2. Tier-Match via cpo_emp_tier_mapping: emp + tier + type
     * 3. Fallback: emp + cpo IS NULL + type
     */
    Optional<ChargingProviderTariff> findBestTariff(String empName, String tariffVariant, String cpoName, ChargingType chargingType);

    /**
     * Liefert alle Tarif-Varianten (z.B. "Tariff L", "Gold") eines EMP,
     * dedupliziert nach emp_name + tariff_variant.
     */
    List<String> findDistinctTariffVariantsByEmp(String empName);

    /**
     * Liefert alle verfügbaren EMP-Namen (für Frontend-Dropdown).
     */
    List<String> findAllEmpNames();

    /**
     * Tier-Lookup: Welchen Tier hat ein CPO beim gegebenen EMP?
     * Gibt null zurück wenn kein spezifisches Mapping existiert (→ STANDARD als Fallback).
     */
    Optional<String> findTierForCpo(String empName, String cpoName);

    /**
     * Alle bekannten CPO-Namen: UNION aus cpo_emp_tier_mapping.cpo_name und
     * charging_provider_tariffs.emp_name (EMPs die auch eigene Netze betreiben).
     * Sortiert, dedupliziert.
     */
    List<String> findAllKnownCpoNames();
}
