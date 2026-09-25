package com.evmonitor.infrastructure.persistence.ingest;

import com.evmonitor.application.ingest.event.ImportEventOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ImportEventRepository extends JpaRepository<ImportEvent, UUID> {

    /** Kontolöschung (zusätzlich zum FK-CASCADE, damit auch ohne Flyway-Schema testbar). */
    @Modifying
    @Transactional
    @Query("DELETE FROM ImportEvent e WHERE e.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    /** TTL-Purge. */
    @Modifying
    @Transactional
    @Query("DELETE FROM ImportEvent e WHERE e.createdAt < :threshold")
    int deleteByCreatedAtBefore(@Param("threshold") LocalDateTime threshold);

    record OutcomeTotal(String provider, String channel, ImportEventOutcome outcome, long events,
                        long sessionsImported, long sessionsSkipped, long sessionsFailed,
                        long tripsImported, long tripsSkipped, LocalDateTime lastAt) {}

    record ErrorTotal(String provider, String channel, String error, long count, LocalDateTime lastAt) {}

    record DailyTotal(LocalDate date, ImportEventOutcome outcome, long count) {}

    @Query("""
            SELECT new com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository$OutcomeTotal(
                   e.provider, e.channel, e.outcome, COUNT(e),
                   SUM(e.sessionsImported), SUM(e.sessionsSkipped), SUM(e.sessionsFailed),
                   SUM(e.tripsImported), SUM(e.tripsSkipped), MAX(e.createdAt))
            FROM ImportEvent e WHERE e.createdAt >= :since
            GROUP BY e.provider, e.channel, e.outcome
            ORDER BY e.provider, e.channel
            """)
    List<OutcomeTotal> outcomeTotalsSince(@Param("since") LocalDateTime since);

    @Query("""
            SELECT new com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository$ErrorTotal(
                   e.provider, e.channel, e.error, COUNT(e), MAX(e.createdAt))
            FROM ImportEvent e WHERE e.createdAt >= :since AND e.error IS NOT NULL
            GROUP BY e.provider, e.channel, e.error
            """)
    List<ErrorTotal> errorTotalsSince(@Param("since") LocalDateTime since);

    @Query("""
            SELECT new com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository$DailyTotal(
                   CAST(e.createdAt AS LocalDate), e.outcome, COUNT(e))
            FROM ImportEvent e WHERE e.createdAt >= :since
            GROUP BY CAST(e.createdAt AS LocalDate), e.outcome
            ORDER BY CAST(e.createdAt AS LocalDate), e.outcome
            """)
    List<DailyTotal> dailyTotalsSince(@Param("since") LocalDateTime since);
}
