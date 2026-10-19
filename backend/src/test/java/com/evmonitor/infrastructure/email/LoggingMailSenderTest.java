package com.evmonitor.infrastructure.email;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class LoggingMailSenderTest {

    private final JavaMailSender delegate = mock(JavaMailSender.class);
    private final LoggingMailSender sender = new LoggingMailSender(delegate);

    private MimeMessage mime(String to, String subject) throws Exception {
        MimeMessage m = new MimeMessage(Session.getInstance(new Properties()));
        m.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        m.setSubject(subject, "UTF-8");
        return m;
    }

    @Test
    void logsRecipientAndSubjectAfterSuccessfulMimeSend(CapturedOutput out) throws Exception {
        MimeMessage m = mime("anna@example.org", "Dein VW-Data-Act-AutoSync-Test endet am 18.10.");

        sender.send(m);

        verify(delegate).send(m);
        assertThat(out).contains("[MAIL] gesendet")
                .contains("to=[anna@example.org]")
                .contains("subject=\"Dein VW-Data-Act-AutoSync-Test endet am 18.10.\"");
    }

    @Test
    void logsSimpleMessage(CapturedOutput out) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo("admin@example.org");
        msg.setSubject("EV Monitor - Alert");

        sender.send(msg);

        verify(delegate).send(msg);
        assertThat(out).contains("[MAIL] gesendet").contains("to=[admin@example.org]").contains("EV Monitor - Alert");
    }

    @Test
    void logsFailureAndRethrows(CapturedOutput out) throws Exception {
        MimeMessage m = mime("kurt@example.org", "Hallo");
        doThrow(new MailSendException("SMTP down")).when(delegate).send(m);

        assertThatThrownBy(() -> sender.send(m)).isInstanceOf(MailSendException.class);

        assertThat(out).contains("[MAIL] fehlgeschlagen").contains("to=[kurt@example.org]").contains("SMTP down");
        assertThat(out).doesNotContain("[MAIL] gesendet");
    }
}
