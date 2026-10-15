package com.evmonitor.application.vehiclespec;

import com.evmonitor.domain.exception.AuthException;
import com.evmonitor.domain.exception.ConflictException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.domain.vehiclespec.MissingVehicleRequest;
import com.evmonitor.domain.vehiclespec.MissingVehicleRequestRepository;
import com.evmonitor.infrastructure.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Meldeweg "Fehlt etwas?": jede Schutzregel gegen Massenversand ein Test.
 * Mail-Inhalte testet der Notifier, hier zählt nur, ob und wann er gerufen wird.
 */
@ExtendWith(MockitoExtension.class)
class MissingVehicleRequestServiceTest {

    private static final String IP = "203.0.113.7";

    @Mock private MissingVehicleRequestRepository repository;
    @Mock private RateLimitService rateLimitService;
    @Mock private MissingVehicleRequestNotifier notifier;

    private MissingVehicleRequestService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MissingVehicleRequestService(repository, rateLimitService, notifier);
        ReflectionTestUtils.setField(service, "dailyMailCap", 50);
        lenient().when(rateLimitService.tryConsumeVehicleRequestUser(any())).thenReturn(true);
        lenient().when(rateLimitService.tryConsumeVehicleRequestIp(any())).thenReturn(true);
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(notifier.notifyUserDone(any())).thenReturn(true);
    }

    private static MissingVehicleRequestCommand cmd(String text) {
        return new MissingVehicleRequestCommand(text, "max@example.com", "POLESTAR", "POLESTAR_2",
                new BigDecimal("78"), 2022);
    }

    // ── submit ───────────────────────────────────────────────────────────────

    @Test
    void submit_valid_savesRowAndNotifiesAdmin() {
        service.submit(userId, IP, cmd("Polestar 2 Long Range Dual Motor 2022"));

        ArgumentCaptor<MissingVehicleRequest> saved = ArgumentCaptor.forClass(MissingVehicleRequest.class);
        verify(repository).save(saved.capture());
        MissingVehicleRequest r = saved.getValue();
        assertThat(r.getUserId()).isEqualTo(userId);
        assertThat(r.getContactEmail()).isEqualTo("max@example.com");
        assertThat(r.getFreeText()).isEqualTo("Polestar 2 Long Range Dual Motor 2022");
        assertThat(r.getBrand()).isEqualTo("POLESTAR");
        assertThat(r.getModel()).isEqualTo("POLESTAR_2");
        assertThat(r.getNetKwh()).isEqualByComparingTo("78");
        assertThat(r.getBuildYear()).isEqualTo(2022);
        assertThat(r.getCreatedAt()).isNotNull();
        assertThat(r.getAdminNotifiedAt()).isNotNull();
        assertThat(r.getDoneAt()).isNull();
        verify(notifier).notifyAdmin(r);
    }

    @Test
    void submit_sameUserSameTextWithin24h_isSilentlyDeduped() {
        when(repository.existsByUserIdAndFreeTextIgnoreCaseAndCreatedAtAfter(
                eq(userId), eq("Polestar 2 Long Range"), any())).thenReturn(true);

        service.submit(userId, IP, cmd("  Polestar   2 Long   Range "));

        verify(repository, never()).save(any());
        verify(notifier, never()).notifyAdmin(any());
    }

    @Test
    void submit_userLimitExceeded_is429AndSavesNothing() {
        when(rateLimitService.tryConsumeVehicleRequestUser(userId.toString())).thenReturn(false);

        assertThatThrownBy(() -> service.submit(userId, IP, cmd("Lucid Air")))
                .isInstanceOf(AuthException.class)
                .extracting("code").isEqualTo("RATE_LIMITED");
        verify(repository, never()).save(any());
        verify(notifier, never()).notifyAdmin(any());
    }

    @Test
    void submit_ipLimitExceeded_is429AndSavesNothing() {
        when(rateLimitService.tryConsumeVehicleRequestIp(IP)).thenReturn(false);

        assertThatThrownBy(() -> service.submit(userId, IP, cmd("Lucid Air")))
                .isInstanceOf(AuthException.class)
                .extracting("code").isEqualTo("RATE_LIMITED");
        verify(repository, never()).save(any());
    }

    @Test
    void submit_dailyMailCapReached_savesRowWithoutMail() {
        when(repository.countByAdminNotifiedAtAfter(any())).thenReturn(50L);

        service.submit(userId, IP, cmd("Lucid Air"));

        ArgumentCaptor<MissingVehicleRequest> saved = ArgumentCaptor.forClass(MissingVehicleRequest.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getAdminNotifiedAt()).isNull();
        verify(notifier, never()).notifyAdmin(any());
    }

    @Test
    void submit_stripsHtmlTagsAndCollapsesWhitespace() {
        service.submit(userId, IP, cmd("<b>Polestar</b> 2   <script>alert(1)</script>"));

        ArgumentCaptor<MissingVehicleRequest> saved = ArgumentCaptor.forClass(MissingVehicleRequest.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getFreeText()).isEqualTo("Polestar 2 alert(1)");
    }

    @Test
    void submit_blankText_isValidationError() {
        assertThatThrownBy(() -> service.submit(userId, IP, cmd("<br>")))
                .isInstanceOf(ValidationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void submit_invalidEmail_isValidationError() {
        MissingVehicleRequestCommand bad = new MissingVehicleRequestCommand("Lucid Air", "not-an-email",
                null, null, null, null);
        assertThatThrownBy(() -> service.submit(userId, IP, bad))
                .isInstanceOf(ValidationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void submit_emailTooLong_isValidationError() {
        String longLocal = "a".repeat(250);
        MissingVehicleRequestCommand bad = new MissingVehicleRequestCommand("Lucid Air", longLocal + "@example.com",
                null, null, null, null);
        assertThatThrownBy(() -> service.submit(userId, IP, bad))
                .isInstanceOf(ValidationException.class);
    }

    // ── markDone ─────────────────────────────────────────────────────────────

    @Test
    void markDone_setsDoneAndNotifiesUserExactlyOnce() {
        UUID adminId = UUID.randomUUID();
        MissingVehicleRequest r = MissingVehicleRequest.create(userId, cmd("Lucid Air").toDraft());
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));

        service.markDone(adminId, r.getId());

        assertThat(r.getDoneAt()).isNotNull();
        assertThat(r.getDoneByAdminId()).isEqualTo(adminId);
        assertThat(r.getUserNotifiedAt()).isNotNull();
        verify(notifier, times(1)).notifyUserDone(r);
        verify(repository).save(r);
    }

    @Test
    void markDone_whenMailFails_keepsDoneButNotNotified() {
        MissingVehicleRequest r = MissingVehicleRequest.create(userId, cmd("Lucid Air").toDraft());
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        when(notifier.notifyUserDone(r)).thenReturn(false);

        service.markDone(UUID.randomUUID(), r.getId());

        assertThat(r.getDoneAt()).isNotNull();
        assertThat(r.getUserNotifiedAt()).isNull();
    }

    @Test
    void markDone_twice_isConflict() {
        MissingVehicleRequest r = MissingVehicleRequest.create(userId, cmd("Lucid Air").toDraft());
        r.markDone(UUID.randomUUID());
        when(repository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.markDone(UUID.randomUUID(), r.getId()))
                .isInstanceOf(ConflictException.class);
        verify(notifier, never()).notifyUserDone(any());
    }

    @Test
    void markDone_unknownId_isNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markDone(UUID.randomUUID(), id))
                .isInstanceOf(NotFoundException.class);
    }
}
