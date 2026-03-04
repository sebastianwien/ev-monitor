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
import java.util.List;

@Component
public class OnboardingReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(OnboardingReminderScheduler.class);
    private static final int REMINDER_DAYS_AFTER_REGISTRATION = 14;

    private final UserRepository userRepository;
    private final CarRepository carRepository;
    private final EvLogRepository evLogRepository;
    private final EmailService emailService;

    public OnboardingReminderScheduler(UserRepository userRepository, CarRepository carRepository,
                                       EvLogRepository evLogRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.carRepository = carRepository;
        this.evLogRepository = evLogRepository;
        this.emailService = emailService;
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void sendOnboardingReminders() {
        LocalDate targetDay = LocalDate.now().minusDays(REMINDER_DAYS_AFTER_REGISTRATION);

        List<User> candidates = userRepository.findRegisteredOnDay(targetDay);
        log.info("Onboarding reminder: {} candidate(s) registered on {}", candidates.size(), targetDay);

        for (User user : candidates) {
            boolean hasNoActivity = carRepository.countByUserId(user.getId()) == 0
                    || evLogRepository.countByUserId(user.getId()) == 0;
            if (hasNoActivity) {
                emailService.sendOnboardingReminderEmail(user.getEmail(), user.getUsername());
                log.info("Sent onboarding reminder to user {}", user.getId());
            }
        }
    }
}
