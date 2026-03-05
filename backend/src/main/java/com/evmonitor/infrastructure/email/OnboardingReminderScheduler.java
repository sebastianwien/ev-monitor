package com.evmonitor.infrastructure.email;

import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class OnboardingReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(OnboardingReminderScheduler.class);
    private static final int REMINDER_DAYS_AFTER_REGISTRATION = 14;

    private final UserRepository userRepository;
    private final CarRepository carRepository;
    private final EvLogRepository evLogRepository;
    private final EmailService emailService;
    private final AlertEmailService alertEmailService;

    public OnboardingReminderScheduler(UserRepository userRepository, CarRepository carRepository,
                                       EvLogRepository evLogRepository, EmailService emailService,
                                       AlertEmailService alertEmailService) {
        this.userRepository = userRepository;
        this.carRepository = carRepository;
        this.evLogRepository = evLogRepository;
        this.emailService = emailService;
        this.alertEmailService = alertEmailService;
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void sendOnboardingReminders() {
        LocalDate targetDay = LocalDate.now().minusDays(REMINDER_DAYS_AFTER_REGISTRATION);

        List<User> candidates = userRepository.findRegisteredOnDay(targetDay);
        log.info("Onboarding reminder: {} candidate(s) registered on {}", candidates.size(), targetDay);

        List<String> reminded = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        try {
            for (User user : candidates) {
                boolean hasNoActivity = carRepository.countByUserId(user.getId()) == 0
                        || evLogRepository.countByUserId(user.getId()) == 0;
                if (hasNoActivity) {
                    emailService.sendOnboardingReminderEmail(user.getEmail(), user.getUsername());
                    reminded.add(user.getUsername());
                    log.info("Sent onboarding reminder to user {}", user.getId());
                } else {
                    skipped.add(user.getUsername());
                }
            }
        } catch (Exception e) {
            log.error("Onboarding reminder scheduler failed", e);
            alertEmailService.sendAlert(
                    "onboarding-reminder-error-" + targetDay,
                    "🚨 [EV Monitor] Onboarding Reminder Scheduler fehlgeschlagen",
                    "Der Scheduler ist am %s mit einer Exception abgebrochen:\n\n%s: %s"
                            .formatted(targetDay, e.getClass().getSimpleName(), e.getMessage())
            );
            return;
        }

        if (!reminded.isEmpty()) {
            String body = buildReportBody(targetDay, candidates.size(), reminded, skipped);
            alertEmailService.sendAlert(
                    "onboarding-reminder-report-" + targetDay,
                    "[EV Monitor] Onboarding Reminder: %d Mail(s) verschickt".formatted(reminded.size()),
                    body
            );
        }
    }

    private String buildReportBody(LocalDate targetDay, int totalCandidates,
                                   List<String> reminded, List<String> skipped) {
        StringBuilder sb = new StringBuilder();
        sb.append("EV Monitor — Onboarding Reminder Report\n");
        sb.append("========================================\n\n");
        sb.append("Datum:        ").append(LocalDate.now()).append("\n");
        sb.append("Zielgruppe:   Registriert am ").append(targetDay).append("\n");
        sb.append("Kandidaten:   ").append(totalCandidates).append("\n");
        sb.append("Versendet:    ").append(reminded.size()).append("\n");
        sb.append("Übersprungen: ").append(skipped.size()).append(" (bereits aktiv)\n\n");

        sb.append("Reminder verschickt an:\n");
        reminded.forEach(u -> sb.append("  - ").append(u).append("\n"));

        if (!skipped.isEmpty()) {
            sb.append("\nÜbersprungen (haben Auto oder Log):\n");
            skipped.forEach(u -> sb.append("  - ").append(u).append("\n"));
        }

        return sb.toString();
    }
}
