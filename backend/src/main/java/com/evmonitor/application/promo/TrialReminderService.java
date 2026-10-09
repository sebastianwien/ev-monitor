package com.evmonitor.application.promo;

import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.persistence.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mail 7 days before a long trial ends (FIRSTEV: day 21 of 28), so nobody is charged by surprise.
 * Each reminder is claimed with an atomic update before sending: during a Blue/Green switch two
 * instances may run the job, only one of them mails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrialReminderService {

    static final int DAYS_BEFORE_END = 7;

    private final JpaUserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public void schedule(UUID userId, LocalDateTime dueAt) {
        userRepository.setTrialReminderDueAt(userId, dueAt);
    }

    @Transactional
    public void clear(UUID userId) {
        userRepository.setTrialReminderDueAt(userId, null);
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Berlin")
    public void sendDueReminders() {
        for (TrialReminderDue due : userRepository.findTrialRemindersDue(LocalDateTime.now())) {
            if (userRepository.claimTrialReminder(due.userId()) != 1) continue;
            emailService.sendTrialEndingEmail(due.email(), due.username(), due.locale(),
                    due.dueAt().plusDays(DAYS_BEFORE_END).toLocalDate());
            log.info("Trial reminder sent to user {}", due.userId());
        }
    }
}
