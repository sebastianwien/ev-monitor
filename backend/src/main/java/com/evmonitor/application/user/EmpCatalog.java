package com.evmonitor.application.user;

import com.evmonitor.infrastructure.persistence.JpaEmpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Ordnet den frei eingegebenen Namen einer Ladekarte dem EMP-Katalog zu. Gross-/Klein-
 * schreibung und Rand-Leerraum spielen keine Rolle; sonst muss der Name exakt stimmen -
 * "EnBW mobility+" bleibt ohne Verweis, bis ein Alias-Mechanismus wie bei den Ladenetzen da ist.
 */
@Service
@RequiredArgsConstructor
public class EmpCatalog {

    private final JpaEmpRepository repository;

    public Optional<String> resolve(String cardName) {
        if (cardName == null || cardName.isBlank()) return Optional.empty();
        String wanted = key(cardName);
        return repository.findAllNamesSorted().stream().filter(n -> key(n).equals(wanted)).findFirst();
    }

    public List<String> allNames() {
        return repository.findAllNamesSorted();
    }

    private static String key(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }
}
