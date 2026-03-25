package com.evmonitor.application.user;

import com.evmonitor.infrastructure.persistence.JpaUserChargingProviderRepository;
import com.evmonitor.infrastructure.persistence.UserChargingProviderEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserChargingProviderService.
 * Focuses on the auto-deactivation business logic and ownership enforcement.
 */
@ExtendWith(MockitoExtension.class)
class UserChargingProviderServiceTest {

    @Mock
    private JpaUserChargingProviderRepository repository;

    private UserChargingProviderService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserChargingProviderService(repository);
        // save returns the entity as-is (ID and timestamps not relevant for logic tests)
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── add() — auto-deactivation ─────────────────────────────────────────────

    @Test
    void shouldCallDeactivateCurrent_WithDayBeforeNewActiveFrom() {
        LocalDate newActiveFrom = LocalDate.of(2026, 3, 1);
        UserChargingProviderRequest request = new UserChargingProviderRequest(
                "IONITY", new BigDecimal("0.29"), new BigDecimal("0.49"),
                BigDecimal.ZERO, BigDecimal.ZERO, newActiveFrom);

        service.add(userId, request);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(repository).deactivateCurrent(eq(userId), dateCaptor.capture());
        assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void shouldSetCorrectUserId_WhenSavingNewProvider() {
        UserChargingProviderRequest request = new UserChargingProviderRequest(
                "Fastned", null, null, null, null, LocalDate.now());

        service.add(userId, request);

        ArgumentCaptor<UserChargingProviderEntity> entityCaptor =
                ArgumentCaptor.forClass(UserChargingProviderEntity.class);
        verify(repository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getUserId()).isEqualTo(userId);
        assertThat(entityCaptor.getValue().getActiveUntil()).isNull();
    }

    @Test
    void shouldDefaultFeesToZero_WhenNullPassedIn() {
        UserChargingProviderRequest request = new UserChargingProviderRequest(
                "EnBW", null, null, null, null, LocalDate.now());

        service.add(userId, request);

        ArgumentCaptor<UserChargingProviderEntity> captor =
                ArgumentCaptor.forClass(UserChargingProviderEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMonthlyFeeEur()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(captor.getValue().getSessionFeeEur()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── delete() — ownership ──────────────────────────────────────────────────

    @Test
    void shouldDeleteProvider_WhenOwnershipMatches() {
        UUID providerId = UUID.randomUUID();
        UserChargingProviderEntity entity = entityForUser(providerId, userId);
        when(repository.findById(providerId)).thenReturn(Optional.of(entity));

        service.delete(userId, providerId);

        verify(repository).delete(entity);
    }

    @Test
    void shouldThrowIllegalArgument_WhenDeletingOtherUsersProvider() {
        UUID providerId = UUID.randomUUID();
        UserChargingProviderEntity entity = entityForUser(providerId, otherUserId);
        when(repository.findById(providerId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.delete(userId, providerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provider does not belong to user");
    }

    @Test
    void shouldThrowIllegalArgument_WhenProviderNotFound() {
        UUID providerId = UUID.randomUUID();
        when(repository.findById(providerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, providerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provider not found");
    }

    // ── getAll() ──────────────────────────────────────────────────────────────

    @Test
    void shouldReturnOnlyOwnProviders() {
        UserChargingProviderEntity e = entityForUser(UUID.randomUUID(), userId);
        e.setProviderName("IONITY");
        when(repository.findByUserIdOrderByActiveFromDesc(userId)).thenReturn(List.of(e));

        List<UserChargingProviderResponse> result = service.getAll(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).providerName()).isEqualTo("IONITY");
        verify(repository).findByUserIdOrderByActiveFromDesc(userId);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UserChargingProviderEntity entityForUser(UUID id, UUID userId) {
        UserChargingProviderEntity e = new UserChargingProviderEntity();
        e.setId(id);
        e.setUserId(userId);
        e.setProviderName("TestProvider");
        e.setMonthlyFeeEur(BigDecimal.ZERO);
        e.setSessionFeeEur(BigDecimal.ZERO);
        e.setActiveFrom(LocalDate.now());
        e.setActiveUntil(null);
        return e;
    }
}
