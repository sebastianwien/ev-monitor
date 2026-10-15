package com.evmonitor.application.vehiclespec;

import com.evmonitor.application.AdminAlertService;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.vehiclespec.MissingVehicleRequest;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Nutzer-Rückmeldung: Sprache aus dem Konto, ohne Kontakt-Mail keine Mail, Mail-Fehler bricht nichts. */
@ExtendWith(MockitoExtension.class)
class MissingVehicleRequestNotifierTest {

    @Mock private AdminAlertService adminAlertService;
    @Mock private EmailService emailService;
    @Mock private UserRepository userRepository;
    @InjectMocks private MissingVehicleRequestNotifier notifier;

    private static MissingVehicleRequest request(String email) {
        return MissingVehicleRequest.create(UUID.randomUUID(),
                new MissingVehicleRequestCommand("Polestar 2 Long Range", email, null, null, null, null).toDraft());
    }

    @Test
    void notifyUserDone_usesRegistrationLocaleAndFreeTextAsLabel() {
        MissingVehicleRequest r = request("max@example.com");
        User user = TestDataBuilder.createTestUser("max@example.com").toBuilder().registrationLocale("en-US").build();
        when(userRepository.findById(r.getUserId())).thenReturn(Optional.of(user));

        boolean sent = notifier.notifyUserDone(r);

        assertThat(sent).isTrue();
        verify(emailService).sendMissingVehicleDoneEmail("max@example.com", "en-US", "Polestar 2 Long Range");
    }

    @Test
    void notifyUserDone_withoutContactEmail_sendsNothing() {
        MissingVehicleRequest r = request("max@example.com");
        r.purgeContactEmail();

        assertThat(notifier.notifyUserDone(r)).isFalse();
        verify(emailService, never()).sendMissingVehicleDoneEmail(anyString(), any(), anyString());
    }

    @Test
    void notifyUserDone_whenMailFails_returnsFalse() {
        MissingVehicleRequest r = request("max@example.com");
        when(userRepository.findById(r.getUserId())).thenReturn(Optional.empty());
        doThrow(new RuntimeException("smtp down")).when(emailService)
                .sendMissingVehicleDoneEmail(anyString(), any(), anyString());

        assertThat(notifier.notifyUserDone(r)).isFalse();
    }

    @Test
    void notifyAdmin_delegatesToAdminAlert() {
        MissingVehicleRequest r = request("max@example.com");
        notifier.notifyAdmin(r);
        verify(adminAlertService).sendMissingVehicleRequest(r);
    }
}
