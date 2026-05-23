package com.evmonitor.application.imports.xpeng;

import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.xpeng.VinUtils;
import com.evmonitor.infrastructure.persistence.xpeng.XpengConnection;
import com.evmonitor.infrastructure.persistence.xpeng.XpengConnectionRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class XpengOutboundMailService {

    static final String XPENG_DATA_PRIVACY_EMAIL = "data-privacy@xiaopeng.com";
    static final String TOKEN_PREFIX = "[token:";
    static final String TOKEN_SUFFIX = "]";

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final XpengConnectionRepository connectionRepo;

    @Value("${app.mail.from:noreply@ev-monitor.net}")
    private String fromAddress;

    @Value("${xpeng.reply-to-inbox:xpeng-inbox@ev-monitor.net}")
    private String replyToInbox;

    @Transactional
    public void sendDataRequest(XpengConnection conn) {
        User user = userRepository.findById(conn.getUserId())
                .orElseThrow(() -> new IllegalStateException("User nicht gefunden: " + conn.getUserId()));

        String ccAddress = conn.getXpengEmail() != null ? conn.getXpengEmail() : user.getEmail();
        String subject = buildSubject(conn);

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(XPENG_DATA_PRIVACY_EMAIL);
            helper.setReplyTo(replyToInbox);
            helper.setCc(ccAddress);
            helper.setSubject(subject);
            helper.setText(buildBody(conn.getVin()), false);
            mailSender.send(msg);
        } catch (Exception e) {
            throw new RuntimeException("XPeng DA-Anfrage konnte nicht gesendet werden: " + e.getMessage(), e);
        }

        conn.setLastRequestSentAt(LocalDateTime.now());
        connectionRepo.save(conn);
        log.info("XPeng DA-Anfrage gesendet: connection={} vin={} cc={}",
                conn.getId(), VinUtils.mask(conn.getVin()), ccAddress);
    }

    static String buildSubject(XpengConnection conn) {
        return "Request for Telematics Data - VIN " + conn.getVin()
                + " (EU Data Act) " + TOKEN_PREFIX + conn.getRoutingToken() + TOKEN_SUFFIX;
    }

    private static String buildBody(String vin) {
        return """
                Dear XPENG Data Protection Team,

                Pursuant to Article 4 of the EU Data Act (Regulation (EU) 2023/2854), \
                I am requesting access to the telematics data of my vehicle \
                for the most recent 30-day period available.

                VIN: %s

                Please provide the data in the Excel format used for similar requests. \
                If the file is encrypted, kindly send the password in a separate email \
                as a reply to this message.

                This request is submitted on behalf of the vehicle owner by EV Monitor \
                (https://ev-monitor.net), acting as authorized data recipient pursuant to \
                Art. 5 EU Data Act, based on explicit user consent.

                Thank you and best regards,
                EV Monitor - https://ev-monitor.net""".formatted(vin);
    }
}
