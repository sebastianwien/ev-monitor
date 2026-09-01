package com.evmonitor.infrastructure.scheduling;

import com.evmonitor.application.LeaderboardService;
import com.evmonitor.application.SpecChargingEfficiencyJob;
import com.evmonitor.application.SurveyService;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.github.GitHubIssueService;
import com.evmonitor.infrastructure.weather.TemperatureBackfillJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class AppScheduler {

    private static final int REMINDER_DAYS_AFTER_REGISTRATION = 14;
    private static final int RE_ENGAGEMENT_DAYS_INACTIVE = 21;
    private static final int DORMANT_AUTOSYNC_DAYS_INACTIVE = 21;
    // 7-day Stripe trial + 28 days of paid usage = "4 weeks after the trial ended".
    private static final int AUTOSYNC_SATISFACTION_DAYS_AFTER_PURCHASE = 35;
    private static final String AUTOSYNC_SATISFACTION_SLUG = "autosync-satisfaction";

    private final UserRepository userRepository;
    private final CarRepository carRepository;
    private final EvLogRepository evLogRepository;
    private final EmailService emailService;
    private final GitHubIssueService gitHubIssueService;
    private final TemperatureBackfillJob temperatureBackfillJob;
    private final com.evmonitor.infrastructure.weather.TripTemperatureBackfillJob tripTemperatureBackfillJob;
    private final LeaderboardService leaderboardService;
    private final SpecChargingEfficiencyJob specChargingEfficiencyJob;
    private final SurveyService surveyService;

    public AppScheduler(UserRepository userRepository, CarRepository carRepository,
                        EvLogRepository evLogRepository, EmailService emailService,
                        GitHubIssueService gitHubIssueService,
                        TemperatureBackfillJob temperatureBackfillJob,
                        com.evmonitor.infrastructure.weather.TripTemperatureBackfillJob tripTemperatureBackfillJob,
                        LeaderboardService leaderboardService,
                        SpecChargingEfficiencyJob specChargingEfficiencyJob,
                        SurveyService surveyService) {
        this.userRepository = userRepository;
        this.carRepository = carRepository;
        this.evLogRepository = evLogRepository;
        this.emailService = emailService;
        this.gitHubIssueService = gitHubIssueService;
        this.temperatureBackfillJob = temperatureBackfillJob;
        this.tripTemperatureBackfillJob = tripTemperatureBackfillJob;
        this.leaderboardService = leaderboardService;
        this.specChargingEfficiencyJob = specChargingEfficiencyJob;
        this.surveyService = surveyService;
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
                    emailService.sendOnboardingReminderEmail(user.getEmail(), user.getUsername(), user.getRegistrationLocale());
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
                emailService.sendReEngagementEmail(user.getEmail(), user.getUsername(), user.getRegistrationLocale());
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
     * Re-engages users whose car is still logging via a live connector (Tesla/Smartcar/VW
     * Group/XPeng) but who haven't opened the app themselves in {@link
     * #DORMANT_AUTOSYNC_DAYS_INACTIVE} days. Deliberately separate from {@link
     * #sendReEngagementEmails}: that one keys off the last ev_log, which for this group
     * keeps refreshing on its own and would never hit the exact-day match.
     *
     * <p>Uses {@code last_seen <= day} plus a "not yet mailed" flag rather than an exact-day
     * match: this run absorbs any backlog of users who already crossed the threshold before
     * this feature existed, or whose exact day was missed by a deploy - no separate backfill
     * needed. Failures are isolated per user so one bad send doesn't skip the rest of the cohort.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendDormantAutoSyncEmails() {
        LocalDate targetDay = LocalDate.now().minusDays(DORMANT_AUTOSYNC_DAYS_INACTIVE);

        List<User> candidates = userRepository.findDormantAutoSyncUsersDue(targetDay);
        log.info("Dormant AutoSync: {} candidate(s) last seen on or before {}", candidates.size(), targetDay);

        List<String> mailed = new ArrayList<>();
        int failed = 0;

        for (User user : candidates) {
            try {
                emailService.sendAutoSyncDormantEmail(user.getEmail(), user.getUsername(), user.getRegistrationLocale());
                userRepository.markDormantAutoSyncEmailSent(user.getId(), LocalDateTime.now());
                mailed.add(user.getUsername());
                log.info("Sent dormant AutoSync email to user {}", user.getId());
            } catch (Exception e) {
                failed++;
                log.error("Dormant AutoSync send failed for user {}", user.getId(), e);
            }
        }

        if (!mailed.isEmpty() || failed > 0) {
            log.info("Dormant AutoSync report: {} sent, {} failed - {}", mailed.size(), failed, mailed);
        }
        if (failed > 0) {
            gitHubIssueService.createIssue(
                    "dormant-autosync-error-" + targetDay,
                    "🚨 [EV Monitor] Dormant-AutoSync-Mails: " + failed + " Versand(e) fehlgeschlagen",
                    "## Versand-Fehler\n\nDatum: `%s`\n\n%d von %d Mails fehlgeschlagen (Details im Log)."
                            .formatted(targetDay, failed, candidates.size())
            );
        }
    }

    /**
     * Asks AutoSync subscribers how satisfied they are, 4 weeks after their 7-day trial
     * ended (= 35 days after the purchase moment stamped in autosync_started_at). The
     * exact-day match means each user is mailed on a single calendar day, not repeatedly;
     * users who already answered are skipped so a reconnect cannot re-mail a responder.
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendAutoSyncSatisfactionSurveys() {
        LocalDate targetDay = LocalDate.now().minusDays(AUTOSYNC_SATISFACTION_DAYS_AFTER_PURCHASE);

        List<User> candidates = userRepository.findAutoSyncSurveyCandidates(targetDay);
        log.info("AutoSync satisfaction: {} candidate(s) purchased on {}", candidates.size(), targetDay);

        List<String> mailed = new ArrayList<>();
        int failed = 0;

        // Isolate failures per user: the exact-day match means there is no retry, so a single
        // bad send (SMTP hiccup, template issue) must not skip the rest of the day's cohort.
        for (User user : candidates) {
            try {
                if (surveyService.hasResponded(AUTOSYNC_SATISFACTION_SLUG, user.getId())) {
                    continue;
                }
                emailService.sendAutoSyncSatisfactionEmail(user.getEmail(), user.getUsername(), user.getRegistrationLocale());
                mailed.add(user.getUsername());
                log.info("Sent AutoSync satisfaction survey to user {}", user.getId());
            } catch (Exception e) {
                failed++;
                log.error("AutoSync satisfaction send failed for user {}", user.getId(), e);
            }
        }

        if (!mailed.isEmpty() || failed > 0) {
            log.info("AutoSync satisfaction report: {} sent, {} failed - {}", mailed.size(), failed, mailed);
        }
        if (failed > 0) {
            gitHubIssueService.createIssue(
                    "autosync-satisfaction-error-" + targetDay,
                    "🚨 [EV Monitor] AutoSync Satisfaction: " + failed + " Versand(e) fehlgeschlagen",
                    "## Versand-Fehler\n\nDatum: `%s`\n\n%d von %d Mails fehlgeschlagen (Details im Log)."
                            .formatted(targetDay, failed, candidates.size())
            );
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

    /**
     * Laeuft nacheinander, nicht parallel: beide Backfills teilen sich das Tageskontingent der
     * freien Open-Meteo-API, und gleichzeitig laufende Laeufe wuerden es doppelt verbrauchen.
     */
    @Scheduled(cron = "0 30 2 * * *")
    public void backfillMissingTemperatures() {
        log.info("Daily temperature backfill started");
        try {
            String summary = temperatureBackfillJob.run() + " | " + tripTemperatureBackfillJob.run();
            log.info("Daily temperature backfill finished: {}", summary);
        } catch (Exception e) {
            log.error("Daily temperature backfill failed", e);
        }
    }

    @Scheduled(cron = "0 0 3 * * MON")
    public void updateSpecChargingEfficiencies() {
        log.info("SpecChargingEfficiencyJob started");
        try {
            String summary = specChargingEfficiencyJob.run();
            log.info("SpecChargingEfficiencyJob finished: {}", summary);
        } catch (Exception e) {
            log.error("SpecChargingEfficiencyJob failed", e);
        }
    }
}
