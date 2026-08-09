package com.evmonitor.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class BatterySohEntry {

    private final UUID id;
    private final UUID carId;
    private final BigDecimal sohPercent;
    private final LocalDate recordedAt;
    private final LocalDateTime createdAt;
    private final BatterySohSource source;

    /** Charging logs behind the estimate. Set for CHARGE_LOG, null for every other source. */
    private final Integer sampleSize;

    /** SoC hub the estimate rests on. Only meaningful for CHARGE_LOG, may be null. */
    private final BigDecimal socHubPercent;

    public BatterySohEntry(UUID id, UUID carId, BigDecimal sohPercent, LocalDate recordedAt,
            LocalDateTime createdAt, BatterySohSource source, Integer sampleSize,
            BigDecimal socHubPercent) {
        if (source == null) {
            throw new IllegalArgumentException("SoH entry needs a source");
        }
        // Mirrors chk_car_battery_soh_log_sample_size - fail here with a readable message
        // rather than as a constraint violation on flush.
        boolean expectsSampleSize = source == BatterySohSource.CHARGE_LOG;
        if (expectsSampleSize && (sampleSize == null || sampleSize < 1)) {
            throw new IllegalArgumentException("CHARGE_LOG entries need a sample size of at least 1");
        }
        if (!expectsSampleSize && sampleSize != null) {
            throw new IllegalArgumentException("sampleSize is only defined for CHARGE_LOG entries");
        }
        if (!expectsSampleSize && socHubPercent != null) {
            throw new IllegalArgumentException("socHubPercent is only defined for CHARGE_LOG entries");
        }
        this.id = id;
        this.carId = carId;
        this.sohPercent = sohPercent;
        this.recordedAt = recordedAt;
        this.createdAt = createdAt;
        this.source = source;
        this.sampleSize = sampleSize;
        this.socHubPercent = socHubPercent;
    }

    public static BatterySohEntry manual(UUID id, UUID carId, BigDecimal sohPercent,
            LocalDate recordedAt, LocalDateTime createdAt) {
        return new BatterySohEntry(id, carId, sohPercent, recordedAt, createdAt,
                BatterySohSource.MANUAL, null, null);
    }

    public static BatterySohEntry fromChargeLogs(UUID id, UUID carId, BigDecimal sohPercent,
            LocalDate recordedAt, LocalDateTime createdAt, int sampleSize, BigDecimal socHubPercent) {
        return new BatterySohEntry(id, carId, sohPercent, recordedAt, createdAt,
                BatterySohSource.CHARGE_LOG, sampleSize, socHubPercent);
    }

    public static BatterySohEntry fromVehicleBms(UUID id, UUID carId, BigDecimal sohPercent,
            LocalDate recordedAt, LocalDateTime createdAt) {
        return new BatterySohEntry(id, carId, sohPercent, recordedAt, createdAt,
                BatterySohSource.VEHICLE_BMS, null, null);
    }
}
