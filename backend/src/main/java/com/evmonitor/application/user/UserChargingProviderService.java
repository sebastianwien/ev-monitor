package com.evmonitor.application.user;

import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.infrastructure.persistence.JpaUserChargingProviderRepository;
import com.evmonitor.infrastructure.persistence.UserChargingProviderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserChargingProviderService {

    private final JpaUserChargingProviderRepository repository;
    private final com.evmonitor.application.CoinLogService coinLogService;

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
        entity.setActiveUntil(null);
        entity.setPrivateCard(request.isPrivate());
        endPreviousHomeTariff(userId, request, null);

        UserChargingProviderResponse saved = toResponse(repository.save(entity));
        // Einmalig: die erste Karte ist der Schritt, der Auto-Bepreisung ueberhaupt moeglich macht.
        coinLogService.awardCoinsForEvent(userId, com.evmonitor.application.CoinLogService.CoinEvent.CARD_CREATED, null);
        return saved;
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
        entity.setPrivateCard(request.isPrivate());
        endPreviousHomeTariff(userId, request, providerId);

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

    /**
     * Zu einem Zeitpunkt gilt genau ein Heimtarif - man hat zu Hause einen Stromvertrag.
     * Beim Anlegen eines neueren endet der bisherige am Tag davor, statt unbefristet parallel
     * zu laufen: zwei gleichzeitig gueltige Heimtarife sind mehrdeutig, und dann bepreist
     * {@code LocationPricing} gar nichts mehr - stillschweigend.
     *
     * Oeffentliche Ladekarten bleiben davon unberuehrt, die duerfen beliebig parallel laufen.
     *
     * @param selfId die gerade bearbeitete Karte, die sich nicht selbst beenden darf (null beim Anlegen)
     * @throws ValidationException wenn der neue Tarif vor einem bestehenden beginnt - welcher
     *         dann gilt, ist unentscheidbar, das muss der User selbst klaeren
     */
    private void endPreviousHomeTariff(UUID userId, UserChargingProviderRequest request, UUID selfId) {
        if (!request.isPrivate()) return;

        LocalDate from = request.activeFrom();
        for (UserChargingProviderEntity other : repository.findByUserIdAndPrivateCardTrueAndDeletedAtIsNull(userId)) {
            if (other.getId().equals(selfId)) continue;
            // Bereits beendet und vor dem neuen Tarif abgelaufen: keine Ueberschneidung.
            if (other.getActiveUntil() != null && other.getActiveUntil().isBefore(from)) continue;
            if (!other.getActiveFrom().isBefore(from)) {
                throw new ValidationException("HOME_TARIFF_OVERLAP",
                        "A home tariff already covers this period - end it first");
            }
            other.setActiveUntil(from.minusDays(1));
            repository.save(other);
        }
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
                e.isPrivateCard()
        );
    }
}
