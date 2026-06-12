package com.evmonitor.application.imports.xpeng;

import com.evmonitor.infrastructure.persistence.xpeng.XpengConnection;
import com.evmonitor.infrastructure.persistence.xpeng.XpengConnectionRepository;
import com.evmonitor.infrastructure.persistence.xpeng.XpengReceivedMail;
import com.evmonitor.infrastructure.persistence.xpeng.XpengReceivedMailRepository;
import jakarta.mail.Flags;
import jakarta.mail.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class XpengImapPollerTest {

    @Mock XpengConnectionRepository connectionRepo;
    @Mock XpengReceivedMailRepository receivedMailRepo;
    @Mock XpengImportService importService;
    @InjectMocks XpengImapPoller poller;

    // --- extractRoutingTokenFromSubject ---

    @Test
    void extractsValidTokenFromSubject() {
        UUID token = UUID.randomUUID();
        String subject = "Re: Request for Telematics Data - VIN L1XX (EU Data Act) [token:" + token + "]";
        assertEquals(token, XpengImapPoller.extractRoutingTokenFromSubject(subject));
    }

    @Test
    void extractionIsCaseInsensitive() {
        UUID token = UUID.randomUUID();
        String subject = "Re: test [TOKEN:" + token.toString().toUpperCase() + "]";
        assertEquals(token, XpengImapPoller.extractRoutingTokenFromSubject(subject));
    }

    @Test
    void returnsNullForSubjectWithoutToken() {
        assertNull(XpengImapPoller.extractRoutingTokenFromSubject("Fwd: Normal reply without token"));
    }

    @Test
    void returnsNullForNullSubject() {
        assertNull(XpengImapPoller.extractRoutingTokenFromSubject(null));
    }

    @Test
    void returnsNullForMalformedToken() {
        assertNull(XpengImapPoller.extractRoutingTokenFromSubject("Re: Test [token:not-a-uuid]"));
    }

    // --- processMessage: dedup ---

    @Test
    void skipsDuplicateMessageId() throws Exception {
        String msgId = "<dup@test.com>";
        UUID token = UUID.randomUUID();
        Message msg = mockMessage(msgId, "Re: XPeng [token:" + token + "]");
        when(receivedMailRepo.existsByMessageId(msgId)).thenReturn(true);

        invokeProcessMessage(msg);

        verify(msg).setFlag(Flags.Flag.SEEN, true);
        verify(connectionRepo, never()).findByRoutingToken(any());
        verify(importService, never()).uploadXlsx(any(), any(), any(), any(), any(), any());
    }

    // --- processMessage: token not found, no VIN fallback either ---

    @Test
    void skipsMailWithNoRoutingTokenAndNoVin() throws Exception {
        Message msg = mockMessage("<id@test.com>", "Re: Normal mail without token");
        when(receivedMailRepo.existsByMessageId(any())).thenReturn(false);

        invokeProcessMessage(msg);

        verify(msg).setFlag(Flags.Flag.SEEN, true);
        verify(connectionRepo, never()).findByRoutingToken(any());
        verify(connectionRepo, never()).findByVin(any());
    }

    // --- processMessage: unknown token ---

    @Test
    void skipsMailWithUnknownRoutingToken() throws Exception {
        UUID token = UUID.randomUUID();
        Message msg = mockMessage("<id@test.com>", "Re: XPeng [token:" + token + "]");
        when(receivedMailRepo.existsByMessageId(any())).thenReturn(false);
        when(connectionRepo.findByRoutingToken(token)).thenReturn(Optional.empty());

        invokeProcessMessage(msg);

        verify(msg).setFlag(Flags.Flag.SEEN, true);
        verify(importService, never()).uploadXlsx(any(), any(), any(), any(), any(), any());
    }

    // --- processMessage: inactive connection ---

    @Test
    void skipsMailForRevokedConnection() throws Exception {
        UUID token = UUID.randomUUID();
        Message msg = mockMessage("<id@test.com>", "Re: XPeng [token:" + token + "]");
        XpengConnection revokedConn = buildConn(token, true);
        when(receivedMailRepo.existsByMessageId(any())).thenReturn(false);
        when(connectionRepo.findByRoutingToken(token)).thenReturn(Optional.of(revokedConn));

        invokeProcessMessage(msg);

        verify(msg).setFlag(Flags.Flag.SEEN, true);
        verify(importService, never()).uploadXlsx(any(), any(), any(), any(), any(), any());
    }

    // --- processMessage: no XLSX attachment ---

    @Test
    void savesRecordAndMarksSeenWhenNoXlsxAttachment() throws Exception {
        UUID token = UUID.randomUUID();
        Message msg = mockMessage("<id@test.com>", "Re: XPeng [token:" + token + "]");
        XpengConnection conn = buildConn(token, false);
        when(receivedMailRepo.existsByMessageId(any())).thenReturn(false);
        when(connectionRepo.findByRoutingToken(token)).thenReturn(Optional.of(conn));
        when(receivedMailRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // msg.isMimeType("multipart/*") returns false by default (Mockito), msg.getFileName() returns null
        // → extractXlsxAttachments returns empty list

        invokeProcessMessage(msg);

        ArgumentCaptor<XpengReceivedMail> captor = ArgumentCaptor.forClass(XpengReceivedMail.class);
        verify(receivedMailRepo).save(captor.capture());
        assertNull(captor.getValue().getJobId(), "Kein Import-Job wenn kein XLSX");
        verify(msg).setFlag(Flags.Flag.SEEN, true);
        verify(importService, never()).uploadXlsx(any(), any(), any(), any(), any(), any());
    }

    // --- extractPassword ---

    @Test
    void extractsPasswordViaPasswordKeyword() {
        assertEquals("abc123", XpengImapPoller.extractPassword("Please find your password: abc123 for the file.", null));
    }

    @Test
    void extractsPasswordViaPwKeyword() {
        assertEquals("Xp3ng!9", XpengImapPoller.extractPassword("PW: Xp3ng!9", null));
    }

    @Test
    void extractsPasswordViaPasswortKeyword() {
        assertEquals("geheim99", XpengImapPoller.extractPassword("Das Passwort: geheim99 fuer die Datei.", null));
    }

    @Test
    void returnsBodyAsFallbackWhenShortAndNoKeyword() {
        String body = "Hunter42!";
        assertEquals(body, XpengImapPoller.extractPassword(body, null));
    }

    @Test
    void returnsNullForLongBodyWithNoMatch() {
        String longBody = "a".repeat(201);
        assertNull(XpengImapPoller.extractPassword(longBody, null));
    }

    @Test
    void returnsNullForNullBody() {
        assertNull(XpengImapPoller.extractPassword(null, null));
    }

    @Test
    void extractsPasswordViaVinSuffixWithoutKeyword() {
        // XPeng pattern: YYYYMMDD + last 4 VIN chars, no keyword prefix
        assertEquals("202512237070", XpengImapPoller.extractPassword(
                "Bitte verwenden Sie 202512237070 zum Oeffnen der Datei.", "7070"));
    }

    @Test
    void vinSuffixNotMatchedWhenVinSuffixIsNull() {
        // Body must be long enough that the short-body fallback doesn't apply
        String body = "Bitte verwenden Sie 202512237070 zum Oeffnen der Datei. " + "x".repeat(160);
        assertNull(XpengImapPoller.extractPassword(body, null));
    }

    @Test
    void keywordMatchTakesPriorityOverVinSuffix() {
        // keyword match returns "MyPass", not the date+vin string
        assertEquals("MyPass", XpengImapPoller.extractPassword(
                "Password: MyPass - also 202512237070 here", "7070"));
    }

    // Blocker 3a: "Code:" Keyword (echtes XPeng-Format)
    @Test
    void extractsCodeKeywordAsPassword() {
        assertEquals("202605287070", XpengImapPoller.extractPassword("Code: 202605287070", null));
    }

    // Blocker 3b: "password will be sent" darf NICHT matchen
    @Test
    void doesNotExtractPasswordFromPasswordWillPhrase() {
        String body = "The password will be sent to you in a separate email.";
        assertNull(XpengImapPoller.extractPassword(body, null));
    }

    // Blocker 3c: Body-Fallback darf nicht bei Sätzen (Leerzeichen) triggern
    @Test
    void bodyFallbackIgnoresTextWithSpaces() {
        String body = "Dear customer";
        assertNull(XpengImapPoller.extractPassword(body, null));
    }

    // Blocker 3d: Body-Fallback greift bei tokenartigem Kurztext ohne Leerzeichen
    @Test
    void bodyFallbackWorksForTokenWithoutSpaces() {
        assertEquals("Abc12345", XpengImapPoller.extractPassword("Abc12345", null));
    }

    // --- processMessage: password extracted from nested multipart ---

    @Test
    void savesExtractedPasswordFromNestedMultipart() throws Exception {
        UUID token = UUID.randomUUID();
        UUID connId = UUID.randomUUID();

        // multipart/mixed containing multipart/alternative containing text/plain
        jakarta.mail.internet.MimeBodyPart textPart = mock(jakarta.mail.internet.MimeBodyPart.class);
        lenient().when(textPart.isMimeType("text/plain")).thenReturn(true);
        lenient().when(textPart.isMimeType("multipart/*")).thenReturn(false);
        lenient().when(textPart.getContent()).thenReturn("PW: nested99");

        jakarta.mail.Multipart innerMp = mock(jakarta.mail.Multipart.class);
        when(innerMp.getCount()).thenReturn(1);
        when(innerMp.getBodyPart(0)).thenReturn(textPart);

        jakarta.mail.internet.MimeBodyPart altPart = mock(jakarta.mail.internet.MimeBodyPart.class);
        lenient().when(altPart.isMimeType("text/plain")).thenReturn(false);
        lenient().when(altPart.isMimeType("multipart/*")).thenReturn(true);
        lenient().when(altPart.getContent()).thenReturn(innerMp);

        jakarta.mail.Multipart outerMp = mock(jakarta.mail.Multipart.class);
        when(outerMp.getCount()).thenReturn(1);
        when(outerMp.getBodyPart(0)).thenReturn(altPart);

        Message msg = mockMessage("<nested@test.com>", "Re: XPeng [token:" + token + "]");
        lenient().when(msg.isMimeType("multipart/*")).thenReturn(true);
        lenient().when(msg.isMimeType("text/plain")).thenReturn(false);
        lenient().when(msg.getContent()).thenReturn(outerMp);

        XpengConnection conn = XpengConnection.builder()
                .id(connId).userId(UUID.randomUUID()).carId(UUID.randomUUID())
                .vin("L1NN12345678ABCDE").routingToken(token).autoSyncEnabled(true)
                .consentGrantedAt(LocalDateTime.now()).totalImportsCount(0)
                .consentVersion(XpengConnection.AUTOSYNC_CONSENT_VERSION).build();

        when(receivedMailRepo.existsByMessageId(any())).thenReturn(false);
        when(connectionRepo.findByRoutingToken(token)).thenReturn(Optional.of(conn));
        when(receivedMailRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        invokeProcessMessage(msg);

        ArgumentCaptor<XpengReceivedMail> captor = ArgumentCaptor.forClass(XpengReceivedMail.class);
        verify(receivedMailRepo).save(captor.capture());
        assertEquals("nested99", captor.getValue().getExtractedPassword());
    }

    // --- processMessage: no-XLSX saves extracted password ---

    @Test
    void savesExtractedPasswordWhenNoXlsxAttachment() throws Exception {
        UUID token = UUID.randomUUID();
        Message msg = mockMessage("<pw@test.com>", "Re: XPeng [token:" + token + "]");
        lenient().when(msg.isMimeType("text/plain")).thenReturn(true);
        lenient().when(msg.getContent()).thenReturn("Password: secret99");
        XpengConnection conn = buildConn(token, false);
        when(receivedMailRepo.existsByMessageId(any())).thenReturn(false);
        when(connectionRepo.findByRoutingToken(token)).thenReturn(Optional.of(conn));
        when(receivedMailRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        invokeProcessMessage(msg);

        ArgumentCaptor<XpengReceivedMail> captor = ArgumentCaptor.forClass(XpengReceivedMail.class);
        verify(receivedMailRepo).save(captor.capture());
        assertEquals("secret99", captor.getValue().getExtractedPassword());
        verify(importService, never()).uploadXlsx(any(), any(), any(), any(), any(), any());
    }

    // --- processMessage: XLSX uses password from DB ---

    @Test
    void passesStoredPasswordToUploadWhenXlsxPresent() throws Exception {
        UUID token = UUID.randomUUID();
        UUID connId = UUID.randomUUID();

        // Build a message with an XLSX multipart attachment
        jakarta.mail.internet.MimeBodyPart xlsxPart = mock(jakarta.mail.internet.MimeBodyPart.class);
        when(xlsxPart.isMimeType("multipart/*")).thenReturn(false);
        when(xlsxPart.getFileName()).thenReturn("data.xlsx");
        when(xlsxPart.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[0]));

        jakarta.mail.Multipart mp = mock(jakarta.mail.Multipart.class);
        when(mp.getCount()).thenReturn(1);
        when(mp.getBodyPart(0)).thenReturn(xlsxPart);

        Message msg = mockMessage("<xlsx@test.com>", "Re: XPeng [token:" + token + "]");
        when(msg.isMimeType("multipart/*")).thenReturn(true);
        when(msg.getContent()).thenReturn(mp);

        XpengConnection conn = XpengConnection.builder()
                .id(connId).userId(UUID.randomUUID()).carId(UUID.randomUUID())
                .vin("L1NN12345678ABCDE").routingToken(token).autoSyncEnabled(true)
                .consentGrantedAt(LocalDateTime.now()).totalImportsCount(0)
                .consentVersion(XpengConnection.AUTOSYNC_CONSENT_VERSION).build();

        XpengReceivedMail pwRecord = XpengReceivedMail.builder()
                .connectionId(connId).messageId("<pw@prev.com>")
                .receivedAt(LocalDateTime.now().minusMinutes(5))
                .extractedPassword("topSecret1").build();

        when(receivedMailRepo.existsByMessageId(any())).thenReturn(false);
        when(connectionRepo.findByRoutingToken(token)).thenReturn(Optional.of(conn));
        when(receivedMailRepo.findFirstByConnectionIdAndExtractedPasswordIsNotNullOrderByReceivedAtDesc(connId))
                .thenReturn(Optional.of(pwRecord));
        when(importService.uploadXlsx(any(), any(), any(), any(), any(), any()))
                .thenReturn(new com.evmonitor.infrastructure.persistence.xpeng.XpengImportJob());
        when(receivedMailRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        invokeProcessMessage(msg);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(importService).uploadXlsx(any(), any(), any(), passwordCaptor.capture(), any(), any());
        assertEquals("topSecret1", passwordCaptor.getValue());
    }

    // --- extractXpengDownloadLinks ---

    @Test
    void extractsDownloadLinkFromHtml() {
        String html = "<html><body>"
                + "超大附件列表  igor_DA-Request_5.21-6.3.xlsx  [27.3MB]<br/>"
                + "进入下载页面  <a href=\"https://mail.xiaopeng.com/alimail/openLinks/downloadMimeMetaDiskBigAttach?id=netdiskid%3Av001%3Afile%3Aabc123\">点击下载</a>"
                + "</body></html>";

        List<String> links = XpengImapPoller.extractXpengDownloadLinks(html);

        assertEquals(1, links.size());
        assertTrue(links.get(0).contains("downloadMimeMetaDiskBigAttach"));
    }

    @Test
    void extractsDownloadLinkWithSingleQuotes() {
        String html = "<a href='https://mail.xiaopeng.com/alimail/openLinks/downloadMimeMetaDiskBigAttach?id=xyz'>下载</a>";

        List<String> links = XpengImapPoller.extractXpengDownloadLinks(html);

        assertEquals(1, links.size());
        assertTrue(links.get(0).contains("downloadMimeMetaDiskBigAttach"));
    }

    @Test
    void returnsEmptyListWhenNoDownloadLink() {
        String html = "<html><body>Normal mail content without any download links.</body></html>";

        List<String> links = XpengImapPoller.extractXpengDownloadLinks(html);

        assertTrue(links.isEmpty());
    }

    @Test
    void returnsEmptyListForNullHtml() {
        List<String> links = XpengImapPoller.extractXpengDownloadLinks(null);

        assertTrue(links.isEmpty());
    }

    // Blocker 5: &amp; in href-Attributen wird zu & unescaped
    @Test
    void unescapesHtmlEntitiesInDownloadLink() {
        String url = "https://mail.xiaopeng.com/alimail/openLinks/downloadMimeMetaDiskBigAttach?id=abc&amp;foo=bar";
        String html = "<a href=\"" + url + "\">下载</a>";

        List<String> links = XpengImapPoller.extractXpengDownloadLinks(html);

        assertEquals(1, links.size());
        assertEquals("https://mail.xiaopeng.com/alimail/openLinks/downloadMimeMetaDiskBigAttach?id=abc&foo=bar",
                links.get(0));
    }

    @Test
    void extractsMultipleDownloadLinks() {
        String html = "<html><body>"
                + "<a href=\"https://mail.xiaopeng.com/alimail/openLinks/downloadMimeMetaDiskBigAttach?id=file1\">file1</a>"
                + "<a href=\"https://mail.xiaopeng.com/alimail/openLinks/downloadMimeMetaDiskBigAttach?id=file2\">file2</a>"
                + "</body></html>";

        List<String> links = XpengImapPoller.extractXpengDownloadLinks(html);

        assertEquals(2, links.size());
    }

    // --- extractFilenameFromLinkContext ---

    @Test
    void extractsFilenameFromHtmlContextBeforeLink() {
        String html = "igor_DA-Request_5.21-6.3.xlsx  [27.3MB]<br/>"
                + "<a href=\"https://mail.xiaopeng.com/alimail/openLinks/downloadMimeMetaDiskBigAttach?id=abc\">下载</a>";

        String filename = XpengImapPoller.extractFilenameFromLinkContext(html,
                "https://mail.xiaopeng.com/alimail/openLinks/downloadMimeMetaDiskBigAttach?id=abc");

        assertEquals("igor_DA-Request_5.21-6.3.xlsx", filename);
    }

    @Test
    void fallsBackToDefaultFilenameWhenNoXlsxInContext() {
        String html = "<a href=\"https://mail.xiaopeng.com/alimail/openLinks/downloadMimeMetaDiskBigAttach?id=abc\">下载</a>";

        String filename = XpengImapPoller.extractFilenameFromLinkContext(html,
                "https://mail.xiaopeng.com/alimail/openLinks/downloadMimeMetaDiskBigAttach?id=abc");

        assertEquals("xpeng-download.xlsx", filename);
    }

    // --- imap.folders config ---

    @Test
    void folderListDefaultContainsBothFolders() {
        ReflectionTestUtils.setField(poller, "imapFolders", List.of("INBOX", "INBOX.spambucket"));
        List<String> folders = getImapFolders();
        assertEquals(2, folders.size());
        assertTrue(folders.contains("INBOX"));
        assertTrue(folders.contains("INBOX.spambucket"));
    }

    @Test
    void folderListCanBeConfiguredToSingleFolder() {
        ReflectionTestUtils.setField(poller, "imapFolders", List.of("INBOX"));
        List<String> folders = getImapFolders();
        assertEquals(1, folders.size());
        assertEquals("INBOX", folders.get(0));
    }

    @SuppressWarnings("unchecked")
    private List<String> getImapFolders() {
        return (List<String>) ReflectionTestUtils.getField(poller, "imapFolders");
    }

    // --- Fix 4: Download-Fehler = kein Record + kein SEEN (Retry) ---

    @Test
    void doesNotSaveRecordOrMarkSeenWhenDownloadFails() throws Exception {
        UUID token = UUID.randomUUID();
        String html = "<a href=\"https://mail.xiaopeng.com/alimail/openLinks/downloadMimeMetaDiskBigAttach?id=unreachable\">下载</a>";
        Message msg = mockMessage("<dl-fail@test.com>", "Re: XPeng [token:" + token + "]");
        // No XLSX attachment (isMimeType multipart/* false), HTML body with download link
        lenient().when(msg.isMimeType("multipart/*")).thenReturn(false);
        lenient().when(msg.isMimeType("text/html")).thenReturn(true);
        lenient().when(msg.getContent()).thenReturn(html);
        when(msg.getFrom()).thenReturn(new jakarta.mail.Address[]{});
        XpengConnection conn = buildConn(token, false);
        when(receivedMailRepo.existsByMessageId(any())).thenReturn(false);
        when(connectionRepo.findByRoutingToken(token)).thenReturn(Optional.of(conn));

        // downloadToTempFile will throw (unreachable host) → allSucceeded = false
        invokeProcessMessage(msg);

        verify(receivedMailRepo, never()).save(any());
        verify(msg, never()).setFlag(Flags.Flag.SEEN, true);
    }

    // --- extractAlibabaGdownUrl ---

    @Test
    void extractsGdownUrlFromAlibabaLandingPage() {
        String html = "<script>var downloadUrl = \"\\/attachment\\/gdown\\/Olafto_DA.xlsx?itemid=\" + encodeURIComponent(\"netdiskid:v001:file:abc;def/ghi\");</script>";
        String result = XpengImapPoller.extractAlibabaGdownUrl(html, "https://mail.xiaopeng.com/alimail/openLinks/downloadMimeMetaDiskBigAttach?id=xxx");
        assertTrue(result.startsWith("https://mail.xiaopeng.com/attachment/gdown/Olafto_DA.xlsx?itemid="));
        assertTrue(result.contains("netdiskid"));
        assertTrue(result.contains("%3A")); // colon encoded
        assertTrue(result.contains("%3B")); // semicolon encoded
    }

    @Test
    void extractsGdownUrlFromRealXpengHtml() {
        String html = """
                <script type="text/javascript" nonce="">
                    function downloadFile() {
                        var downloadUrl = "\\/attachment\\/gdown\\/Olafto_DA-Request_5.12-6.12.xlsx?itemid=" + encodeURIComponent("netdiskid:v001:file:DzzzzzzNqZq;abc123\\/def\\/ghi");
                        location.href = downloadUrl;
                    }
                </script>
                """;
        String result = XpengImapPoller.extractAlibabaGdownUrl(html, "https://mail.xiaopeng.com/alimail/openLinks/downloadMimeMetaDiskBigAttach?id=old");
        assertEquals("https://mail.xiaopeng.com/attachment/gdown/Olafto_DA-Request_5.12-6.12.xlsx?itemid="
                + "netdiskid%3Av001%3Afile%3ADzzzzzzNqZq%3Babc123%2Fdef%2Fghi", result);
    }

    @Test
    void throwsWhenNoDownloadUrlInLandingPage() {
        String html = "<html><body><p>Login required</p></body></html>";
        assertThrows(IllegalStateException.class, () ->
                XpengImapPoller.extractAlibabaGdownUrl(html, "https://mail.xiaopeng.com/alimail/openLinks/downloadMimeMetaDiskBigAttach?id=x"));
    }

    // --- extractVinFromSubject ---

    @Test
    void extractsVinFromSubjectWithVinPrefix() {
        String subject = "Re: Daten-Anfrage VIN LSVAU2180N2190941 (EU Data Act)";
        assertEquals("LSVAU2180N2190941", XpengImapPoller.extractVinFromSubject(subject));
    }

    @Test
    void extractsVinFromSubjectCaseInsensitive() {
        String subject = "Re: vin lsvau2180n2190941 test";
        assertEquals("LSVAU2180N2190941", XpengImapPoller.extractVinFromSubject(subject));
    }

    @Test
    void extractsVinWithMultipleSpacesBetweenPrefixAndVin() {
        String subject = "Re: VIN  LSVAU2180N2190941 (request)";
        assertEquals("LSVAU2180N2190941", XpengImapPoller.extractVinFromSubject(subject));
    }

    @Test
    void returnsNullWhenNoVinInSubject() {
        assertNull(XpengImapPoller.extractVinFromSubject("Re: Normal reply without VIN"));
    }

    @Test
    void returnsNullForNullSubjectInVinExtraction() {
        assertNull(XpengImapPoller.extractVinFromSubject(null));
    }

    @Test
    void doesNotMatchVinWithForbiddenCharsIoQ() {
        // VINs duerfen kein I, O, Q enthalten
        assertNull(XpengImapPoller.extractVinFromSubject("Re: VIN LSVAU2I80N2190941"));
        assertNull(XpengImapPoller.extractVinFromSubject("Re: VIN LSVAU2O80N2190941"));
        assertNull(XpengImapPoller.extractVinFromSubject("Re: VIN LSVAU2Q80N2190941"));
    }

    // --- processMessage: VIN Fallback ---

    @Test
    void usesVinFallbackWhenNoRoutingToken() throws Exception {
        String vin = "LSVAU2180N2190941";
        Message msg = mockMessage("<vin-fallback@test.com>",
                "Re: Daten-Anfrage VIN " + vin + " (EU Data Act)");
        XpengConnection conn = buildConnWithVin(vin);
        when(receivedMailRepo.existsByMessageId(any())).thenReturn(false);
        when(connectionRepo.findByVin(vin)).thenReturn(Optional.of(conn));
        when(receivedMailRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        invokeProcessMessage(msg);

        verify(connectionRepo).findByVin(vin);
        verify(connectionRepo, never()).findByRoutingToken(any());
        verify(msg).setFlag(Flags.Flag.SEEN, true);
    }

    @Test
    void vinFallbackSkipsInactiveConnection() throws Exception {
        String vin = "LSVAU2180N2190941";
        Message msg = mockMessage("<vin-revoked@test.com>",
                "Re: VIN " + vin + " Antwort");
        XpengConnection revokedConn = buildConnWithVin(vin, true);
        when(receivedMailRepo.existsByMessageId(any())).thenReturn(false);
        when(connectionRepo.findByVin(vin)).thenReturn(Optional.of(revokedConn));

        invokeProcessMessage(msg);

        verify(msg).setFlag(Flags.Flag.SEEN, true);
        verify(importService, never()).uploadXlsx(any(), any(), any(), any(), any(), any());
    }

    @Test
    void vinFallbackSkipsWhenNoConnectionFound() throws Exception {
        String vin = "LSVAU2180N2190941";
        Message msg = mockMessage("<vin-unknown@test.com>",
                "Re: VIN " + vin + " Antwort");
        when(receivedMailRepo.existsByMessageId(any())).thenReturn(false);
        when(connectionRepo.findByVin(vin)).thenReturn(Optional.empty());

        invokeProcessMessage(msg);

        verify(msg).setFlag(Flags.Flag.SEEN, true);
        verify(importService, never()).uploadXlsx(any(), any(), any(), any(), any(), any());
    }

    @Test
    void skipsMailWithNeitherTokenNorVin() throws Exception {
        Message msg = mockMessage("<no-token-no-vin@test.com>", "Re: Normal mail without token or VIN");
        when(receivedMailRepo.existsByMessageId(any())).thenReturn(false);

        invokeProcessMessage(msg);

        verify(msg).setFlag(Flags.Flag.SEEN, true);
        verify(connectionRepo, never()).findByRoutingToken(any());
        verify(connectionRepo, never()).findByVin(any());
    }

    // --- Two-Pass: Passwort-Scan vor Daten-Import ---

    @Test
    void passwordPassSkipsXlsxMail() throws Exception {
        UUID token = UUID.randomUUID();
        jakarta.mail.internet.MimeBodyPart xlsxPart = mock(jakarta.mail.internet.MimeBodyPart.class);
        when(xlsxPart.isMimeType("multipart/*")).thenReturn(false);
        when(xlsxPart.getFileName()).thenReturn("data.xlsx");
        jakarta.mail.Multipart mp = mock(jakarta.mail.Multipart.class);
        when(mp.getCount()).thenReturn(1);
        when(mp.getBodyPart(0)).thenReturn(xlsxPart);
        Message msg = mockMessage("<xlsx-pw-pass@test.com>", "Re: XPeng [token:" + token + "]");
        when(msg.isMimeType("multipart/*")).thenReturn(true);
        when(msg.getContent()).thenReturn(mp);
        when(receivedMailRepo.existsByMessageId(any())).thenReturn(false);
        when(connectionRepo.findByRoutingToken(token)).thenReturn(Optional.of(buildConn(token, false)));

        invokeProcessMessage(msg, true);

        verify(msg, never()).setFlag(Flags.Flag.SEEN, true);
        verify(importService, never()).uploadXlsx(any(), any(), any(), any(), any(), any());
        verify(receivedMailRepo, never()).save(any());
    }

    @Test
    void passwordPassSkipsDownloadLinkMail() throws Exception {
        UUID token = UUID.randomUUID();
        String html = "<a href=\"https://mail.xiaopeng.com/alimail/openLinks/downloadMimeMetaDiskBigAttach?id=abc\">下载</a>";
        Message msg = mockMessage("<dl-pw-pass@test.com>", "Re: XPeng [token:" + token + "]");
        lenient().when(msg.isMimeType("multipart/*")).thenReturn(false);
        lenient().when(msg.isMimeType("text/html")).thenReturn(true);
        lenient().when(msg.getContent()).thenReturn(html);
        when(msg.getFrom()).thenReturn(new jakarta.mail.Address[]{});
        when(receivedMailRepo.existsByMessageId(any())).thenReturn(false);
        when(connectionRepo.findByRoutingToken(token)).thenReturn(Optional.of(buildConn(token, false)));

        invokeProcessMessage(msg, true);

        verify(msg, never()).setFlag(Flags.Flag.SEEN, true);
        verify(receivedMailRepo, never()).save(any());
    }

    @Test
    void passwordPassProcessesPasswordMail() throws Exception {
        UUID token = UUID.randomUUID();
        Message msg = mockMessage("<pw-pass@test.com>", "Re: XPeng [token:" + token + "]");
        lenient().when(msg.isMimeType("text/plain")).thenReturn(true);
        lenient().when(msg.getContent()).thenReturn("Password: secret99");
        when(receivedMailRepo.existsByMessageId(any())).thenReturn(false);
        when(connectionRepo.findByRoutingToken(token)).thenReturn(Optional.of(buildConn(token, false)));
        when(receivedMailRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        invokeProcessMessage(msg, true);

        ArgumentCaptor<XpengReceivedMail> captor = ArgumentCaptor.forClass(XpengReceivedMail.class);
        verify(receivedMailRepo).save(captor.capture());
        assertEquals("secret99", captor.getValue().getExtractedPassword());
        verify(msg).setFlag(Flags.Flag.SEEN, true);
    }

    // --- helpers ---

    private static Message mockMessage(String messageId, String subject) throws Exception {
        Message msg = mock(Message.class);
        when(msg.getHeader("Message-ID")).thenReturn(new String[]{messageId});
        when(msg.getSubject()).thenReturn(subject);
        return msg;
    }

    private static XpengConnection buildConnWithVin(String vin) {
        return buildConnWithVin(vin, false);
    }

    private static XpengConnection buildConnWithVin(String vin, boolean revoked) {
        XpengConnection.XpengConnectionBuilder b = XpengConnection.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .carId(UUID.randomUUID())
                .vin(vin)
                .routingToken(UUID.randomUUID())
                .autoSyncEnabled(true)
                .consentGrantedAt(LocalDateTime.now())
                .totalImportsCount(0)
                .consentVersion(XpengConnection.AUTOSYNC_CONSENT_VERSION);
        if (revoked) b.consentRevokedAt(LocalDateTime.now());
        return b.build();
    }

    private static XpengConnection buildConn(UUID routingToken, boolean revoked) {
        XpengConnection.XpengConnectionBuilder b = XpengConnection.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .carId(UUID.randomUUID())
                .vin("L1NN12345678ABCDE")
                .routingToken(routingToken)
                .autoSyncEnabled(true)
                .consentGrantedAt(LocalDateTime.now())
                .totalImportsCount(0)
                .consentVersion(XpengConnection.AUTOSYNC_CONSENT_VERSION);
        if (revoked) b.consentRevokedAt(LocalDateTime.now());
        return b.build();
    }

    private void invokeProcessMessage(Message msg) throws Exception {
        invokeProcessMessage(msg, false);
    }

    private void invokeProcessMessage(Message msg, boolean passwordsOnly) throws Exception {
        Method m = XpengImapPoller.class.getDeclaredMethod("processMessage", Message.class, boolean.class);
        m.setAccessible(true);
        try {
            m.invoke(poller, msg, passwordsOnly);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw e;
        }
    }
}
