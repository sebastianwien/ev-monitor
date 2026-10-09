package com.evmonitor.application.promo;

import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.persistence.JpaUserRepository;
import com.evmonitor.infrastructure.persistence.TrialReminderRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrialReminderServiceTest {

    private final JpaUserRepository users = mock(JpaUserRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final TrialReminderService service = new TrialReminderService(users, emailService);

    @Test
    void dueReminderIsClaimedThenMailedWithTheTrialEnd() {
        UUID id = UUID.randomUUID();
        LocalDateTime due = LocalDateTime.of(2026, 11, 1, 9, 0);
        when(users.findTrialRemindersDue(any())).thenReturn(List.of(new TrialReminderRow(id, "a@example.com", "anna", "de", due)));
        when(users.claimTrialReminder(id)).thenReturn(1);

        service.sendDueReminders();

        verify(emailService).sendTrialEndingEmail("a@example.com", "anna", "de", LocalDate.of(2026, 11, 8));
    }

    @Test
    void reminderClaimedByTheParallelInstanceIsNotSentTwice() {
        UUID id = UUID.randomUUID();
        when(users.findTrialRemindersDue(any())).thenReturn(List.of(
                new TrialReminderRow(id, "a@example.com", "anna", "de", LocalDateTime.now())));
        when(users.claimTrialReminder(id)).thenReturn(0);

        service.sendDueReminders();

        verify(emailService, never()).sendTrialEndingEmail(anyString(), anyString(), anyString(), any());
    }

    @Test
    void oneFailingMailDoesNotStopTheOthers() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        LocalDateTime due = LocalDateTime.of(2026, 11, 1, 9, 0);
        when(users.findTrialRemindersDue(any())).thenReturn(List.of(
                new TrialReminderRow(first, "a@example.com", "anna", "de", due),
                new TrialReminderRow(second, "b@example.com", "ben", "de", due)));
        when(users.claimTrialReminder(any())).thenReturn(1);
        doThrow(new IllegalStateException("smtp down")).when(emailService)
                .sendTrialEndingEmail(eq("a@example.com"), anyString(), anyString(), any());

        service.sendDueReminders();

        verify(emailService).sendTrialEndingEmail("b@example.com", "ben", "de", LocalDate.of(2026, 11, 8));
    }
}
