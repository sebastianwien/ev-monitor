package com.evmonitor.infrastructure.scheduling;

import com.evmonitor.application.LeaderboardService;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.github.GitHubIssueService;
import com.evmonitor.infrastructure.weather.TemperatureBackfillJob;
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
    private static final int RE_ENGAGEMENT_DAYS_INACTIVE = 14;

    private final UserRepository userRepository;
    private final CarRepository carRepository;
    private final EvLogRepository evLogRepository;
    private final EmailService emailService;
    private final GitHubIssueService gitHubIssueService;
    private final TemperatureBackfillJob temperatureBackfillJob;
    private final LeaderboardService leaderboardService;

    public AppScheduler(UserRepository userRepository, CarRepository carRepository,
                        EvLogRepository evLogRepository, EmailService emailService,
                        GitHubIssueService gitHubIssueService,
                        TemperatureBackfillJob temperatureBackfillJob,
                        LeaderboardService leaderboardService) {
        this.userRepository = userRepository;
        this.carRepository = carRepository;
        this.evLogRepository = evLogRepository;
        this.emailService = emailService;
        this.gitHubIssueService = gitHubIssueService;
        this.temperatureBackfillJob = temperatureBackfillJob;
        this.leaderboardService = leaderboardService;
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

    @Scheduled(cron = "0 0 7 * * *")
    public void sendReEngagementEmails() {
        LocalDate targetDay = LocalDate.now().minusDays(RE_ENGAGEMENT_DAYS_INACTIVE);

        List<User> candidates = userRepository.findUsersWithLastLogOnDay(targetDay);
        log.info("Re-engagement: {} candidate(s) with last log on {}", candidates.size(), targetDay);

        List<String> reminded = new ArrayList<>();

        try {
            for (User user : candidates) {
                emailService.sendReEngagementEmail(user.getEmail(), user.getUsername());
                reminded.add(user.getUsername());
                log.info("Sent re-engagement email to user {}", user.getId());
            }
        } catch (Exception e) {
            log.error("Re-engagement scheduler failed", e);
            gitHubIssueService.createIssue(
                    "re-engagement-error-" + targetDay,
                    "🚨 [EV Monitor] Re-Engagement Scheduler fehlgeschlagen",
                    "## Scheduler-Fehler\n\nDatum: `%s`\n\nException: `%s: %s`"
                            .formatted(targetDay, e.getClass().getSimpleName(), e.getMessage())
            );
            return;
        }

        if (!reminded.isEmpty()) {
            log.info("Re-engagement report: {} sent — {}", reminded.size(), reminded);
        }
    }

    /**
     * Runs at 00:05 on the 1st of each month.
     * Awards bonus coins to the top 3 users of the previous month's leaderboard categories.
     */
    @Scheduled(cron = "0 5 0 1 * *")
    public void awardMonthlyLeaderboardRewards() {
        LocalDate previousMonth = LocalDate.now().minusMonths(1);
        log.info("Awarding monthly leaderboard rewards for {}", previousMonth.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
        try {
            leaderboardService.awardMonthEndRewards(previousMonth);
            log.info("Monthly leaderboard rewards awarded for {}", previousMonth);
        } catch (Exception e) {
            log.error("Monthly leaderboard reward job failed for {}", previousMonth, e);
            gitHubIssueService.createIssue(
                    "leaderboard-reward-error-" + previousMonth,
                    "Leaderboard Reward Job fehlgeschlagen - " + previousMonth,
                    "## Fehler\n\nMonat: `%s`\n\nException: `%s: %s`"
                            .formatted(previousMonth, e.getClass().getSimpleName(), e.getMessage())
            );
        }
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void backfillMissingTemperatures() {
        log.info("Daily temperature backfill started");
        try {
            String summary = temperatureBackfillJob.run();
            log.info("Daily temperature backfill finished: {}", summary);
        } catch (Exception e) {
            log.error("Daily temperature backfill failed", e);
        }
    }
}
