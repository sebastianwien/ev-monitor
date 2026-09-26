package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.domain.exception.ConflictException;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ladevorgänge werden nie hart gelöscht, sondern per {@code deleted_at} unsichtbar.
 * Unsichtbar heißt: für Listen, Statistiken und JPQL-Joins weg. Sichtbar bleibt der Tombstone
 * für Dedupe (sonst legt der nächste EUDA-Sync den Vorgang neu an) und für Restore.
 */
class EvLogServiceSoftDeleteTest extends AbstractIntegrationTest {

    @Autowired
    private EvLogService evLogService;

    @Autowired
    private CoinLogService coinLogService;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("softdel-" + UUID.randomUUID().toString().substring(0, 8) + "@ev-monitor.net");
        userId = user.getId();
        carId = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3).getId();
    }

    @Test
    void deleteLog_hidesLogButKeepsRow() {
        EvLog log = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 1, 10, 0)));

        evLogService.deleteLog(log.getId(), userId);

        assertThat(evLogRepository.findById(log.getId())).isEmpty();
        assertThat(evLogRepository.findAllByCarId(carId)).isEmpty();
        assertThat(evLogRepository.countByUserId(userId)).isZero();
        EvLog tombstone = evLogRepository.findByIdIncludingDeleted(log.getId()).orElseThrow();
        assertThat(tombstone.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteLog_notOwner_rejectsAndKeepsLogVisible() {
        User other = createAndSaveUser("softdel-other-" + UUID.randomUUID().toString().substring(0, 8) + "@ev-monitor.net");
        EvLog log = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 1, 10, 0)));

        assertThatThrownBy(() -> evLogService.deleteLog(log.getId(), other.getId()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(evLogRepository.findById(log.getId())).isPresent();
    }

    @Test
    void softDeletedLog_stillCountsAsDuplicateForImports() {
        LocalDateTime loggedAt = LocalDateTime.of(2026, 3, 1, 10, 0);
        EvLog log = evLogRepository.save(buildLog(loggedAt));
        evLogService.deleteLog(log.getId(), userId);

        assertThat(evLogRepository.existsByCarIdAndLoggedAtAndDataSource(carId, loggedAt, DataSource.EU_DATA_ACT_SYNC)).isTrue();
        assertThat(evLogRepository.existsByCarIdAndDataSourceAndLoggedAtBetween(
                carId, DataSource.EU_DATA_ACT_SYNC, loggedAt.minusMinutes(3), loggedAt.plusMinutes(3))).isTrue();
        assertThat(evLogRepository.existsByCarIdAndLoggedAtBetween(carId, loggedAt.minusMinutes(1), loggedAt.plusMinutes(1))).isTrue();
        assertThat(evLogRepository.existsByCarIdAndLoggedAtAndKwhCharged(carId, loggedAt, new BigDecimal("20.0"))).isTrue();
        assertThat(evLogRepository.existsByCarIdAndOdometerKmAndLoggedAtBetween(
                carId, 12345, loggedAt.minusMinutes(1), loggedAt.plusMinutes(1))).isTrue();
    }

    @Test
    void findDeletedByCarId_listsOnlyTombstonesNewestFirst() {
        EvLog older = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 1, 10, 0)));
        EvLog newer = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 2, 10, 0)));
        evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 3, 10, 0)));
        evLogService.deleteLog(older.getId(), userId);
        evLogService.deleteLog(newer.getId(), userId);

        assertThat(evLogRepository.findDeletedByCarId(carId))
                .extracting(EvLog::getId)
                .containsExactly(newer.getId(), older.getId());
        assertThat(evLogRepository.findAllByCarId(carId)).hasSize(1);
    }

    /**
     * "Integration trennen" wischt eine Datenquelle komplett. Bliebe der Tombstone stehen, würde
     * er beim Neu-Verbinden als Duplikat gelten und den Re-Import genau dieses Vorgangs blockieren.
     */
    @Test
    void wipeDataSource_alsoRemovesTombstones() {
        LocalDateTime loggedAt = LocalDateTime.of(2026, 3, 1, 10, 0);
        EvLog tombstone = evLogRepository.save(buildLog(loggedAt));
        evLogService.deleteLog(tombstone.getId(), userId);

        evLogRepository.deleteAllByUserIdAndDataSource(userId, DataSource.EU_DATA_ACT_SYNC);

        assertThat(evLogRepository.findByIdIncludingDeleted(tombstone.getId())).isEmpty();
        assertThat(evLogRepository.existsByCarIdAndLoggedAtAndDataSource(carId, loggedAt, DataSource.EU_DATA_ACT_SYNC)).isFalse();
    }

    @Test
    void wipeDataSources_alsoRemovesTombstones() {
        EvLog tombstone = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 1, 10, 0)));
        evLogService.deleteLog(tombstone.getId(), userId);

        evLogRepository.deleteAllByUserIdAndDataSourceIn(userId, java.util.List.of(DataSource.EU_DATA_ACT_SYNC));

        assertThat(evLogRepository.findByIdIncludingDeleted(tombstone.getId())).isEmpty();
    }

    @Test
    void deleteSoftDeletedByCarIds_removesOnlyTombstones() {
        EvLog tombstone = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 1, 10, 0)));
        EvLog kept = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 2, 10, 0)));
        evLogService.deleteLog(tombstone.getId(), userId);

        int removed = evLogRepository.deleteSoftDeletedByCarIds(java.util.List.of(carId));

        assertThat(removed).isEqualTo(1);
        assertThat(evLogRepository.findByIdIncludingDeleted(tombstone.getId())).isEmpty();
        assertThat(evLogRepository.findById(kept.getId())).isPresent();
    }

    @Test
    void restoreLog_makesLogVisibleAgain() {
        EvLog log = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 1, 10, 0)));
        evLogService.deleteLog(log.getId(), userId);

        evLogService.restoreLog(log.getId(), userId);

        assertThat(evLogRepository.findById(log.getId())).isPresent();
        assertThat(evLogRepository.findAllByCarId(carId)).hasSize(1);
        assertThat(evLogRepository.findByIdIncludingDeleted(log.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void restoreLog_notOwner_throwsForbidden() {
        User other = createAndSaveUser("softdel-other2-" + UUID.randomUUID().toString().substring(0, 8) + "@ev-monitor.net");
        EvLog log = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 1, 10, 0)));
        evLogService.deleteLog(log.getId(), userId);

        assertThatThrownBy(() -> evLogService.restoreLog(log.getId(), other.getId()))
                .isInstanceOf(ForbiddenException.class);
        assertThat(evLogRepository.findById(log.getId())).isEmpty();
    }

    @Test
    void restoreLog_neverDeleted_throwsNotFound() {
        EvLog log = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 1, 10, 0)));

        assertThatThrownBy(() -> evLogService.restoreLog(log.getId(), userId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void restoreLog_unknownId_throwsNotFound() {
        assertThatThrownBy(() -> evLogService.restoreLog(UUID.randomUUID(), userId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteLog_deductsCoins_restoreDoesNotReaward() {
        EvLog log = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 1, 10, 0)));
        coinLogService.awardCoins(userId, CoinType.ACHIEVEMENT_COIN, 5, "test", log.getId());

        evLogService.deleteLog(log.getId(), userId);
        assertThat(coinLogService.sumCoinsForSourceEntity(log.getId())).isZero();

        evLogService.restoreLog(log.getId(), userId);
        assertThat(coinLogService.sumCoinsForSourceEntity(log.getId())).isZero();
    }

    @Test
    void getDeletedLogs_listsTombstonesOfOwnCar() {
        EvLog kept = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 1, 10, 0)));
        EvLog gone = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 2, 10, 0)));
        evLogService.deleteLog(gone.getId(), userId);

        assertThat(evLogService.getDeletedLogs(carId, userId))
                .extracting(DeletedLogResponse::id).containsExactly(gone.getId());
        assertThat(kept).isNotNull();
    }

    @Test
    void getDeletedLogs_foreignCar_isForbidden() {
        User other = createAndSaveUser("softdel-o2-" + UUID.randomUUID().toString().substring(0, 8) + "@ev-monitor.net");

        assertThatThrownBy(() -> evLogService.getDeletedLogs(carId, other.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void purgeLog_removesTombstone_soReimportIsPossible() {
        LocalDateTime loggedAt = LocalDateTime.of(2026, 3, 1, 10, 0);
        EvLog log = evLogRepository.save(buildLog(loggedAt));
        evLogService.deleteLog(log.getId(), userId);

        evLogService.purgeLog(log.getId(), userId);

        assertThat(evLogRepository.findByIdIncludingDeleted(log.getId())).isEmpty();
        assertThat(evLogRepository.existsByCarIdAndLoggedAtAndDataSource(carId, loggedAt, DataSource.EU_DATA_ACT_SYNC)).isFalse();
    }

    @Test
    void purgeLog_activeLog_isConflict() {
        EvLog log = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 1, 10, 0)));

        assertThatThrownBy(() -> evLogService.purgeLog(log.getId(), userId))
                .isInstanceOf(ConflictException.class);
        assertThat(evLogRepository.findById(log.getId())).isPresent();
    }

    @Test
    void purgeLog_foreignLog_isForbidden() {
        User other = createAndSaveUser("softdel-o3-" + UUID.randomUUID().toString().substring(0, 8) + "@ev-monitor.net");
        EvLog log = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 1, 10, 0)));
        evLogService.deleteLog(log.getId(), userId);

        assertThatThrownBy(() -> evLogService.purgeLog(log.getId(), other.getId()))
                .isInstanceOf(ForbiddenException.class);
        assertThat(evLogRepository.findByIdIncludingDeleted(log.getId())).isPresent();
    }

    @Test
    void mergedSource_isNotInTrash_andCannotBeRestored() {
        EvLog target = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 1, 10, 0)));
        EvLog source = evLogRepository.save(buildLog(LocalDateTime.of(2026, 3, 1, 10, 30)));

        evLogService.mergeLog(target.getId(), source.getId(), userId, false);

        assertThat(evLogService.getDeletedLogs(carId, userId)).isEmpty();
        assertThatThrownBy(() -> evLogService.restoreLog(source.getId(), userId))
                .isInstanceOf(ConflictException.class);
        // Tombstone bleibt für den Dedup
        assertThat(evLogRepository.findByIdIncludingDeleted(source.getId())).isPresent();
    }

    private EvLog buildLog(LocalDateTime loggedAt) {
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(carId)
                .kwhCharged(new BigDecimal("20.0"))
                .odometerKm(12345)
                .loggedAt(loggedAt)
                .dataSource(DataSource.EU_DATA_ACT_SYNC)
                .measurementType(EnergyMeasurementType.AT_CHARGER)
                .build();
    }
}
