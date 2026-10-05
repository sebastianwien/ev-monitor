package com.evmonitor.application;

import com.evmonitor.application.StripeReportService.TrialFunnel;
import com.evmonitor.infrastructure.persistence.AdminQueryRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Wochenreport an die Gruender: 7-Tage-Fenster bis "jetzt", Vorwoche als Vergleich.
 * Fire-and-forget wie AdminAlertService: keine Empfaenger = kein Versand, Fehler bleiben intern.
 */
@ExtendWith(MockitoExtension.class)
class WeeklyReportServiceTest {

    /** Montag, 05.10.2026 17:00 Europe/Berlin (= 15:00Z). */
    private static final Instant NOW = Instant.parse("2026-10-05T15:00:00Z");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Mock private AdminQueryRepository adminQueryRepository;
    @Mock private StripeReportService stripeReportService;
    @Mock private JavaMailSender mailSender;

    private WeeklyReportService service;

    @BeforeEach
    void setUp() {
        service = new WeeklyReportService(adminQueryRepository, stripeReportService, mailSender,
                Clock.fixed(NOW, BERLIN));
        ReflectionTestUtils.setField(service, "fromAddress", "noreply@ev-monitor.net");
        ReflectionTestUtils.setField(service, "baseUrl", "https://ev-monitor.net");
        lenient().when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new JavaMailSenderImpl().createMimeMessage());
    }

    private void stubCounts() {
        LocalDateTime end = LocalDateTime.ofInstant(NOW, ZoneId.systemDefault());
        LocalDateTime start = end.minusDays(7);
        LocalDateTime prevStart = end.minusDays(14);
        when(adminQueryRepository.countNewUsers(start, end)).thenReturn(12L);
        when(adminQueryRepository.countNewUsers(prevStart, start)).thenReturn(8L);
        when(adminQueryRepository.countNewCars(start, end)).thenReturn(5L);
        when(adminQueryRepository.countNewCars(prevStart, start)).thenReturn(5L);
        when(adminQueryRepository.countNewEvLogs(start, end)).thenReturn(87L);
        when(adminQueryRepository.countNewEvLogs(prevStart, start)).thenReturn(120L);
        when(adminQueryRepository.countNewTrips(start, end)).thenReturn(300L);
        when(adminQueryRepository.countNewTrips(prevStart, start)).thenReturn(0L);
        stubTrials();
    }

    private void stubDbCounts() {
        when(adminQueryRepository.countNewUsers(any(), any())).thenReturn(1L);
        when(adminQueryRepository.countNewCars(any(), any())).thenReturn(1L);
        when(adminQueryRepository.countNewEvLogs(any(), any())).thenReturn(1L);
        when(adminQueryRepository.countNewTrips(any(), any())).thenReturn(1L);
    }

    private void stubTrials() {
        when(stripeReportService.trialFunnel(NOW.minusSeconds(7 * 86400), NOW))
                .thenReturn(new TrialFunnel(true, 3, 2, 1));
        when(stripeReportService.trialFunnel(NOW.minusSeconds(14 * 86400), NOW.minusSeconds(7 * 86400)))
                .thenReturn(new TrialFunnel(true, 1, 1, 0));
    }

    @Test
    void buildStats_computesWindowAndDeltas() {
        stubCounts();

        WeeklyReportStats stats = service.buildStats();

        assertThat(stats.weekLabel()).isEqualTo("KW 41");
        assertThat(stats.periodLabel()).isEqualTo("28.09. bis 05.10.2026");
        assertThat(stats.newUsers().current()).isEqualTo(12);
        assertThat(stats.newUsers().delta()).isEqualTo(4);
        assertThat(stats.newUsers().trend()).isEqualTo(WeeklyReportStats.Trend.UP);
        assertThat(stats.newCars().trend()).isEqualTo(WeeklyReportStats.Trend.FLAT);
        assertThat(stats.newEvLogs().trend()).isEqualTo(WeeklyReportStats.Trend.DOWN);
        assertThat(stats.trialsStarted().current()).isEqualTo(3);
        assertThat(stats.trialsEnded().current()).isEqualTo(2);
        assertThat(stats.trialsConverted().current()).isEqualTo(1);
        assertThat(stats.conversionRateLabel()).isEqualTo("50 %");
    }

    @Test
    void subject_summarisesHeadlineNumbers() {
        stubCounts();

        String subject = service.subjectFor(service.buildStats());

        assertThat(subject).isEqualTo("Wochenreport KW 41: +12 Nutzer, +3 Trials, +87 Ladevorgänge");
    }

    @Test
    void render_containsAllNumbersAndNoUnresolvedPlaceholders() {
        stubCounts();

        String html = service.render(service.buildStats());

        assertThat(html).contains("KW 41", "28.09. bis 05.10.2026", ">12<", ">87<", ">300<", "50 %",
                "https://ev-monitor.net/admin");
        assertThat(html).doesNotContain("{{");
    }

    @Test
    void render_withoutStripe_marksTrialsAsUnavailable() {
        stubDbCounts();
        when(stripeReportService.trialFunnel(any(), any())).thenReturn(TrialFunnel.empty());

        String html = service.render(service.buildStats());

        assertThat(html).contains("Stripe nicht konfiguriert");
    }

    @Test
    void send_withoutRecipients_skipsSilently() {
        ReflectionTestUtils.setField(service, "recipients", "");

        service.send();

        verifyNoInteractions(mailSender);
    }

    @Test
    void send_withRecipients_sendsHtmlMailToAll() throws Exception {
        stubCounts();
        ReflectionTestUtils.setField(service, "recipients", "a@ev-monitor.net, b@ev-monitor.net");

        service.send();

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_mailFailure_doesNotPropagate() {
        stubCounts();
        ReflectionTestUtils.setField(service, "recipients", "a@ev-monitor.net");
        doThrow(new RuntimeException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        service.send();
    }
}
