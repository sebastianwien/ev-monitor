package com.evmonitor.domain.vehiclespec;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MissingVehicleRequestRepository extends JpaRepository<MissingVehicleRequest, UUID> {

    /** Dedupe: gleicher Nutzer, gleicher (normalisierter) Text innerhalb des Fensters. */
    boolean existsByUserIdAndFreeTextIgnoreCaseAndCreatedAtAfter(UUID userId, String freeText, LocalDateTime after);

    /** Tagesdeckel der Admin-Mails. */
    long countByAdminNotifiedAtAfter(LocalDateTime after);

    List<MissingVehicleRequest> findAllByDoneAtIsNullOrderByCreatedAtDesc();

    /** DSGVO: Kontakt-Mail nach der Aufbewahrungsfrist nullen, die Meldung bleibt. */
    @Modifying
    @Query("""
            update MissingVehicleRequest r
               set r.contactEmail = null
             where r.doneAt < :cutoff
               and r.contactEmail is not null
            """)
    int purgeContactEmailsDoneBefore(@Param("cutoff") LocalDateTime cutoff);

    /** DSGVO: auch nie erledigte Meldungen (Spam, Duplikat) verlieren die Kontakt-Mail nach der Frist. */
    @Modifying
    @Query("""
            update MissingVehicleRequest r
               set r.contactEmail = null
             where r.doneAt is null
               and r.createdAt < :cutoff
               and r.contactEmail is not null
            """)
    int purgeContactEmailsOpenCreatedBefore(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("""
            update MissingVehicleRequest r
               set r.contactEmail = null
             where r.userId = :userId
               and r.contactEmail is not null
            """)
    int purgeContactEmailsOfUser(@Param("userId") UUID userId);
}
