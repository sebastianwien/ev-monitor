package com.evmonitor.application.imports.xpeng;

import com.evmonitor.infrastructure.persistence.xpeng.XpengConnection;
import com.evmonitor.infrastructure.persistence.xpeng.XpengConnectionRepository;
import com.evmonitor.infrastructure.persistence.xpeng.XpengImportJob;
import com.evmonitor.infrastructure.persistence.xpeng.XpengReceivedMail;
import com.evmonitor.infrastructure.persistence.xpeng.XpengReceivedMailRepository;
import jakarta.mail.*;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.FlagTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty("xpeng.imap.host")
public class XpengImapPoller {

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("\\[token:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\]",
                    Pattern.CASE_INSENSITIVE);

    private final XpengConnectionRepository connectionRepo;
    private final XpengReceivedMailRepository receivedMailRepo;
    private final XpengImportService importService;

    @Value("${xpeng.imap.host}")
    private String host;

    @Value("${xpeng.imap.port:993}")
    private int port;

    @Value("${xpeng.imap.user}")
    private String imapUser;

    @Value("${xpeng.imap.password}")
    private String imapPassword;

    @Scheduled(fixedDelayString = "${xpeng.imap.poll-interval-ms:1800000}") // default 30 Minuten
    public void poll() {
        Properties props = new Properties();
        props.put("mail.imap.ssl.enable", "true");
        props.put("mail.imap.host", host);
        props.put("mail.imap.port", String.valueOf(port));
        props.put("mail.imap.connectiontimeout", "10000");
        props.put("mail.imap.timeout", "15000");

        Session session = Session.getInstance(props);
        try (Store store = session.getStore("imap")) {
            store.connect(host, imapUser, imapPassword);
            try (Folder inbox = store.getFolder("INBOX")) {
                inbox.open(Folder.READ_WRITE);
                Message[] unseen = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                log.debug("XPeng IMAP: {} ungelesene Nachrichten gefunden", unseen.length);
                for (Message msg : unseen) {
                    safeProcess(msg);
                }
            }
        } catch (Exception e) {
            log.error("XPeng IMAP-Poll fehlgeschlagen", e);
        }
    }

    private void safeProcess(Message msg) {
        try {
            processMessage(msg);
        } catch (Exception e) {
            log.error("XPeng IMAP: Fehler bei Nachrichtenverarbeitung", e);
            // Mail als gelesen markieren um Endlosschleife zu verhindern
            try { msg.setFlag(Flags.Flag.SEEN, true); } catch (Exception ignored) {}
        }
    }

    private void processMessage(Message msg) throws Exception {
        String messageId = getHeader(msg, "Message-ID");
        String subject = msg.getSubject();

        // Dedup: bereits verarbeitete Mail ueberspringen
        if (messageId != null && receivedMailRepo.existsByMessageId(messageId)) {
            log.debug("XPeng IMAP: Mail {} bereits verarbeitet, ueberspringe", messageId);
            msg.setFlag(Flags.Flag.SEEN, true);
            return;
        }

        UUID routingToken = extractRoutingTokenFromSubject(subject);
        if (routingToken == null) {
            log.debug("XPeng IMAP: kein Routing-Token in Subject '{}', ignoriere", subject);
            msg.setFlag(Flags.Flag.SEEN, true);
            return;
        }

        Optional<XpengConnection> connOpt = connectionRepo.findByRoutingToken(routingToken);
        if (connOpt.isEmpty()) {
            log.warn("XPeng IMAP: unbekannter Routing-Token {} in Mail '{}'", routingToken, subject);
            msg.setFlag(Flags.Flag.SEEN, true);
            return;
        }
        XpengConnection conn = connOpt.get();
        if (!conn.isActive()) {
            log.warn("XPeng IMAP: Connection {} ist nicht mehr aktiv, ignoriere Mail", conn.getId());
            msg.setFlag(Flags.Flag.SEEN, true);
            return;
        }

        List<AttachmentPart> xlsxParts = extractXlsxAttachments(msg);
        if (xlsxParts.isEmpty()) {
            log.info("XPeng IMAP: Mail {} hat keinen XLSX-Anhang, ueberspringe Import", messageId);
            // Trotzdem als verarbeitet merken damit wir sie nicht jedes Mal pruefen
            saveReceivedMailRecord(conn.getId(), messageId, null, null);
            msg.setFlag(Flags.Flag.SEEN, true);
            return;
        }

        for (AttachmentPart part : xlsxParts) {
            Path tmp = Files.createTempFile("xpeng-imap-", ".xlsx");
            try {
                try (InputStream in = part.inputStream()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                try (InputStream uploadStream = Files.newInputStream(tmp)) {
                    XpengImportJob job = importService.uploadXlsx(
                            conn.getUserId(), conn.getCarId(), uploadStream,
                            null, null, "xpeng-imap-poller");
                    saveReceivedMailRecord(conn.getId(), messageId, part.filename(), job.getId());
                    log.info("XPeng IMAP: Import-Job {} fuer connection={} gestartet (Datei: {})",
                            job.getId(), conn.getId(), part.filename());
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        }

        msg.setFlag(Flags.Flag.SEEN, true);
    }

    static UUID extractRoutingTokenFromSubject(String subject) {
        if (subject == null) return null;
        Matcher m = TOKEN_PATTERN.matcher(subject);
        if (!m.find()) return null;
        try {
            return UUID.fromString(m.group(1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String getHeader(Message msg, String name) throws MessagingException {
        String[] headers = msg.getHeader(name);
        return (headers != null && headers.length > 0) ? headers[0] : null;
    }

    private static List<AttachmentPart> extractXlsxAttachments(Part part) throws MessagingException, IOException {
        List<AttachmentPart> result = new ArrayList<>();
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                result.addAll(extractXlsxAttachments(mp.getBodyPart(i)));
            }
        } else {
            String filename = part.getFileName();
            if (filename != null) {
                try { filename = MimeUtility.decodeText(filename); } catch (Exception ignored) {}
                if (filename.toLowerCase().endsWith(".xlsx")) {
                    result.add(new AttachmentPart(filename, part));
                }
            }
        }
        return result;
    }

    private void saveReceivedMailRecord(UUID connectionId, String messageId,
                                        String attachmentName, UUID jobId) {
        XpengReceivedMail record = XpengReceivedMail.builder()
                .connectionId(connectionId)
                .messageId(messageId != null ? messageId : "unknown-" + UUID.randomUUID())
                .receivedAt(LocalDateTime.now())
                .attachmentName(attachmentName)
                .jobId(jobId)
                .build();
        receivedMailRepo.save(record);
    }

    private record AttachmentPart(String filename, Part part) {
        InputStream inputStream() throws MessagingException, IOException {
            return part.getInputStream();
        }
    }
}
