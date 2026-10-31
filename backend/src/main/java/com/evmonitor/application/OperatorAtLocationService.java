package com.evmonitor.application;

import com.evmonitor.application.publicapi.CpoNameNormalizer;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Trägt einen Betreiber auf die anderen öffentlichen Ladungen ohne Betreiber am selben Ort nach.
 *
 * Nur bei eindeutigem Ort: genau derselbe Geohash in öffentlicher Präzision (7 Zeichen, ~150 m;
 * 6 Zeichen sind ~600 m und fassen oft mehrere Säulen) und kein anderer Betreiber an diesem Geohash.
 * Die gerade bearbeitete Ladung zählt dabei nicht mit - ihr alter Wert wird ja gerade ersetzt.
 * Ein vorhandener Betreiber wird nie überschrieben.
 */
@Service
@RequiredArgsConstructor
public class OperatorAtLocationService {

    static final int MIN_UNIQUE_GEOHASH_LENGTH = 7;
    private static final int MAX_CPO_LENGTH = 100;
    private static final int MAX_GEOHASH_LENGTH = 12;

    private final EvLogRepository evLogRepository;
    private final EvLogWriter evLogWriter;
    private final CpoNameNormalizer cpoNameNormalizer;
    private final CoinLogService coinLogService;

    public record OperatorApplied(int updated, int coinsAwarded) {}

    @Transactional(readOnly = true)
    public long countOperatorGaps(UUID userId, String geohash, String cpoName, UUID excludeLogId) {
        return gaps(userId, geohash, cpoName, excludeLogId).size();
    }

    /** Prüft die Eindeutigkeit erneut: zwischen Abfrage und Speichern kann sich der Ort geändert haben. */
    @Transactional
    public OperatorApplied applyOperator(UUID userId, String geohash, String cpoName, UUID excludeLogId) {
        if (cpoName != null && cpoName.trim().length() > MAX_CPO_LENGTH) {
            throw new ValidationException("CPO_NAME_TOO_LONG", "Betreibername ist zu lang.");
        }
        String operator = cpoNameNormalizer.normalize(cpoName);
        int updated = 0;
        int coins = 0;
        for (EvLog log : gaps(userId, geohash, cpoName, excludeLogId)) {
            evLogWriter.save(log.toBuilder().cpoName(operator).build());
            updated++;
            if (updated <= EvLogService.BATCH_REWARD_CAP) {
                coins += coinLogService.awardOncePerEntity(userId, CoinLogService.CoinEvent.CPO_ADDED, log.getId());
            }
        }
        return new OperatorApplied(updated, coins);
    }

    private List<EvLog> gaps(UUID userId, String geohash, String cpoName, UUID excludeLogId) {
        String chosen = operatorKey(cpoName);
        if (chosen == null || chosen.length() > MAX_CPO_LENGTH || geohash == null
                || geohash.length() < MIN_UNIQUE_GEOHASH_LENGTH || geohash.length() > MAX_GEOHASH_LENGTH) return List.of();

        List<EvLog> others = evLogRepository.findAllByUserIdAndGeohash(userId, geohash).stream()
                .filter(log -> !log.getId().equals(excludeLogId))
                .toList();
        boolean otherOperatorHere = others.stream()
                .map(log -> operatorKey(log.getCpoName()))
                .filter(Objects::nonNull)
                .anyMatch(key -> !key.equals(chosen));
        if (otherOperatorHere) return List.of();

        return others.stream()
                .filter(EvLog::isPublicChargingConfirmed)
                .filter(log -> operatorKey(log.getCpoName()) == null)
                .toList();
    }

    /** Vergleichsschlüssel: Katalog-Betreiber (Tesla mit Standortzusatz = Supercharger), sonst Freitext ohne Groß/klein. */
    private String operatorKey(String name) {
        if (name == null || name.isBlank()) return null;
        String canonical = cpoNameNormalizer.canonicalOperator(name);
        return (canonical != null ? canonical : name.trim()).toLowerCase();
    }
}
