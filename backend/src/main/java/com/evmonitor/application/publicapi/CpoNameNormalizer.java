package com.evmonitor.application.publicapi;

import com.evmonitor.domain.ChargingProviderTariffRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Normalizes CPO (Charge Point Operator) names to canonical form.
 * The canonical list is loaded from the DB at startup (UNION of cpo_emp_tier_mapping and charging_provider_tariffs).
 * Case-insensitive match; unknown names are kept as-is.
 */
@Service
public class CpoNameNormalizer {

    private final ChargingProviderTariffRepository tariffRepository;

    private List<String> knownCpos = List.of();
    private Map<String, String> lookup = Map.of();

    public CpoNameNormalizer(ChargingProviderTariffRepository tariffRepository) {
        this.tariffRepository = tariffRepository;
    }

    @PostConstruct
    void init() {
        List<String> cpos = tariffRepository.findAllKnownCpoNames();
        this.knownCpos = Collections.unmodifiableList(cpos);
        this.lookup = cpos.stream().collect(Collectors.toMap(String::toLowerCase, s -> s));
    }

    public List<String> getKnownCpos() {
        return knownCpos;
    }

    /**
     * Returns the canonical form if the input matches a known CPO (case-insensitive),
     * otherwise returns the trimmed input as-is. Returns null for blank input.
     */
    public String normalize(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();
        String canonical = lookup.get(trimmed.toLowerCase());
        return canonical != null ? canonical : trimmed;
    }
}
