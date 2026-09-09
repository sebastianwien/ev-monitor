package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface JpaUserChargingProviderRepository extends JpaRepository<UserChargingProviderEntity, UUID> {

    /** The user's portfolio. Soft-deleted cards stay in the table but leave the wallet. */
    List<UserChargingProviderEntity> findByUserIdAndDeletedAtIsNullOrderByActiveFromDesc(UUID userId);

    boolean existsByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    /**
     * Die als privat markierten Heimtarife, die an diesem Tag galten. Liefert bewusst eine
     * Liste: bei mehr als einem Treffer ist die Zuordnung mehrdeutig und der Aufrufer bepreist
     * nichts, statt zu raten.
     */
    @Query("SELECT c FROM UserChargingProviderEntity c"
            + " WHERE c.userId = :userId"
            + " AND c.privateCard = true"
            + " AND c.deletedAt IS NULL"
            + " AND c.activeFrom <= :on"
            + " AND (c.activeUntil IS NULL OR c.activeUntil >= :on)")
    List<UserChargingProviderEntity> findPrivateCardsActiveOn(@Param("userId") UUID userId, @Param("on") LocalDate on);
}
