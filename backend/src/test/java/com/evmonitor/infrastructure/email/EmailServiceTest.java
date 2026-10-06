package com.evmonitor.infrastructure.email;

import com.evmonitor.infrastructure.security.JwtService;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private JwtService jwtService;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, jwtService);
        ReflectionTestUtils.setField(emailService, "baseUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@ev-monitor.net");
    }

    private MimeMessage createRealMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Test
    void sendVerificationEmail_withDeLocale_sendsDeutschSubject() throws Exception {
        MimeMessage mimeMessage = createRealMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendVerificationEmail("user@example.com", "token123", "de");

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("EV Monitor - E-Mail bestätigen");
    }

    @Test
    void sendVerificationEmail_withEnLocale_sendsEnglishSubject() throws Exception {
        MimeMessage mimeMessage = createRealMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendVerificationEmail("user@example.com", "token123", "en");

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("EV Monitor - Confirm your email");
    }

    @Test
    void sendVerificationEmail_withNullLocale_fallsBackToDeutsch() throws Exception {
        MimeMessage mimeMessage = createRealMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendVerificationEmail("user@example.com", "token123", null);

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("EV Monitor - E-Mail bestätigen");
    }

    @Test
    void sendPasswordResetEmail_withEnLocale_sendsEnglishSubject() throws Exception {
        MimeMessage mimeMessage = createRealMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendPasswordResetEmail("user@example.com", "token123", "en");

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("EV Monitor - Reset your password");
    }

    @Test
    void sendPasswordResetEmail_withDeLocale_sendsDeutschSubject() throws Exception {
        MimeMessage mimeMessage = createRealMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendPasswordResetEmail("user@example.com", "token123", "de");

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("EV Monitor - Passwort zurücksetzen");
    }

    @Test
    void sendOnboardingReminderEmail_withEnLocale_sendsEnglishSubject() throws Exception {
        MimeMessage mimeMessage = createRealMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(jwtService.generateUnsubscribeToken(anyString())).thenReturn("jwt-token");

        emailService.sendOnboardingReminderEmail("user@example.com", "testuser", "en");

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("EV Monitor - Ready for your first charging log?");
    }

    @Test
    void sendReEngagementEmail_withEnLocale_sendsEnglishSubject() throws Exception {
        MimeMessage mimeMessage = createRealMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(jwtService.generateUnsubscribeToken(anyString())).thenReturn("jwt-token");

        emailService.sendReEngagementEmail("user@example.com", "testuser", "en");

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("A quick note from me");
    }

    @Test
    void lifecycleEmails_tagLinksWithUtmCampaign_forPlausibleAttribution() throws Exception {
        when(jwtService.generateUnsubscribeToken(anyString())).thenReturn("jwt-token");

        assertThat(sendAndGetHtml(() -> emailService.sendReEngagementEmail("u@example.com", "u", "de")))
                .contains("http://localhost:5173/dashboard?utm_source=email&amp;utm_medium=lifecycle&amp;utm_campaign=re-engagement")
                .contains("http://localhost:5173/umfrage/why-away?utm_source=email&amp;utm_medium=lifecycle&amp;utm_campaign=re-engagement");
        assertThat(sendAndGetHtml(() -> emailService.sendOnboardingReminderEmail("u@example.com", "u", "de")))
                .contains("utm_campaign=onboarding-reminder");
        assertThat(sendAndGetHtml(() -> emailService.sendAutoSyncDormantEmail("u@example.com", "u", "de")))
                .contains("utm_campaign=autosync-dormant");
        assertThat(sendAndGetHtml(() -> emailService.sendAutoSyncSatisfactionEmail("u@example.com", "u", "de")))
                .contains("/umfrage/autosync-satisfaction?utm_source=email&amp;utm_medium=lifecycle&amp;utm_campaign=autosync-satisfaction");
    }

    @Test
    void lifecycleEmails_leaveUnsubscribeLinkUntagged() throws Exception {
        when(jwtService.generateUnsubscribeToken(anyString())).thenReturn("jwt-token");

        assertThat(sendAndGetHtml(() -> emailService.sendReEngagementEmail("u@example.com", "u", "de")))
                .contains("/api/unsubscribe?token=jwt-token\"");
    }

    @Test
    void transactionalEmails_carryNoUtmParameters() throws Exception {
        assertThat(sendAndGetHtml(() -> emailService.sendVerificationEmail("u@example.com", "t", "de")))
                .doesNotContain("utm_");
        assertThat(sendAndGetHtml(() -> emailService.sendPasswordResetEmail("u@example.com", "t", "de")))
                .doesNotContain("utm_");
    }

    private String sendAndGetHtml(Runnable send) throws Exception {
        MimeMessage mimeMessage = createRealMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        send.run();
        mimeMessage.saveChanges();
        return findHtml(mimeMessage);
    }

    private static String findHtml(Part part) throws Exception {
        if (part.isMimeType("text/html")) return (String) part.getContent();
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String html = findHtml(multipart.getBodyPart(i));
                if (html != null) return html;
            }
        }
        return null;
    }


    @Test
    void monthlyRecap_carriesOneClickUnsubscribeHeadersAndPersonalSender() throws Exception {
        MimeMessage mimeMessage = createRealMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(jwtService.generateUnsubscribeToken("ihle@example.com")).thenReturn("jwt-token");
        ReflectionTestUtils.setField(emailService, "personalReplyTo", "sebastian@ev-monitor.net");

        emailService.sendMonthlyRecapEmail(recap());

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("Dein August mit dem Model 3");
        assertThat(mimeMessage.getHeader("List-Unsubscribe", null))
                .isEqualTo("<http://localhost:5173/api/unsubscribe?token=jwt-token>");
        assertThat(mimeMessage.getHeader("List-Unsubscribe-Post", null)).isEqualTo("List-Unsubscribe=One-Click");
        assertThat(((jakarta.mail.internet.InternetAddress) mimeMessage.getFrom()[0]).getPersonal())
                .isEqualTo("Sebastian von ev-monitor");
        assertThat(mimeMessage.getReplyTo()[0].toString()).isEqualTo("sebastian@ev-monitor.net");
    }

    @Test
    void monthlyRecap_withoutPersonalReplyTo_repliesGoToSender() throws Exception {
        MimeMessage mimeMessage = createRealMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(jwtService.generateUnsubscribeToken(anyString())).thenReturn("jwt-token");

        emailService.sendMonthlyRecapEmail(recap());

        assertThat(mimeMessage.getHeader("Reply-To", null)).isNull();
    }

    private static final java.util.UUID CAR_ID = java.util.UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void monthlyRecap_pricelessHintLinksToPriceBackfillForThatCar() throws Exception {
        when(jwtService.generateUnsubscribeToken(anyString())).thenReturn("jwt-token");
        com.evmonitor.application.recap.MonthlyRecap r = recap();
        com.evmonitor.application.recap.MonthlyRecap withHint = new com.evmonitor.application.recap.MonthlyRecap(
                r.userId(), r.carId(), r.email(), r.username(), r.locale(), r.month(), r.carName(), r.charges(),
                r.acCharges(), r.dcCharges(), r.kwh(), r.costEur(), r.distanceKm(), r.consumptionKwhPer100km(),
                r.homeSharePercent(), r.fuelCostEur(), r.fuelPricePerLiter(),
                new com.evmonitor.application.recap.MonthlyRecap.PricelessHint(2, java.time.LocalDateTime.of(2026, 8, 3, 9, 0), null),
                null);

        assertThat(sendAndGetHtml(() -> emailService.sendMonthlyRecapEmail(withHint)))
                .contains("http://localhost:5173/logs?utm_source=email&amp;utm_medium=lifecycle&amp;utm_campaign=monthly-recap"
                        + "&amp;car=11111111-2222-3333-4444-555555555555&amp;nachtragen=preis");
    }

    private com.evmonitor.application.recap.MonthlyRecap recap() {
        return new com.evmonitor.application.recap.MonthlyRecap(java.util.UUID.randomUUID(), CAR_ID, "ihle@example.com", "Ihle", "de",
                java.time.YearMonth.of(2026, 8), "Model 3", 12, 2, 10, new java.math.BigDecimal("351.4"),
                new java.math.BigDecimal("147.37"), new java.math.BigDecimal("1777"), null, null,
                new java.math.BigDecimal("217.69"), new java.math.BigDecimal("1.75"), null, null);
    }
}
