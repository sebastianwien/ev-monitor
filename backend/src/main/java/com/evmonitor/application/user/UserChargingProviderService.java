package com.evmonitor.application.user;

import com.evmonitor.infrastructure.persistence.JpaUserChargingProviderRepository;
import com.evmonitor.infrastructure.persistence.UserChargingProviderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserChargingProviderService {

    private final JpaUserChargingProviderRepository repository;

    public List<UserChargingProviderResponse> getAll(UUID userId) {
        return repository.findByUserIdOrderByActiveFromDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<UserChargingProviderResponse> getActive(UUID userId) {
        return repository.findByUserIdAndActiveUntilIsNull(userId)
                .map(this::toResponse);
    }

    @Transactional
    public UserChargingProviderResponse add(UUID userId, UserChargingProviderRequest request) {
        // Deactivate current active provider (set active_until to day before new one starts)
        repository.deactivateCurrent(userId, request.activeFrom().minusDays(1));

        UserChargingProviderEntity entity = new UserChargingProviderEntity();
        entity.setUserId(userId);
        entity.setProviderName(request.providerName());
        entity.setAcPricePerKwh(request.acPricePerKwh());
        entity.setDcPricePerKwh(request.dcPricePerKwh());
        entity.setMonthlyFeeEur(request.monthlyFeeEur() != null ? request.monthlyFeeEur() : java.math.BigDecimal.ZERO);
        entity.setSessionFeeEur(request.sessionFeeEur() != null ? request.sessionFeeEur() : java.math.BigDecimal.ZERO);
        entity.setActiveFrom(request.activeFrom());
        entity.setActiveUntil(null);

        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(UUID userId, UUID providerId) {
        UserChargingProviderEntity entity = repository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found"));
        if (!entity.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Provider does not belong to user");
        }
        repository.delete(entity);
    }

    private UserChargingProviderResponse toResponse(UserChargingProviderEntity e) {
        return new UserChargingProviderResponse(
                e.getId(),
                e.getProviderName(),
                e.getAcPricePerKwh(),
                e.getDcPricePerKwh(),
                e.getMonthlyFeeEur(),
                e.getSessionFeeEur(),
                e.getActiveFrom(),
                e.getActiveUntil()
        );
    }
}
