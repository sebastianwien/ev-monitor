package com.evmonitor.application;

import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ordnet automatisch eingegangene Ladungen ihrer Register-Säule zu, wenn das eindeutig ist
 * (Regel: {@link ChargingSiteMatcher}, Schreiben: {@link ChargingSiteAttacher}). Bleiben mehrere
 * Säulen offen, fragt die App den Nutzer ({@link ChargingSiteQuestionService}).
 *
 * Läuft nach dem Commit im Hintergrund: der Registerabruf ist ein Fremddienst, und ein Import
 * soll weder auf ihn warten noch an ihm scheitern. Was der Nutzer oder die Quelle schon gesagt
 * hat, gewinnt immer - eine gesetzte Säule, "privat", ein Betreibername, ein Preis.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChargingSiteAssignment {

    /** Ab 7 Stellen (~150 m) ist eine Ladung öffentlich verortet; darunter liegt sie zu grob für eine Säule. */
    private static final int MIN_GEOHASH_LENGTH = 7;

    /**
     * Höchstens so viele Registerabrufe je Ingest-Aufruf. Live-Quellen liefern eine Ladung je Aufruf;
     * ein historischer Import über Hunderte Orte soll das fremde Register nicht in einem Zug abfragen.
     * Ladungen an Orten, die schon im Cache liegen, zählen nicht mit und werden immer zugeordnet.
     */
    static final int REGISTER_LOOKUPS_PER_BATCH = 30;

    private final EvLogRepository evLogRepository;
    private final NearbyCpoService nearbyCpoService;
    private final ChargingSiteService chargingSiteService;
    private final ChargingSiteAttacher attacher;
    private final ChargingSiteQuestionService questions;

    /**
     * Für die Ladungen eines Ingest-Aufrufs, nacheinander - so fragt auch ein großer Import das
     * Register nie parallel an. Fehler einer Ladung halten die nächste nicht auf.
     */
    @Async
    public void assignAll(List<UUID> logIds) {
        AtomicInteger lookupsLeft = new AtomicInteger(REGISTER_LOOKUPS_PER_BATCH);
        for (UUID logId : logIds) {
            try {
                boolean assigned = assign(logId, lookupsLeft);
                log.debug("Säulen-Zuordnung für Ladung {}: {}", logId, assigned ? "zugeordnet" : "offen");
            } catch (RuntimeException e) {
                log.warn("Säulen-Zuordnung für Ladung {} fehlgeschlagen: {}", logId, e.getMessage());
            }
        }
    }

    /** @param lookupsLeft verbleibende Registerabrufe dieses Aufrufs, wird heruntergezählt */
    private boolean assign(UUID logId, AtomicInteger lookupsLeft) {
        EvLog evLog = evLogRepository.findById(logId).filter(ChargingSiteAssignment::isEligible).orElse(null);
        if (evLog == null) return false;

        // Der Nutzer kommt vom Auto der Ladung, nie vom Aufrufer: Preis und Karte stammen aus
        // seiner Historie, eine fremde Ladung darf sie nie bekommen.
        UUID owner = evLogRepository.findOwnerId(logId).orElse(null);
        if (owner == null) return false;

        if (!nearbyCpoService.isStationsCached(evLog.getGeohash(), null) && lookupsLeft.getAndDecrement() <= 0) {
            return false;
        }
        // Außerhalb der Transaktion: der Registerabruf darf keine DB-Verbindung festhalten.
        Optional<List<NearbyStation>> nearby = nearbyCpoService.findNearbyStations(evLog.getGeohash());
        if (nearby.isEmpty()) return false;

        ChargingSiteMatcher.Result match = ChargingSiteMatcher.pick(evLog.getChargingType(),
                evLog.getMaxChargingPowerKw(), nearby.get(), chargingSiteService.usedSiteKeys(owner));
        if (match.station() == null) {
            // Mehrere Säulen kommen in Frage: der Nutzer weiß es. Ohne Säule im Register gibt es nichts zu fragen.
            if (match.candidates().size() > 1) questions.ask(owner, evLog, match.candidates());
            return false;
        }
        return attacher.attach(evLog, owner, match.station());
    }

    /** Private Ladungen, Wallboxen und vom Nutzer gewählte Säulen bleiben, wie sie sind. */
    private static boolean isEligible(EvLog evLog) {
        return evLog.getChargingSiteId() == null
                && !Boolean.FALSE.equals(evLog.getPublicCharging())
                && evLog.getGeohash() != null && evLog.getGeohash().length() >= MIN_GEOHASH_LENGTH
                && evLog.getDataSource() != DataSource.WALLBOX_GOE
                && evLog.getDataSource() != DataSource.WALLBOX_OCPP;
    }
}
