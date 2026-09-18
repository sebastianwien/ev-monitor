package com.evmonitor.application.imports.eudataact;

import com.evmonitor.domain.TrialWindow;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Gratis-Fenster fuer den VW-EU-Data-Act-AutoSync ({@code euda.autosync.trial.*}).
 * Launch-Datum und Dauer sind bewusst konfigurierbar - die Dauer steht noch nicht fest
 * ({@code EUDA_AUTOSYNC_TRIAL_DAYS}), das Launch-Datum muss dem Deploy-Tag entsprechen.
 */
@ConfigurationProperties(prefix = "euda.autosync.trial")
@Component
@Getter
@Setter
public class EudaAutoSyncTrialProperties {

    private LocalDate launchDate = LocalDate.of(2026, 9, 21);
    private int days = 30;

    public TrialWindow window() {
        return new TrialWindow(launchDate, days);
    }
}
