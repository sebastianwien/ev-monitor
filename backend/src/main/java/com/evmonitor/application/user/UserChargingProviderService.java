package com.evmonitor.application.user;

import com.evmonitor.infrastructure.persistence.JpaUserChargingProviderRepository;
import com.evmonitor.infrastructure.persistence.UserChargingProviderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserChargingProviderService {

    private final JpaUserChargingProviderRepository repository;

    public List<UserChargingProviderResponse> getAll(UUID userId) {
        return repository.findByUserIdAndDeletedAtIsNullOrderByActiveFromDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserChargingProviderResponse add(UUID userId, UserChargingProviderRequest request) {
        UserChargingProviderEntity entity = new UserChargingProviderEntity();
        entity.setUserId(userId);
        entity.setProviderName(request.providerName());
        entity.setLabel(request.label());
        entity.setAcPricePerKwh(request.acPricePerKwh());
        entity.setDcPricePerKwh(request.dcPricePerKwh());
        entity.setMonthlyFeeEur(request.monthlyFeeEur() != null ? request.monthlyFeeEur() : java.math.BigDecimal.ZERO);
        entity.setSessionFeeEur(request.sessionFeeEur() != null ? request.sessionFeeEur() : java.math.BigDecimal.ZERO);
        entity.setActiveFrom(request.activeFrom());
        entity.setHome(Boolean.TRUE.equals(request.isHome()));
        entity.setActiveUntil(null);
        entity.setHome(Boolean.TRUE.equals(request.isHome()));

        return toResponse(repository.save(entity));
    }

    @Transactional
    public UserChargingProviderResponse update(UUID userId, UUID providerId, UserChargingProviderRequest request) {
        UserChargingProviderEntity entity = findOwnedCard(userId, providerId);

        entity.setProviderName(request.providerName());
        entity.setLabel(request.label());
        entity.setAcPricePerKwh(request.acPricePerKwh());
        entity.setDcPricePerKwh(request.dcPricePerKwh());
        entity.setMonthlyFeeEur(request.monthlyFeeEur() != null ? request.monthlyFeeEur() : java.math.BigDecimal.ZERO);
        entity.setSessionFeeEur(request.sessionFeeEur() != null ? request.sessionFeeEur() : java.math.BigDecimal.ZERO);
        entity.setActiveFrom(request.activeFrom());

        return toResponse(repository.save(entity));
    }

    /**
     * Nimmt die Karte aus dem Portfolio. Bewusst kein hartes DELETE: ev_log.charging_provider_id
     * ist ON DELETE SET NULL, das Loeschen wuerde die Karte aus jeder Ladung reissen, die je mit
     * ihr bezahlt wurde - und damit Kostenhistorie und Anbieter-Statistik ruecklings umschreiben.
     */
    @Transactional
    public void delete(UUID userId, UUID providerId) {
        UserChargingProviderEntity entity = findOwnedCard(userId, providerId);
        entity.setDeletedAt(LocalDateTime.now());
        repository.save(entity);
    }

    /** Eine geloeschte Karte existiert fuer den User nicht mehr - sie ist weder aenderbar noch erneut loeschbar. */
    private UserChargingProviderEntity findOwnedCard(UUID userId, UUID providerId) {
        UserChargingProviderEntity entity = repository.findById(providerId)
                .filter(e -> e.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found"));
        if (!entity.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Provider does not belong to user");
        }
        return entity;
    }

    private UserChargingProviderResponse toResponse(UserChargingProviderEntity e) {
        return new UserChargingProviderResponse(
                e.getId(),
                e.getProviderName(),
                e.getLabel(),
                e.getAcPricePerKwh(),
                e.getDcPricePerKwh(),
                e.getMonthlyFeeEur(),
                e.getSessionFeeEur(),
                e.getActiveFrom(),
                e.getActiveUntil(),
                e.isHome()
        );
    }
}
