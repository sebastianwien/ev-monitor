package com.evmonitor.application;

import com.evmonitor.domain.ChargingSessionGroup;
import com.evmonitor.domain.ChargingSessionGroupRepository;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gruppiert mehrere Ladevorgänge zu logischen Ladegruppen.
 *
 * Zwei Gruppierungsstrategien:
 *
 * 1. Zeitbasiert (processSessionForGrouping) — für Echtzeit-Sessions von Wallboxen und API-Uploads.
 *    Anwendungsfall: Solar-Überschussladen — die Wallbox startet/stoppt mehrfach je nach
 *    verfügbarem Überschuss. Sessions desselben Tages mit kurzem Abstand (< merge_gap_minutes)
 *    werden zusammengefasst.
 *    Unterstützte Quellen: WALLBOX_GOE, API_UPLOAD
 *
 * 2. Odometer-basiert (groupByOdometer) — für Batch-Imports mit geteiltem Kilometerstand.
 *    Anwendungsfall: Spritmonitor-Import — mehrere Ladevorgänge am selben Stopp teilen sich
 *    einen Odometer-Wert. Ohne Gruppierung schlägt der Verbrauch-Plausibilitätscheck fehl.
 *    Unterstützte Quellen: SPRITMONITOR_IMPORT
 */
@Service
public class SessionGroupService {

    private static final Logger log = LoggerFactory.getLogger(SessionGroupService.class);

    /** Default: Sessions mit weniger als 90 Minuten Pause werden gruppiert. */
    public static final int DEFAULT_MERGE_GAP_MINUTES = 90;

    private final ChargingSessionGroupRepository groupRepository;
    private final EvLogRepository evLogRepository;

    public SessionGroupService(ChargingSessionGroupRepository groupRepository,
            EvLogRepository evLogRepository) {
        this.groupRepository = groupRepository;
        this.evLogRepository = evLogRepository;
    }

