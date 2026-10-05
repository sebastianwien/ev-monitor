package com.evmonitor.application.voice;

import com.evmonitor.application.AdminAlertService;
import com.evmonitor.infrastructure.persistence.voice.VoiceDraftRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Kostenwaechter des Sprachlogs: nach jeder protokollierten Aufnahme wird die Summe von
 * {@code voice_draft.cost_usd} seit Monatsbeginn (Serverzeit) geprueft. Ueberschreitet sie eine weitere
 * Stufe (Default 10 USD, {@code voice-log.alert-step-usd}), geht eine Mail an
 * {@code voice-log.alert-email}. Mit dem neuen Monat beginnt die Zaehlung bei 0. Best Effort, wirft nie.
 */
@Service
@Slf4j
public class VoiceCostAlertService {

    private final VoiceDraftRepository repository;
    private final AdminAlertService alerts;
    private final BigDecimal stepUsd;

    public VoiceCostAlertService(VoiceDraftRepository repository, AdminAlertService alerts,
                                 @Value("${voice-log.alert-step-usd:10}") BigDecimal stepUsd) {
        this.repository = repository;
        this.alerts = alerts;
        this.stepUsd = stepUsd;
    }

    /** @param justAdded Kosten der soeben gespeicherten Zeile (sie steckt bereits in der Summe). */
    public void afterRecorded(BigDecimal justAdded) {
        if (justAdded == null || justAdded.signum() <= 0 || stepUsd.signum() <= 0) return;
        try {
            BigDecimal month = nz(repository.sumCostUsdSince(LocalDate.now().withDayOfMonth(1).atStartOfDay()));
            BigDecimal before = month.subtract(justAdded);
            BigDecimal stepAfter = month.divide(stepUsd, 0, RoundingMode.FLOOR);
            if (stepAfter.compareTo(before.divide(stepUsd, 0, RoundingMode.FLOOR)) <= 0) return;
            BigDecimal total = nz(repository.sumCostUsd());
            alerts.sendVoiceCostAlert(month, total, stepAfter.multiply(stepUsd).setScale(0, RoundingMode.HALF_UP));
        } catch (Exception e) {
            log.warn("Sprachlog-Kostenalarm nicht geprueft: {}", e.getClass().getSimpleName());
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
