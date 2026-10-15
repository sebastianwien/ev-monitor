package com.evmonitor.application;

import com.evmonitor.application.vehiclespec.MissingVehicleRequestCommand;
import com.evmonitor.domain.SubscriptionTier;
import com.evmonitor.domain.vehiclespec.MissingVehicleRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the purchase-celebration path in AdminAlertService - the internal
 * "yay, a new subscription!" motivation mail to the founders. It is a fire-and-forget
 * side channel: no configured recipients means silent skip, and a mail-send failure
 * must never bubble up into the Stripe webhook that triggered it.
 */
@ExtendWith(MockitoExtension.class)
class AdminAlertServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private AdminAlertService service;

    private AdminAlertService withRecipients(String recipients) {
        AdminAlertService s = new AdminAlertService(mailSender);
        ReflectionTestUtils.setField(s, "fromAddress", "noreply@ev-monitor.net");
        ReflectionTestUtils.setField(s, "purchaseRecipients", recipients);
        return s;
    }

    @Test
    void withRecipients_sendsMailToAll() {
        service = withRecipients("a@ev-monitor.net,b@ev-monitor.net,c@ev-monitor.net");

        service.sendPurchaseCelebration(SubscriptionTier.AUTOSYNC_LIVE, false);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("a@ev-monitor.net", "b@ev-monitor.net", "c@ev-monitor.net");
        assertThat(msg.getFrom()).isEqualTo("noreply@ev-monitor.net");
        // The purchased tier must be visible somewhere in subject or body.
        assertThat(msg.getSubject() + " " + msg.getText()).contains("AutoSync Live");
    }

    @Test
    void commaSeparatedRecipients_areTrimmedAndBlanksDropped() {
        service = withRecipients(" a@ev-monitor.net ,, b@ev-monitor.net ,");

        service.sendPurchaseCelebration(SubscriptionTier.SUPPORTER, false);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("a@ev-monitor.net", "b@ev-monitor.net");
    }

    @Test
    void trialFlag_isReflectedInMail() {
        service = withRecipients("a@ev-monitor.net");

        service.sendPurchaseCelebration(SubscriptionTier.AUTOSYNC, true);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat((msg.getSubject() + " " + msg.getText()).toLowerCase()).contains("trial");
    }

    @Test
    void noRecipientsConfigured_skipsSilently() {
        service = withRecipients("");

        service.sendPurchaseCelebration(SubscriptionTier.AUTOSYNC, false);

        verifyNoInteractions(mailSender);
    }

    @Test
    void blankRecipients_skipSilently() {
        service = withRecipients("   ");

        service.sendPurchaseCelebration(SubscriptionTier.AUTOSYNC, false);

        verifyNoInteractions(mailSender);
    }

    @Test
    void mailSendFailure_isSwallowed_neverBreaksTheWebhook() {
        service = withRecipients("a@ev-monitor.net");
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> service.sendPurchaseCelebration(SubscriptionTier.AUTOSYNC, false))
                .doesNotThrowAnyException();
    }

    // --- trial converted (trial ran through into a real, paying subscription) -----------

    @Test
    void trialConverted_withRecipients_sendsMailToAll() {
        service = withRecipients("a@ev-monitor.net,b@ev-monitor.net");

        service.sendTrialConverted(SubscriptionTier.AUTOSYNC_LIVE);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("a@ev-monitor.net", "b@ev-monitor.net");
        assertThat(msg.getSubject() + " " + msg.getText()).contains("AutoSync Live");
    }

    @Test
    void trialConverted_noRecipients_skipsSilently() {
        service = withRecipients("");

        service.sendTrialConverted(SubscriptionTier.AUTOSYNC);

        verifyNoInteractions(mailSender);
    }

    // ── Fahrzeug-Meldung ─────────────────────────────────────────────────────

    private AdminAlertService withVehicleRequestRecipient(String recipient) {
        AdminAlertService s = new AdminAlertService(mailSender);
        ReflectionTestUtils.setField(s, "fromAddress", "noreply@ev-monitor.net");
        ReflectionTestUtils.setField(s, "vehicleRequestAdminEmail", recipient);
        ReflectionTestUtils.setField(s, "baseUrl", "https://ev-monitor.net");
        return s;
    }

    private static MissingVehicleRequest vehicleRequest(String text) {
        return MissingVehicleRequest.create(UUID.randomUUID(),
                new MissingVehicleRequestCommand(text, "max@example.com",
                        "POLESTAR", "POLESTAR_2", new BigDecimal("78"), 2022).toDraft());
    }

    @Test
    void vehicleRequest_escapesFreeText_linksToTab_andKeepsContactOut() {
        service = withVehicleRequestRecipient("admin@ev-monitor.net");

        service.sendMissingVehicleRequest(vehicleRequest("<script>alert(1)</script> Polestar"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("admin@ev-monitor.net");
        assertThat(msg.getSubject()).isEqualTo("EV Monitor - Neue Fahrzeug-Meldung");
        assertThat(msg.getText())
                .contains("&lt;script&gt;")
                .doesNotContain("<script>")
                .contains("POLESTAR", "POLESTAR_2", "78", "2022")
                .contains("https://ev-monitor.net/admin?tab=specs")
                .doesNotContain("max@example.com");
    }

    @Test
    void vehicleRequest_withoutRecipient_isSkipped() {
        service = withVehicleRequestRecipient("");
        service.sendMissingVehicleRequest(vehicleRequest("Lucid Air"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void vehicleRequest_mailSendFailure_isSwallowed() {
        service = withVehicleRequestRecipient("admin@ev-monitor.net");
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> service.sendMissingVehicleRequest(vehicleRequest("Lucid Air")))
                .doesNotThrowAnyException();
    }

    @Test
    void trialConverted_mailSendFailure_isSwallowed() {
        service = withRecipients("a@ev-monitor.net");
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> service.sendTrialConverted(SubscriptionTier.AUTOSYNC))
                .doesNotThrowAnyException();
    }
}
