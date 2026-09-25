package com.evmonitor.application.imports.vweuda;

import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VwEudaNotificationServiceTest {

    @Mock UserRepository userRepository;
    @Mock EmailService emailService;
    @InjectMocks VwEudaNotificationService service;

    private final UUID userId = UUID.randomUUID();

    private User user() {
        return TestDataBuilder.createTestUserWithId(userId, "max@example.com", "hash");
    }

    @Test
    void handover_sendsHandoverMailInUsersLocale() {
        User u = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        service.notify(userId, VwEudaNotificationService.EVENT_HANDOVER, Map.of("drop", "x.zip"));

        verify(emailService).sendVwEudaHandoverEmail("max@example.com", u.getUsername(), u.getRegistrationLocale());
    }

    @Test
    void connectionLost_sendsConnectionLostMail() {
        User u = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        service.notify(userId, VwEudaNotificationService.EVENT_CONNECTION_LOST, Map.of("reason", "sso"));

        verify(emailService).sendVwEudaConnectionLostEmail("max@example.com", u.getUsername(), u.getRegistrationLocale());
    }

    @Test
    void historyImported_passesCountsToMail() {
        User u = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        service.notify(userId, VwEudaNotificationService.EVENT_HISTORY_IMPORTED, Map.of("imported", 197, "skipped", 6));

        verify(emailService).sendVwEudaHistoryImportedEmail("max@example.com", u.getUsername(),
                u.getRegistrationLocale(), 197, 6);
    }

    @Test
    void trialEnding_passesEndDateToMail() {
        User u = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        service.notify(userId, VwEudaNotificationService.EVENT_TRIAL_ENDING, Map.of("endsAt", "2026-10-21"));

        verify(emailService).sendVwEudaTrialEndingEmail("max@example.com", u.getUsername(),
                u.getRegistrationLocale(), java.time.LocalDate.of(2026, 10, 21));
    }

    @Test
    void trialEnded_sendsTrialEndedMail() {
        User u = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        service.notify(userId, VwEudaNotificationService.EVENT_TRIAL_ENDED, Map.of());

        verify(emailService).sendVwEudaTrialEndedEmail("max@example.com", u.getUsername(), u.getRegistrationLocale());
    }

    @Test
    void unknownUserOrEvent_sendsNothing() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        service.notify(userId, VwEudaNotificationService.EVENT_HANDOVER, Map.of());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));
        service.notify(userId, "SOMETHING_ELSE", Map.of());

        verify(emailService, never()).sendVwEudaHandoverEmail(anyString(), any(), any());
        verify(emailService, never()).sendVwEudaConnectionLostEmail(anyString(), any(), any());
        verify(emailService, never()).sendVwEudaHistoryImportedEmail(anyString(), any(), any(), anyInt(), anyInt());
    }
}
