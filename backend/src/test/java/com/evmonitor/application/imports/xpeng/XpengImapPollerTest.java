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

    // --- processMessage: token not found ---

    @Test
    void skipsMailWithNoRoutingToken() throws Exception {
        Message msg = mockMessage("<id@test.com>", "Re: Normal mail without token");
        when(receivedMailRepo.existsByMessageId(any())).thenReturn(false);

        invokeProcessMessage(msg);

        verify(msg).setFlag(Flags.Flag.SEEN, true);
        verify(connectionRepo, never()).findByRoutingToken(any());
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

    // --- helpers ---

    private static Message mockMessage(String messageId, String subject) throws Exception {
        Message msg = mock(Message.class);
        when(msg.getHeader("Message-ID")).thenReturn(new String[]{messageId});
        when(msg.getSubject()).thenReturn(subject);
        return msg;
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
        Method m = XpengImapPoller.class.getDeclaredMethod("processMessage", Message.class);
        m.setAccessible(true);
        try {
            m.invoke(poller, msg);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw e;
        }
    }
}
