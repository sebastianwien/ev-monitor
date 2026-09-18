package com.evmonitor.application.imports.eudataact;

import com.evmonitor.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Einzige Quelle fuer die Frage "darf dieser Nutzer den VW-EU-Data-Act-AutoSync nutzen".
 * Der Connectors-Service fragt hier nach (beim Verbinden und taeglich fuer laufende
 * Verbindungen), das Frontend zeigt danach Karte, Trial-Hinweis oder Teaser.
 *
 * <p>Berechtigt sind die AutoSync-Tarife, privilegierte Rollen und - im launch-verankerten
 * Trial ({@link EudaAutoSyncTrialProperties}) - alle anderen. SUPPORTER zahlt nur fuer
 * Auswertungen und bekommt keinen AutoSync.
 */
@Service
@RequiredArgsConstructor
public class EudaAutoSyncEntitlementService {

    private final EudaAutoSyncTrialProperties trial;

    /**
     * @param entitled    darf AutoSync nutzen
     * @param viaTrial    nur wegen des Trials - dann laeuft es ab und der Nutzer soll das wissen
     * @param trialEndsAt letzter Trial-Tag des Nutzers (auch nach Ablauf, fuer die Ansprache)
     */
    public record Entitlement(boolean entitled, boolean viaTrial, LocalDate trialEndsAt) {}

    public Entitlement entitlementFor(User user) {
        return entitlementFor(user, LocalDate.now());
    }

    public Entitlement entitlementFor(User user, LocalDate today) {
        boolean permanent = user.getSubscriptionTier().grantsTelemetry() || user.hasPrivilegedRole();
        boolean withinTrial = user.isWithinTrial(trial.window(), today);
        return new Entitlement(permanent || withinTrial, !permanent && withinTrial, user.trialEndsAt(trial.window()));
    }
}
