package com.evmonitor.infrastructure.email;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Protokolliert jeden Mailversand (Empfänger, Betreff) - Erfolg wie Fehler. Wird per
 * {@link MailLoggingConfig} um den {@link JavaMailSender} gelegt, damit kein Versandweg
 * am Protokoll vorbeigeht, egal welcher Service sendet.
 */
@Slf4j
public class LoggingMailSender implements JavaMailSender {

    private final JavaMailSender delegate;

    public LoggingMailSender(JavaMailSender delegate) {
        this.delegate = delegate;
    }

    @Override
    public MimeMessage createMimeMessage() {
        return delegate.createMimeMessage();
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
        return delegate.createMimeMessage(contentStream);
    }

    @Override
    public void send(MimeMessage mimeMessage) throws MailException {
        sendLogged(describe(mimeMessage), () -> delegate.send(mimeMessage));
    }

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {
        for (MimeMessage m : mimeMessages) send(m);
    }

    @Override
    public void send(MimeMessagePreparator preparator) throws MailException {
        MimeMessage m = createMimeMessage();
        try {
            preparator.prepare(m);
        } catch (Exception e) {
            throw new org.springframework.mail.MailPreparationException(e);
        }
        send(m);
    }

    @Override
    public void send(MimeMessagePreparator... preparators) throws MailException {
        for (MimeMessagePreparator p : preparators) send(p);
    }

    @Override
    public void send(SimpleMailMessage simpleMessage) throws MailException {
        String to = Arrays.toString(simpleMessage.getTo() == null ? new String[0] : simpleMessage.getTo());
        sendLogged(format(to, simpleMessage.getSubject()), () -> delegate.send(simpleMessage));
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
        for (SimpleMailMessage m : simpleMessages) send(m);
    }

    private void sendLogged(String description, Runnable send) {
        try {
            send.run();
        } catch (RuntimeException e) {
            log.warn("[MAIL] fehlgeschlagen {} error=\"{}\"", description, e.getMessage());
            throw e;
        }
        log.info("[MAIL] gesendet {}", description);
    }

    private static String describe(MimeMessage m) {
        try {
            Address[] recipients = m.getAllRecipients();
            List<String> to = recipients == null ? List.of() : Arrays.stream(recipients).map(Address::toString).toList();
            return format(to.toString(), m.getSubject());
        } catch (MessagingException e) {
            return format("[?]", "?");
        }
    }

    private static String format(String to, String subject) {
        return "to=" + to + " subject=\"" + subject + "\"";
    }
}
