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
 * Portfolio-Modell: mehrere Tarife gleichzeitig aktiv, kein Auto-Deactivate mehr.
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
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── add() — Portfolio-Modell: kein deactivateCurrent ─────────────────────

    @Test
    void shouldNotDeactivateExisting_WhenAddingNewProvider() {
        UserChargingProviderRequest request = new UserChargingProviderRequest(
                "IONITY", null, new BigDecimal("0.29"), new BigDecimal("0.49"),
                BigDecimal.ZERO, BigDecimal.ZERO, LocalDate.now());

        service.add(userId, request);

        // Portfolio: keine anderen Provider deaktivieren
        verify(repository, never()).deleteAll(any());
        verify(repository, never()).deleteAllById(any());
    }

    @Test
    void shouldSaveMultipleActiveProviders_WithoutConflict() {
        UserChargingProviderRequest request1 = new UserChargingProviderRequest(
                "EnBW", "Arbeit RFID", new BigDecimal("0.29"), new BigDecimal("0.49"),
                new BigDecimal("4.99"), BigDecimal.ZERO, LocalDate.now());
        UserChargingProviderRequest request2 = new UserChargingProviderRequest(
                "Maingau", null, new BigDecimal("0.39"), new BigDecimal("0.52"),
                BigDecimal.ZERO, BigDecimal.ZERO, LocalDate.now());

        service.add(userId, request1);
        service.add(userId, request2);

        verify(repository, times(2)).save(any());
    }

    @Test
    void shouldSetCorrectUserId_WhenSavingNewProvider() {
        UserChargingProviderRequest request = new UserChargingProviderRequest(
                "Fastned", null, null, null, null, null, LocalDate.now());

        service.add(userId, request);

        ArgumentCaptor<UserChargingProviderEntity> entityCaptor =
                ArgumentCaptor.forClass(UserChargingProviderEntity.class);
        verify(repository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getUserId()).isEqualTo(userId);
        assertThat(entityCaptor.getValue().getActiveUntil()).isNull();
    }

    @Test
    void shouldSetLabel_WhenProvidedOnAdd() {
        UserChargingProviderRequest request = new UserChargingProviderRequest(
                "EnBW", "Meine EnBW Karte", null, null, null, null, LocalDate.now());

        service.add(userId, request);

        ArgumentCaptor<UserChargingProviderEntity> captor =
                ArgumentCaptor.forClass(UserChargingProviderEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getLabel()).isEqualTo("Meine EnBW Karte");
    }

    @Test
    void shouldDefaultFeesToZero_WhenNullPassedIn() {
        UserChargingProviderRequest request = new UserChargingProviderRequest(
                "EnBW", null, null, null, null, null, LocalDate.now());

        service.add(userId, request);

        ArgumentCaptor<UserChargingProviderEntity> captor =
                ArgumentCaptor.forClass(UserChargingProviderEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMonthlyFeeEur()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(captor.getValue().getSessionFeeEur()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── update() ─────────────────────────────────────────────────────────────

    @Test
    void shouldUpdateProvider_WhenOwnershipMatches() {
        UUID providerId = UUID.randomUUID();
        UserChargingProviderEntity entity = entityForUser(providerId, userId);
        when(repository.findById(providerId)).thenReturn(Optional.of(entity));

        UserChargingProviderRequest request = new UserChargingProviderRequest(
                "EnBW updated", "Neues Label", new BigDecimal("0.25"), new BigDecimal("0.45"),
                new BigDecimal("2.99"), BigDecimal.ZERO, LocalDate.now());

        service.update(userId, providerId, request);

        verify(repository).save(entity);
        assertThat(entity.getProviderName()).isEqualTo("EnBW updated");
        assertThat(entity.getLabel()).isEqualTo("Neues Label");
        assertThat(entity.getAcPricePerKwh()).isEqualByComparingTo(new BigDecimal("0.25"));
    }

    @Test
    void shouldThrowIllegalArgument_WhenUpdatingOtherUsersProvider() {
        UUID providerId = UUID.randomUUID();
        UserChargingProviderEntity entity = entityForUser(providerId, otherUserId);
        when(repository.findById(providerId)).thenReturn(Optional.of(entity));

        UserChargingProviderRequest request = new UserChargingProviderRequest(
                "Hacked", null, null, null, null, null, LocalDate.now());

        assertThatThrownBy(() -> service.update(userId, providerId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provider does not belong to user");
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

    @Test
    void shouldReturnMultipleProviders_ForPortfolio() {
        UserChargingProviderEntity e1 = entityForUser(UUID.randomUUID(), userId);
        e1.setProviderName("EnBW");
        UserChargingProviderEntity e2 = entityForUser(UUID.randomUUID(), userId);
        e2.setProviderName("Maingau");
        when(repository.findByUserIdOrderByActiveFromDesc(userId)).thenReturn(List.of(e1, e2));

        List<UserChargingProviderResponse> result = service.getAll(userId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserChargingProviderResponse::providerName)
                .containsExactly("EnBW", "Maingau");
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
