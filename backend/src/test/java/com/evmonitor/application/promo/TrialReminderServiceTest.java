package com.evmonitor.application.promo;

import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.persistence.JpaUserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(users.findTrialRemindersDue(any())).thenReturn(List.of(new TrialReminderDue(id, "a@example.com", "anna", "de", due)));
        when(users.claimTrialReminder(id)).thenReturn(1);

        service.sendDueReminders();

        verify(emailService).sendTrialEndingEmail("a@example.com", "anna", "de", LocalDate.of(2026, 11, 8));
    }

    @Test
    void reminderClaimedByTheParallelInstanceIsNotSentTwice() {
        UUID id = UUID.randomUUID();
        when(users.findTrialRemindersDue(any())).thenReturn(List.of(
                new TrialReminderDue(id, "a@example.com", "anna", "de", LocalDateTime.now())));
        when(users.claimTrialReminder(id)).thenReturn(0);

        service.sendDueReminders();

        verify(emailService, never()).sendTrialEndingEmail(anyString(), anyString(), anyString(), any());
    }
}
