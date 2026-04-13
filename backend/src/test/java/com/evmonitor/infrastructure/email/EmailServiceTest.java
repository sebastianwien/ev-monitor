package com.evmonitor.infrastructure.email;

import com.evmonitor.infrastructure.security.JwtService;
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

}
