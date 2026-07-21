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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
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

    private static final Pattern VIN_PATTERN =
            Pattern.compile("\\bVIN\\s+([A-HJ-NPR-Z0-9]{17})\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("(?:password|passwort|pw|code)\\s*:\\s*([\\S]{4,30})",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern ALIBABA_GDOWN_PATTERN = Pattern.compile(
            "var downloadUrl\\s*=\\s*\"([^\"]+)\"\\s*\\+\\s*encodeURIComponent\\(\"([^\"]+)\"\\)",
            Pattern.DOTALL);

    private static final Pattern DOWNLOAD_LINK_PATTERN =
            Pattern.compile("href=[\"']([^\"']*mail\\.xiaopeng\\.com/alimail/openLinks/downloadMimeMetaDiskBigAttach[^\"']*)[\"']",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern XLSX_FILENAME_PATTERN =
            Pattern.compile("([\\w\\-. ]+\\.xlsx)", Pattern.CASE_INSENSITIVE);

    private static final long MAX_DOWNLOAD_BYTES = 110L * 1024 * 1024; // 110 MB

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

    @Value("${xpeng.imap.folders:INBOX,INBOX.spambucket}")
    private List<String> imapFolders;

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
            // Pass 1: alle Ordner nach Passwörtern absuchen und speichern
            for (String folderName : imapFolders) {
                pollFolder(store, folderName.trim(), true);
            }
            // Pass 2: XLSX und Download-Links mit jetzt verfügbaren Passwörtern verarbeiten
            for (String folderName : imapFolders) {
                pollFolder(store, folderName.trim(), false);
            }
        } catch (Exception e) {
            log.error("XPeng IMAP-Poll fehlgeschlagen", e);
        }
    }

    private void pollFolder(Store store, String folderName, boolean passwordsOnly) {
        try (Folder folder = store.getFolder(folderName)) {
            folder.open(Folder.READ_WRITE);
            Message[] unseen = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            log.info("XPeng IMAP: Ordner '{}' - {} ungelesene Nachricht(en) [{}]",
                    folderName, unseen.length, passwordsOnly ? "Pass 1: Passwort-Scan" : "Pass 2: Daten-Import");
            for (Message msg : unseen) {
                safeProcess(msg, passwordsOnly);
            }
        } catch (Exception e) {
            log.error("XPeng IMAP: Fehler beim Scannen von Ordner '{}'", folderName, e);
        }
    }

    private void safeProcess(Message msg, boolean passwordsOnly) {
        String subject = "<unbekannt>";
        try {
            subject = msg.getSubject();
            processMessage(msg, passwordsOnly);
        } catch (Exception e) {
            log.error("XPeng IMAP: Fehler bei Verarbeitung von Mail '{}': {}", subject, e.getMessage(), e);
            // Mail als gelesen markieren um Endlosschleife zu verhindern
            try { msg.setFlag(Flags.Flag.SEEN, true); } catch (Exception ignored) {}
        }
    }

    private void processMessage(Message msg, boolean passwordsOnly) throws Exception {
        String messageId = getHeader(msg, "Message-ID");
        String subject = msg.getSubject();
        String from = msg.getFrom() != null && msg.getFrom().length > 0
                ? msg.getFrom()[0].toString() : "<unbekannt>";

        log.info("XPeng IMAP: Verarbeite Mail von '{}' - Subject: '{}'", from, subject);

        // Dedup: bereits verarbeitete Mail ueberspringen
        if (messageId != null && receivedMailRepo.existsByMessageId(messageId)) {
            log.info("XPeng IMAP: Mail {} bereits verarbeitet (messageId bekannt), ueberspringe", messageId);
            msg.setFlag(Flags.Flag.SEEN, true);
            return;
        }

        UUID routingToken = extractRoutingTokenFromSubject(subject);
        Optional<XpengConnection> connOpt;
        if (routingToken != null) {
            connOpt = connectionRepo.findByRoutingToken(routingToken);
            if (connOpt.isEmpty()) {
                log.warn("XPeng IMAP: unbekannter Routing-Token {} in Mail '{}'", routingToken, subject);
                msg.setFlag(Flags.Flag.SEEN, true);
                return;
            }
        } else {
            String vin = extractVinFromSubject(subject);
            if (vin == null) {
                log.warn("XPeng IMAP: kein Routing-Token und keine VIN in Subject '{}' von '{}' - ignoriere Mail", subject, from);
                msg.setFlag(Flags.Flag.SEEN, true);
                return;
            }
            connOpt = connectionRepo.findByVin(vin);
            if (connOpt.isEmpty()) {
                log.warn("XPeng IMAP: VIN-Fallback - keine Connection fuer VIN {} in Mail '{}'", vin, subject);
                msg.setFlag(Flags.Flag.SEEN, true);
                return;
            }
            log.info("XPeng IMAP: Routing-Token nicht gefunden, VIN-Fallback fuer VIN {} (connection={})", vin, connOpt.get().getId());
        }
        XpengConnection conn = connOpt.get();
        if (!conn.isActive()) {
            log.warn("XPeng IMAP: Connection {} ist nicht mehr aktiv, ignoriere Mail", conn.getId());
            msg.setFlag(Flags.Flag.SEEN, true);
            return;
        }

        List<AttachmentPart> xlsxParts = extractXlsxAttachments(msg);
        if (!xlsxParts.isEmpty()) {
            if (passwordsOnly) return; // Pass 2 verarbeitet XLSX
            processXlsxAttachments(msg, conn, messageId, from, xlsxParts);
            return;
        }

        // Kein MIME-Anhang: Download-Links im HTML-Body suchen
        String htmlBody = extractHtmlBody(msg);
        if (htmlBody != null) {
            List<String> downloadLinks = extractXpengDownloadLinks(htmlBody);
            if (!downloadLinks.isEmpty()) {
                if (passwordsOnly) return; // Pass 2 verarbeitet Download-Links
                processDownloadLinks(msg, conn, messageId, from, htmlBody, downloadLinks);
                return;
            }
        }

        // Weder Anhang noch Download-Link: Passwort-Mail oder unbekannter Inhalt
        String vinSuffix = vinSuffix(conn.getVin());
        String body = getPlainTextBody(msg);
        String password = extractPassword(body, vinSuffix);
        if (password != null) {
            log.warn("XPeng IMAP: Passwort-Mail fuer connection={} VIN={} erkannt und gespeichert (Passwort-Laenge: {})",
                    conn.getId(), com.evmonitor.domain.xpeng.VinUtils.mask(conn.getVin()), password.length());
        } else {
            log.warn("XPeng IMAP: Mail von '{}' hat keinen XLSX-Anhang und kein erkennbares Passwort - Body-Laenge: {}",
                    from, body != null ? body.length() : 0);
        }
        saveReceivedMailRecord(conn.getId(), messageId, null, null, password);
        msg.setFlag(Flags.Flag.SEEN, true);
    }

    private void processXlsxAttachments(Message msg, XpengConnection conn, String messageId,
                                         String from, List<AttachmentPart> xlsxParts) throws Exception {
        log.info("XPeng IMAP: {} XLSX-Anhang/Anhaenge gefunden in Mail von '{}'", xlsxParts.size(), from);

        String password = lookupStoredPassword(conn.getId());
        boolean allEnqueued = true;

        for (AttachmentPart part : xlsxParts) {
            Path tmp = Files.createTempFile("xpeng-imap-", ".xlsx");
            try {
                try (InputStream in = part.inputStream()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                // XPeng liefert XLSX und Passwort in getrennten Mails - trifft die XLSX zuerst ein,
                // liegt hier noch kein (oder ein veraltetes) Passwort. Dann NICHT importieren und die
                // Mail UNSEEN + ohne Record lassen, damit ein spaeterer Poll sie mit dem dann
                // vorhandenen Passwort erneut versucht (Dedup laeuft ueber existsByMessageId).
                if (!com.evmonitor.domain.xpeng.XpengExcelStreamingParser.canDecrypt(tmp, password)) {
                    log.warn("XPeng IMAP: XLSX '{}' fuer connection={} noch nicht entschluesselbar "
                            + "(Passwort {}) - Mail bleibt UNSEEN fuer Retry beim naechsten Poll",
                            part.filename(), conn.getId(), password == null ? "fehlt" : "passt nicht");
                    allEnqueued = false;
                    continue;
                }
                try (InputStream uploadStream = Files.newInputStream(tmp)) {
                    XpengImportJob job = importService.uploadXlsx(
                            conn.getUserId(), conn.getCarId(), uploadStream,
                            password, null, "xpeng-imap-poller");
                    saveReceivedMailRecord(conn.getId(), messageId, part.filename(), job.getId(), null);
                    log.info("XPeng IMAP: Import-Job {} fuer connection={} gestartet (Datei: {})",
                            job.getId(), conn.getId(), part.filename());
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        }

        if (allEnqueued) {
            msg.setFlag(Flags.Flag.SEEN, true);
        }
    }

    private void processDownloadLinks(Message msg, XpengConnection conn, String messageId,
                                      String from, String htmlBody, List<String> downloadLinks) throws MessagingException {
        log.info("XPeng IMAP: {} Download-Link(s) in Mail von '{}' gefunden", downloadLinks.size(), from);

        String password = lookupStoredPassword(conn.getId());
        boolean allSucceeded = true;

        for (String url : downloadLinks) {
            String filename = extractFilenameFromLinkContext(htmlBody, url);
            log.info("XPeng IMAP: Starte Download von '{}' (Datei: {})", url, filename);
            Path tmp = null;
            try {
                tmp = downloadToTempFile(url);
                long fileSize = Files.size(tmp);
                log.info("XPeng IMAP: Download abgeschlossen - {} Bytes (Datei: {})", fileSize, filename);
                try (InputStream uploadStream = Files.newInputStream(tmp)) {
                    XpengImportJob job = importService.uploadXlsx(
                            conn.getUserId(), conn.getCarId(), uploadStream,
                            password, null, "xpeng-imap-poller");
                    saveReceivedMailRecord(conn.getId(), messageId, filename, job.getId(), null);
                    log.info("XPeng IMAP: Import-Job {} fuer connection={} gestartet (Download: {})",
                            job.getId(), conn.getId(), filename);
                }
            } catch (Exception e) {
                log.error("XPeng IMAP: Download-Fehler fuer URL '{}' - Mail bleibt ungelesen fuer Retry: {}", url, e.getMessage(), e);
                allSucceeded = false;
            } finally {
                if (tmp != null) {
                    try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
                }
            }
        }

        if (allSucceeded) {
            msg.setFlag(Flags.Flag.SEEN, true);
        } else {
            log.warn("XPeng IMAP: Mindestens ein Download fehlgeschlagen - Mail bleibt UNSEEN, naechster Poll versucht es erneut");
        }
    }

    private String lookupStoredPassword(UUID connectionId) {
        return receivedMailRepo
                .findFirstByConnectionIdAndExtractedPasswordIsNotNullOrderByReceivedAtDesc(connectionId)
                .map(XpengReceivedMail::getExtractedPassword)
                .orElse(null);
    }

    private Path downloadToTempFile(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        // Schritt 1: Landing Page holen (klein, als String) - Alibaba sendet HTML mit JS-Download-Button
        HttpRequest probe = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "EV-Monitor-XPeng-Importer/1.0")
                .GET()
                .build();
        HttpResponse<String> probe_resp = client.send(probe, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (probe_resp.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + probe_resp.statusCode() + " fuer " + url);
        }
        String contentType = probe_resp.headers().firstValue("Content-Type").orElse("");
        String actualUrl = contentType.contains("text/html")
                ? extractAlibabaGdownUrl(probe_resp.body(), url)
                : url;
        if (!actualUrl.equals(url)) {
            log.info("XPeng IMAP: Alibaba Landing Page erkannt - echter Download: {}", actualUrl);
        }
        // Schritt 2: Echte Datei streamen mit Groessenlimit
        HttpRequest download = HttpRequest.newBuilder()
                .uri(URI.create(actualUrl))
                .timeout(Duration.ofSeconds(120))
                .header("User-Agent", "EV-Monitor-XPeng-Importer/1.0")
                .GET()
                .build();
        HttpResponse<InputStream> resp = client.send(download, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + " beim Direktdownload");
        }
        Path tmp = Files.createTempFile("xpeng-download-", ".xlsx");
        try (InputStream body = resp.body();
             var out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[65536];
            long total = 0;
            int n;
            while ((n = body.read(buf)) > 0) {
                total += n;
                if (total > MAX_DOWNLOAD_BYTES) {
                    Files.deleteIfExists(tmp);
                    throw new IllegalStateException("Download zu gross: >" + MAX_DOWNLOAD_BYTES + " Bytes");
                }
                out.write(buf, 0, n);
            }
        }
        return tmp;
    }

    static String extractAlibabaGdownUrl(String html, String originalUrl) {
        Matcher m = ALIBABA_GDOWN_PATTERN.matcher(html);
        if (!m.find()) {
            throw new IllegalStateException("Alibaba Landing Page ohne Download-URL - originalUrl: " + originalUrl);
        }
        String path = m.group(1).replace("\\/", "/");
        String rawItemId = m.group(2).replace("\\/", "/");
        String encodedItemId = URLEncoder.encode(rawItemId, StandardCharsets.UTF_8);
        URI uri = URI.create(originalUrl);
        return uri.getScheme() + "://" + uri.getHost() + path + encodedItemId;
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

    static String extractVinFromSubject(String subject) {
        if (subject == null) return null;
        Matcher m = VIN_PATTERN.matcher(subject);
        if (!m.find()) return null;
        return m.group(1).toUpperCase();
    }

    static List<String> extractXpengDownloadLinks(String html) {
        if (html == null) return List.of();
        List<String> links = new ArrayList<>();
        Matcher m = DOWNLOAD_LINK_PATTERN.matcher(html);
        while (m.find()) {
            links.add(m.group(1).replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">"));
        }
        return links;
    }

    static String extractFilenameFromLinkContext(String html, String linkUrl) {
        if (html == null || linkUrl == null) return "xpeng-download.xlsx";
        // Suche nach dem XLSX-Dateinamen im HTML-Bereich vor dem Link (max. 300 Zeichen)
        int linkIndex = html.indexOf(linkUrl);
        if (linkIndex > 0) {
            int start = Math.max(0, linkIndex - 300);
            String context = html.substring(start, linkIndex);
            Matcher m = XLSX_FILENAME_PATTERN.matcher(context);
            String lastMatch = null;
            while (m.find()) {
                lastMatch = m.group(1).trim();
            }
            if (lastMatch != null) return lastMatch;
        }
        return "xpeng-download.xlsx";
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
                                        String attachmentName, UUID jobId, String extractedPassword) {
        XpengReceivedMail record = XpengReceivedMail.builder()
                .connectionId(connectionId)
                .messageId(messageId != null ? messageId : "unknown-" + UUID.randomUUID())
                .receivedAt(LocalDateTime.now())
                .attachmentName(attachmentName)
                .jobId(jobId)
                .extractedPassword(extractedPassword)
                .build();
        receivedMailRepo.save(record);
    }

    static String extractPassword(String body, String vinSuffix) {
        if (body == null) return null;
        String trimmed = body.strip();
        Matcher m = PASSWORD_PATTERN.matcher(trimmed);
        if (m.find()) return m.group(1);
        if (vinSuffix != null && !vinSuffix.isEmpty()) {
            Pattern vinPattern = Pattern.compile("(\\d{8}" + Pattern.quote(vinSuffix) + ")",
                    Pattern.CASE_INSENSITIVE);
            Matcher vm = vinPattern.matcher(trimmed);
            if (vm.find()) return vm.group(1);
        }
        // Fallback: kurzer Body ohne Leerzeichen = wahrscheinlich reines Passwort-Token
        return trimmed.length() >= 4 && trimmed.length() <= 60 && !trimmed.contains(" ") ? trimmed : null;
    }

    private static String getPlainTextBody(Message msg) {
        try {
            return extractPlainText(msg);
        } catch (Exception e) {
            log.debug("XPeng IMAP: Konnte Mail-Body nicht lesen", e);
        }
        return null;
    }

    private static String extractPlainText(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            return content instanceof String s ? s : null;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String result = extractPlainText(mp.getBodyPart(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    private static String extractHtmlBody(Part part) {
        try {
            return extractHtml(part);
        } catch (Exception e) {
            log.debug("XPeng IMAP: Konnte HTML-Body nicht lesen", e);
        }
        return null;
    }

    private static String extractHtml(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            return content instanceof String s ? s : null;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String result = extractHtml(mp.getBodyPart(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    private static String vinSuffix(String vin) {
        if (vin == null || vin.length() < 4) return null;
        return vin.substring(vin.length() - 4);
    }

    private record AttachmentPart(String filename, Part part) {
        InputStream inputStream() throws MessagingException, IOException {
            return part.getInputStream();
        }
    }
}
