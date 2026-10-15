package com.evmonitor.application.vehiclespec;

import com.evmonitor.application.AdminAlertService;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.vehiclespec.MissingVehicleRequest;
import com.evmonitor.infrastructure.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mails des Meldewegs: Admin-Hinweis beim Eingang, Nutzer-Rückmeldung beim Erledigen.
 * Die Kontakt-Mail erscheint nie im Log, nur die Meldungs-ID.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MissingVehicleRequestNotifier {

    private final AdminAlertService adminAlertService;
    private final EmailService emailService;
    private final UserRepository userRepository;

    public void notifyAdmin(MissingVehicleRequest request) {
        adminAlertService.sendMissingVehicleRequest(request);
    }

    /** @return true, wenn die Rückmeldung an den Nutzer rausging. */
    public boolean notifyUserDone(MissingVehicleRequest request) {
        if (request.getContactEmail() == null) {
            return false;
        }
        String locale = userRepository.findById(request.getUserId()).map(User::getRegistrationLocale).orElse(null);
        try {
            emailService.sendMissingVehicleDoneEmail(request.getContactEmail(), locale, request.getFreeText());
            return true;
        } catch (RuntimeException e) {
            log.error("Fahrzeug-Meldung: Rückmeldung an Nutzer fehlgeschlagen, id={}", request.getId(), e);
            return false;
        }
    }
}
