package com.evmonitor.infrastructure.scheduling;

import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.github.GitHubIssueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class AppScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppScheduler.class);
    private static final int REMINDER_DAYS_AFTER_REGISTRATION = 14;

    private final UserRepository userRepository;
    private final CarRepository carRepository;
    private final EvLogRepository evLogRepository;
    private final EmailService emailService;
    private final GitHubIssueService gitHubIssueService;

    public AppScheduler(UserRepository userRepository, CarRepository carRepository,
                        EvLogRepository evLogRepository, EmailService emailService,
                        GitHubIssueService gitHubIssueService) {
        this.userRepository = userRepository;
        this.carRepository = carRepository;
        this.evLogRepository = evLogRepository;
        this.emailService = emailService;
        this.gitHubIssueService = gitHubIssueService;
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
            gitHubIssueService.createIssue(
                    "onboarding-reminder-error-" + targetDay,
                    "🚨 [EV Monitor] Onboarding Reminder Scheduler fehlgeschlagen",
                    "## Scheduler-Fehler\n\nDatum: `%s`\n\nException: `%s: %s`"
                            .formatted(targetDay, e.getClass().getSimpleName(), e.getMessage())
            );
            return;
        }

        if (!reminded.isEmpty()) {
            log.info("Onboarding reminder report: {} sent, {} skipped — {}",
                    reminded.size(), skipped.size(), reminded);
        }
    }
}
