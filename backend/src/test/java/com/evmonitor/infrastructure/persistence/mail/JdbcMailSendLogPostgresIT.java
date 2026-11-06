package com.evmonitor.infrastructure.persistence.mail;

import com.evmonitor.application.mail.MailSendLog;
import com.evmonitor.application.mail.MailSendLog.CampaignGoal;
import com.evmonitor.application.mail.MailSendLog.CampaignVisits;
import com.evmonitor.application.mail.MailSendLog.RecentSend;
import com.evmonitor.application.mail.MailSendLog.SendStats;
import com.evmonitor.application.mail.MailType;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.testutil.AbstractPostgresIT;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Echtes Postgres: Fremdschlüssel mit ON DELETE CASCADE und die Unterabfrage auf app_user prüft H2 nicht.
 * Andere Tests verschicken auch Mails (EmailService ist echt), deshalb zählen die Fälle Differenzen
 * oder filtern auf eigene Adressen.
 */
class JdbcMailSendLogPostgresIT extends AbstractPostgresIT {

    @Autowired MailSendLog log;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void record_linksTheUserByEmailIgnoringCase_andKeepsNoAddressForStrangers() {
        User user = userRepository.save(TestDataBuilder.createTestUser(uniqueEmail()));
        String stranger = uniqueEmail();

        log.record(MailType.ONBOARDING_REMINDER, user.getEmail().toUpperCase(), true);
        log.record(MailType.ONBOARDING_REMINDER, stranger, false);

        List<RecentSend> recent = log.recent(MailType.ONBOARDING_REMINDER, 50);
        assertThat(recent).anySatisfy(r -> {
            assertThat(r.userId()).isEqualTo(user.getId());
            assertThat(r.email()).isEqualTo(user.getEmail());
            assertThat(r.sent()).isTrue();
        });
        assertThat(recent).noneMatch(r -> stranger.equals(r.email()));
        assertThat(recent).anySatisfy(r -> {
            assertThat(r.userId()).isNull();
            assertThat(r.sent()).isFalse();
        });
    }

    @Test
    void recent_isNewestFirst_andLimited() {
        User user = userRepository.save(TestDataBuilder.createTestUser(uniqueEmail()));
        for (int i = 0; i < 3; i++) log.record(MailType.TRIAL_ENDING, user.getEmail(), true);

        List<RecentSend> recent = log.recent(MailType.TRIAL_ENDING, 2);

        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).sentAt()).isAfterOrEqualTo(recent.get(1).sentAt());
    }

    @Test
    void sendStats_countsSentAndFailedInTheWindow() {
        SendStats before = statsFor(MailType.AUTOSYNC_SATISFACTION);
        String email = uniqueEmail();
        log.record(MailType.AUTOSYNC_SATISFACTION, email, true);
        log.record(MailType.AUTOSYNC_SATISFACTION, email, true);
        log.record(MailType.AUTOSYNC_SATISFACTION, email, false);
        jdbc.update("INSERT INTO mail_send_log (mail_type, sent, sent_at) VALUES (?, TRUE, now() - interval '40 days')",
                MailType.AUTOSYNC_SATISFACTION.key());

        SendStats after = statsFor(MailType.AUTOSYNC_SATISFACTION);

        assertThat(after.sent() - before.sent()).isEqualTo(2);
        assertThat(after.failed() - before.failed()).isEqualTo(1);
        assertThat(after.lastSentAt()).isNotNull();
    }

    @Test
    void deleteOlderThan_removesOnlyOldRows() {
        String key = MailType.EUDA_HANDOVER.key();
        jdbc.update("INSERT INTO mail_send_log (mail_type, sent, sent_at) VALUES (?, TRUE, now() - interval '400 days')", key);
        log.record(MailType.EUDA_HANDOVER, uniqueEmail(), true);

        log.deleteOlderThan(LocalDateTime.now().minusDays(365));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_send_log WHERE mail_type = ? AND sent_at < now() - interval '365 days'",
                Integer.class, key)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_send_log WHERE mail_type = ?", Integer.class, key)).isPositive();
    }

    @Test
    void deletingTheUser_deletesTheirLog() {
        User user = userRepository.save(TestDataBuilder.createTestUser(uniqueEmail()));
        log.record(MailType.RE_ENGAGEMENT, user.getEmail(), true);

        jdbc.update("DELETE FROM app_user WHERE id = ?", user.getId());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_send_log WHERE user_id = ?", Integer.class, user.getId())).isZero();
    }

    @Test
    void campaignNumbers_comeFromLifecycleRowsOfPlausible() {
        LocalDate day = LocalDate.of(2031, 1, 15);
        String campaign = "it-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO plausible_source_daily (stat_date, source, utm_medium, utm_campaign, visitors, visits) VALUES (?, 'email', 'lifecycle', ?, 4, 5)", day, campaign);
        jdbc.update("INSERT INTO plausible_source_daily (stat_date, source, utm_medium, utm_campaign, visitors, visits) VALUES (?, 'email', 'other', ?, 9, 9)", day, campaign);
        jdbc.update("INSERT INTO plausible_source_goal_daily (stat_date, source, utm_medium, utm_campaign, goal, visitors) VALUES (?, 'email', 'lifecycle', ?, 'log_created', 2)", day, campaign);

        List<CampaignVisits> visits = log.campaignVisits(day.minusDays(1));
        List<CampaignGoal> goals = log.campaignGoals(day.minusDays(1));

        assertThat(visits).filteredOn(v -> v.campaign().equals(campaign)).singleElement()
                .satisfies(v -> assertThat(v.visits()).isEqualTo(5));
        assertThat(goals).filteredOn(g -> g.campaign().equals(campaign)).singleElement()
                .satisfies(g -> {
                    assertThat(g.goal()).isEqualTo("log_created");
                    assertThat(g.visitors()).isEqualTo(2);
                });
        assertThat(log.campaignVisits(day.plusDays(1))).noneMatch(v -> v.campaign().equals(campaign));
    }

    private SendStats statsFor(MailType type) {
        return log.sendStats(LocalDateTime.now().minusDays(30)).stream()
                .filter(s -> s.mailType().equals(type.key())).findFirst()
                .orElse(new SendStats(type.key(), 0, 0, null));
    }

    private static String uniqueEmail() {
        return "mail-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.com";
    }
}