    /**
     * Verarbeitet einen neu gespeicherten Log und gruppiert ihn zeitbasiert mit bestehenden Sessions.
     * Für WALLBOX_GOE und API_UPLOAD; andere Quellen werden ignoriert.
     *
     * Läuft in einer eigenen Transaktion (REQUIRES_NEW) damit ein Fehler beim Grouping
     * nicht die äußere Log-Save-Transaktion als rollback-only markiert. Die Caller
     * (createWallboxLog, logCharging, PublicApiImportService) wollen den Log auch dann
     * speichern wenn das Grouping fehlschlägt.
     *
     * @param savedLog Der gerade gespeicherte Log-Eintrag
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSessionForGrouping(EvLog savedLog) {
        processSessionForGrouping(savedLog, DEFAULT_MERGE_GAP_MINUTES);
    }

    /**
     * Verarbeitet einen neu gespeicherten Log mit konfiguriertem Merge-Fenster.
     * Für WALLBOX_GOE und API_UPLOAD; andere Quellen werden ignoriert.
     *
     * @param savedLog Der gerade gespeicherte Log-Eintrag
     * @param mergeGapMinutes Maximale Pause zwischen Sessions (in Minuten) für Gruppierung
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSessionForGrouping(EvLog savedLog, int mergeGapMinutes) {
        // Nur WALLBOX_GOE und API_UPLOAD Sessions gruppieren
        if (savedLog.getDataSource() != DataSource.WALLBOX_GOE
                && savedLog.getDataSource() != DataSource.API_UPLOAD) {
            return;
        }

        LocalDateTime newSessionStart = savedLog.getLoggedAt();
        LocalDateTime threshold = newSessionStart.minusMinutes(mergeGapMinutes);

        Optional<ChargingSessionGroup> openGroup = groupRepository.findOpenGroupForCar(
                savedLog.getCarId(),
                threshold,
                newSessionStart.toLocalDate(),
                savedLog.getDataSource().name());

        UUID groupId;
        if (openGroup.isPresent()) {
            // Bestehende Gruppe erweitern
            ChargingSessionGroup group = openGroup.get();
            group.addSubSession(savedLog);
            ChargingSessionGroup saved = groupRepository.save(group);
            groupId = saved.getId();
        } else {
            // Neue Gruppe anlegen
            ChargingSessionGroup newGroup = ChargingSessionGroup.createFrom(savedLog);
            ChargingSessionGroup saved = groupRepository.save(newGroup);
            groupId = saved.getId();
        }

        // Log mit der Gruppe verknüpfen
        evLogRepository.setSessionGroupId(savedLog.getId(), groupId);
    }

    /**
     * Gruppiert Sprit-Monitor-Einträge mit gleichem Odometer-Wert zu Session-Gruppen.
     *
     * Hintergrund: Spritmonitor erlaubt mehrere Ladevorgänge pro Stopp mit identischem
     * Kilometerstand. Ohne Gruppierung schlägt der Plausibilitätscheck fehl - ein Log
     * bekommt die gesamte Distanz, aber nur einen Teil der Energie (z.B. 11 kWh / 2647 km
     * = 0.42 kWh/100km → wird als implausibel verworfen).
     *
     * Logs ohne Odometer-Wert und Einzel-Einträge bleiben unberührt.
     * Läuft in derselben Transaktion wie der Import (REQUIRED).
     *
     * @param savedLogs Alle in diesem Import-Batch gespeicherten Logs (chronologisch sortiert)
     */
    @Transactional
    public void groupByOdometer(List<EvLog> savedLogs) {
        savedLogs.stream()
                .filter(l -> l.getOdometerKm() != null)
                .collect(Collectors.groupingBy(EvLog::getOdometerKm))
                .values().stream()
                .filter(group -> group.size() > 1)
                .forEach(group -> {
                    List<EvLog> sorted = group.stream()
                            .sorted(Comparator.comparing(EvLog::getLoggedAt))
                            .toList();

                    ChargingSessionGroup sessionGroup = ChargingSessionGroup.createFrom(sorted.get(0));
                    for (int i = 1; i < sorted.size(); i++) {
                        sessionGroup.addSubSession(sorted.get(i));
                    }
                    ChargingSessionGroup saved = groupRepository.save(sessionGroup);

                    for (EvLog evLog : sorted) {
                        evLogRepository.setSessionGroupId(evLog.getId(), saved.getId());
                    }

                    log.debug("Grouped {} logs at odometer {} km into session group {}",
                            sorted.size(), sorted.get(0).getOdometerKm(), saved.getId());
                });
    }

    /**
     * Löscht alle Session-Gruppen eines Users für eine DataSource.
     * Muss NACH dem Löschen der zugehörigen ev_log-Einträge aufgerufen werden.
     */
    @Transactional
    public void deleteGroupsByUserIdAndDataSource(UUID userId, String dataSource) {
        groupRepository.deleteAllByUserIdAndDataSource(userId, dataSource);
    }

    /**
     * Gibt alle Sub-Sessions einer Gruppe zurück.
     * Für die Detail-Aufklapp-Ansicht im Dashboard.
     */
    public List<EvLogResponse> getSubSessions(UUID groupId) {
        return evLogRepository.findAllBySessionGroupId(groupId).stream()
                .map(EvLogResponse::fromDomain)
                .toList();
    }

    /**
     * Gibt eine Gruppe anhand ihrer ID zurück.
     */
    public Optional<SessionGroupResponse> findById(UUID groupId) {
        return groupRepository.findById(groupId).map(SessionGroupResponse::fromDomain);
    }

    /**
     * Gibt alle Gruppen für ein Fahrzeug zurück.
     */
    public List<SessionGroupResponse> findAllByCarId(UUID carId) {
        return groupRepository.findAllByCarId(carId).stream()
                .map(SessionGroupResponse::fromDomain)
                .toList();
    }
}
