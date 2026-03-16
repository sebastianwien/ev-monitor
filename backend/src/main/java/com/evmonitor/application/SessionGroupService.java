package com.evmonitor.application;

import com.evmonitor.domain.ChargingSessionGroup;
import com.evmonitor.domain.ChargingSessionGroupRepository;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Gruppiert Wallbox-Micro-Sessions zu logischen Ladegruppen.
 *
 * Anwendungsfall: Überschussladen (Solar-Surplus) an einer go-e Wallbox.
 * Die Wallbox startet und stoppt je nach verfügbarem Überschuss mehrfach, was
 * viele kleine WALLBOX_GOE Sessions erzeugt. Diese werden hier zu einer
 * Gruppe zusammengefasst (gleicher Tag, Gap < merge_gap_minutes).
 *
 * Merge-Kriterium:
 * - data_source = WALLBOX_GOE (erweiterbar auf andere Surplus-Quellen)
 * - Gleicher Kalendertag
 * - Gap zwischen letztem session_end und neuem session_start < DEFAULT_MERGE_GAP_MINUTES
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
     * Verarbeitet einen neuen Wallbox-Log und gruppiert ihn ggf. mit bestehenden Sessions.
     * Wird nach dem Speichern eines WALLBOX_GOE Logs aufgerufen.
     *
     * Läuft in derselben Transaktion wie der Log-Save (REQUIRED):
     * Log-Save und Group-Linking sind atomic — entweder beide committed oder keiner.
     *
     * @param savedLog Der gerade gespeicherte Log-Eintrag
     */
    @Transactional
    public void processSessionForGrouping(EvLog savedLog) {
        processSessionForGrouping(savedLog, DEFAULT_MERGE_GAP_MINUTES);
    }

    /**
     * Verarbeitet einen neuen Wallbox-Log mit konfiguriertem Merge-Fenster.
     *
     * @param savedLog Der gerade gespeicherte Log-Eintrag
     * @param mergeGapMinutes Maximale Pause zwischen Sessions (in Minuten) für Gruppierung
     */
    @Transactional
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
