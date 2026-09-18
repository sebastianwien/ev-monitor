package com.evmonitor.application.imports.eudataact;

import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Nutzer-Benachrichtigungen des EU-Data-Act-AutoSync - der Connector meldet nur das Ereignis,
 * Sprache und Anrede kennt der Core. Jedes Ereignis geht per E-Mail; den aktuellen Zustand
 * zeigt zusaetzlich die Verbindungs-Karte im Frontend.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EudaNotificationService {

    public static final String EVENT_HANDOVER = "SMARTCAR_HANDOVER";
    public static final String EVENT_CONNECTION_LOST = "CONNECTION_LOST";
    public static final String EVENT_HISTORY_IMPORTED = "HISTORY_IMPORTED";

    private final UserRepository userRepository;
    private final EmailService emailService;

    public void notify(UUID userId, String event, Map<String, Object> params) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("[EUDA] Benachrichtigung {} fuer unbekannten userId={}", event, userId);
            return;
        }
        switch (event) {
            case EVENT_HANDOVER -> emailService.sendEuDataActHandoverEmail(
                    user.getEmail(), user.getUsername(), user.getRegistrationLocale());
            case EVENT_CONNECTION_LOST -> emailService.sendEuDataActConnectionLostEmail(
                    user.getEmail(), user.getUsername(), user.getRegistrationLocale());
            case EVENT_HISTORY_IMPORTED -> emailService.sendEuDataActHistoryImportedEmail(
                    user.getEmail(), user.getUsername(), user.getRegistrationLocale(),
                    intParam(params, "imported"), intParam(params, "skipped"));
            default -> log.warn("[EUDA] Unbekanntes Ereignis {} fuer userId={}", event, userId);
        }
    }

    private static int intParam(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }
}
