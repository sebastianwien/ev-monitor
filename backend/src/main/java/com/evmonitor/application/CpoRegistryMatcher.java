package com.evmonitor.application;

import com.evmonitor.infrastructure.external.ChargingStationRegistryClient.Station;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Bildet Betreibernamen aus dem Ladesaeulenregister auf unsere kanonischen Ladenetze ab.
 *
 * <p>Bewusst streng in drei Stufen: Alias-Tabelle, exakter Name, exakter Name ohne Rechtsform.
 * Kein Raten ueber Teilstrings oder Aehnlichkeit. Eine falsche Zuordnung wuerde einen falschen
 * Arbeitspreis in die Ladekarten-Simulation tragen, und "EnBW ODR AG" ist eben nicht "EnBW".
 * Was sich nicht sicher zuordnen laesst, faellt heraus - Privatpersonen, Hotels und
 * Parkhausbetreiber, die im Register den Grossteil der Eintraege stellen, verschwinden damit
 * ohne eigene Filterregel.
 *
 * <p>Der Zustand wird beim Start einmal aus der Datenbank geladen
 * (siehe {@code CpoRegistryMatcherConfig}) und ist danach unveraenderlich.
 */
public class CpoRegistryMatcher {

    /**
     * Rechtsformen am Ende einer Firmierung. Absichtlich nur Rechtsformen - Ortsangaben wie
     * "Deutschland" bleiben stehen, sonst wuerde "Fastned Deutschland" faelschlich auf
     * "Fastned" gezogen, ohne dass jemand die Zuordnung geprueft hat.
     */
    private static final Pattern LEGAL_SUFFIX = Pattern.compile(
            "(?i)(\\s*[,&]?\\s*\\b(?:gmbh|mbh|ag|se|kg|ohg|kgaa|co|und|stiftung)\\b\\.?)+\\s*$");

    /** kleingeschriebener kanonischer Name -> kanonischer Name */
    private final Map<String, String> canonicalByLowercase;
    /** kleingeschriebene Firmierung -> kanonischer Name */
    private final Map<String, String> canonicalByAlias;

    public CpoRegistryMatcher(List<String> knownCpoNames, Map<String, String> aliases) {
        this.canonicalByLowercase = knownCpoNames.stream()
                .collect(Collectors.toMap(n -> n.toLowerCase(), n -> n, (a, b) -> a));
        this.canonicalByAlias = aliases.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(), Map.Entry::getValue, (a, b) -> a));
    }

    /**
     * Der kanonische Name des Ladenetzes zu einem Registereintrag.
     * Die Marke wird zuerst geprueft, sie ist der genauere Hinweis.
     */
    public Optional<String> match(Station station) {
        return resolve(station.brand()).or(() -> resolve(station.operator()));
    }

    /**
     * Alle Registereintraege eines Umkreises als Liste kanonischer Namen, ohne Doppelte
     * und mit dem im Umkreis haeufigsten Ladenetz zuerst.
     */
    public List<String> matchAll(List<Station> stations) {
        Map<String, Long> countByCpo = stations.stream()
                .map(this::match)
                .flatMap(Optional::stream)
                .collect(Collectors.groupingBy(n -> n, LinkedHashMap::new, Collectors.counting()));

        return countByCpo.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(e -> -e.getValue())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();
    }

    private Optional<String> resolve(String registryName) {
        if (registryName == null || registryName.isBlank()) {
            return Optional.empty();
        }
        String normalized = registryName.trim().toLowerCase();

        String byAlias = canonicalByAlias.get(normalized);
        if (byAlias != null) {
            return Optional.of(byAlias);
        }

        String exact = canonicalByLowercase.get(normalized);
        if (exact != null) {
            return Optional.of(exact);
        }

        String withoutLegalForm = LEGAL_SUFFIX.matcher(normalized).replaceFirst("").trim();
        if (withoutLegalForm.isEmpty() || withoutLegalForm.equals(normalized)) {
            return Optional.empty();
        }
        return Optional.ofNullable(canonicalByLowercase.get(withoutLegalForm));
    }
}
