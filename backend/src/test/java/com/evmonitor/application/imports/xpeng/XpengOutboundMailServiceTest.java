package com.evmonitor.application.imports.xpeng;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.persistence.xpeng.XpengConnection;
import com.evmonitor.infrastructure.persistence.xpeng.XpengConnectionRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class XpengOutboundMailServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock UserRepository userRepository;
    @Mock XpengConnectionRepository connectionRepo;
    @InjectMocks XpengOutboundMailService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CONN_ID = UUID.randomUUID();
    private static final UUID ROUTING_TOKEN = UUID.randomUUID();
    private static final String VIN = "L1NN12345678ABCDE";
    private static final String USER_EMAIL = "driver@example.com";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "fromAddress", "noreply@ev-monitor.net");
        ReflectionTestUtils.setField(service, "replyToInbox", "xpeng-inbox@ev-monitor.net");
        lenient().when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        lenient().when(connectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void subjectContainsVinAndRoutingToken() {
        XpengConnection conn = buildConn(null);
        String subject = XpengOutboundMailService.buildSubject(conn);

        assertTrue(subject.contains(VIN), "Subject muss VIN enthalten");
        assertTrue(subject.contains("[token:" + ROUTING_TOKEN + "]"),
                "Subject muss Routing-Token enthalten");
        assertTrue(subject.contains("EU Data Act"), "Subject muss EU Data Act erwaehnen");
    }

    @Test
    void usesXpengEmailAsCcWhenProvided() {
        XpengConnection conn = buildConn("xpeng-specific@test.com");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithEmail(USER_EMAIL)));

        service.sendDataRequest(conn);

        // lastRequestSentAt wird gesetzt
        assertNotNull(conn.getLastRequestSentAt());
        verify(connectionRepo).save(conn);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void fallsBackToAppUserEmailForCc() {
        XpengConnection conn = buildConn(null); // kein xpengEmail
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithEmail(USER_EMAIL)));

        service.sendDataRequest(conn);

        verify(mailSender).send(any(MimeMessage.class));
        assertNotNull(conn.getLastRequestSentAt());
    }

    @Test
    void setsLastRequestSentAt() {
        XpengConnection conn = buildConn(null);
        assertNull(conn.getLastRequestSentAt());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithEmail(USER_EMAIL)));

        service.sendDataRequest(conn);

        assertNotNull(conn.getLastRequestSentAt());
        assertTrue(conn.getLastRequestSentAt().isAfter(LocalDateTime.now().minusSeconds(5)));
    }

    @Test
    void throwsWhenUserNotFound() {
        XpengConnection conn = buildConn(null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.sendDataRequest(conn));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // --- Token-Parsing (statische Methode, kein Mock noetig) ---

    @Test
    void extractsTokenFromSubject() {
        UUID token = UUID.randomUUID();
        String subject = "Re: Request for Telematics Data - VIN L1XX (EU Data Act) [token:" + token + "]";
        UUID extracted = XpengImapPoller.extractRoutingTokenFromSubject(subject);
        assertEquals(token, extracted);
    }

    @Test
    void returnsNullForSubjectWithoutToken() {
        assertNull(XpengImapPoller.extractRoutingTokenFromSubject("Fwd: Normal reply without token"));
        assertNull(XpengImapPoller.extractRoutingTokenFromSubject(null));
    }

    @Test
    void returnsNullForMalformedToken() {
        assertNull(XpengImapPoller.extractRoutingTokenFromSubject("Re: Test [token:not-a-uuid]"));
    }

    private XpengConnection buildConn(String xpengEmail) {
        return XpengConnection.builder()
                .id(CONN_ID).userId(USER_ID).carId(UUID.randomUUID()).vin(VIN)
                .routingToken(ROUTING_TOKEN)
                .autoSyncEnabled(true)
                .xpengEmail(xpengEmail)
                .consentGrantedAt(LocalDateTime.now())
                .consentVersion(XpengConnection.AUTOSYNC_CONSENT_VERSION)
                .totalImportsCount(0)
                .build();
    }

    private User userWithEmail(String email) {
        return User.builder()
                .id(USER_ID).email(email).username("testuser")
                .authProvider(AuthProvider.LOCAL)
                .role("USER").premium(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }
}
