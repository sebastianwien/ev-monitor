package com.evmonitor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Offene Ladungen einer Frage: Ladungen des Nutzers in ihrer Zelle mit ihrer Ladeart, ohne Säule,
 * nicht privat, nicht gelöscht, nicht von einer Wallbox.
 */
public interface ChargingSiteQuestionRepository {

    /**
     * Legt die Frage an, wenn es zu Nutzer, Zelle und Ladeart noch keine gibt - auch keine beantwortete.
     * Legt eine parallele Zuordnung dieselbe Frage gleichzeitig an, scheitert eine am Unique-Schlüssel.
     */
    void openIfAbsent(UUID userId, String geohash, ChargingType chargingType, String candidatesJson);

    Optional<ChargingSiteQuestion> findByIdAndUserId(UUID id, UUID userId);

    /** Offene Fragen mit mindestens einer offenen Ladung, die mit den meisten Ladungen zuerst. */
    List<ChargingSiteQuestion.WithLogs> findOpenWithLogs(UUID userId);

    List<UUID> findOpenLogIds(ChargingSiteQuestion question);

    /** Schließt eine offene Frage; false, wenn sie schon beantwortet oder abgelehnt war. */
    boolean close(UUID id, UUID userId, ChargingSiteQuestion.Status status);
}
