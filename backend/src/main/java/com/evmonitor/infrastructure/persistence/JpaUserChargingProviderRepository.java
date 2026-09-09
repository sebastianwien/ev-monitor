package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaUserChargingProviderRepository extends JpaRepository<UserChargingProviderEntity, UUID> {

    /** The user's portfolio. Soft-deleted cards stay in the table but leave the wallet. */
    List<UserChargingProviderEntity> findByUserIdAndDeletedAtIsNullOrderByActiveFromDesc(UUID userId);

    boolean existsByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    /**
     * Alle Heimtarife des Users. Welcher davon zu einer Ladung passt, entscheidet
     * {@code LocationPricing#homeCardFor} anhand des Ladedatums - so bleibt die Regel an
     * einer Stelle, und der Sammel-Nachtrag fragt nicht pro Ladung erneut die DB.
     */
    List<UserChargingProviderEntity> findByUserIdAndPrivateCardTrueAndDeletedAtIsNull(UUID userId);
}
