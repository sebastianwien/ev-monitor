package com.evmonitor.application.tessie;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.InternalTripRequest;
import com.evmonitor.application.ingest.ChargingEntry;
import com.evmonitor.application.ingest.IngestCommand;
import com.evmonitor.application.ingest.IngestDoor;
import com.evmonitor.application.ingest.IngestGateway;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EnergyMeasurementType;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Processes raw Tessie imports (tessie_raw_imports) into ev_log/ev_trip rows for one car.
 *
 * Two-stage architecture:
 *  - Stage A (SQL): merge consecutive sub-sessions via window functions, returns aggregated rows.
 *  - Stage B (Java): compute geohash with the project's canonical {@link GeoHash} library,
 *    classify public (DC always public; AC > 11 kW public; otherwise unknown - Tessie says nothing
 *    about home vs. elsewhere), hand charges and drives to the {@link IngestGateway}.
 *
 * After processing, sets {@code tessie_raw_imports.processed = true} for the (user, vin) scope.
 */
@Service
@Slf4j
public class TessieProcessorService {

    /** AC charging strictly above this kW level is treated as public charging. */
    static final BigDecimal AC_PUBLIC_THRESHOLD_KW = new BigDecimal("11.0");

    private final NamedParameterJdbcTemplate jdbc;
    private final IngestGateway ingestGateway;
    private final String chargesMergeSql;
    private final String drivesMergeSql;
    private final String routeTypeBackfillSql;

    public TessieProcessorService(
            NamedParameterJdbcTemplate jdbc,
            IngestGateway ingestGateway,
            @Value("classpath:sql/tessie/process_charges_merge.sql") Resource chargesMergeResource,
            @Value("classpath:sql/tessie/process_drives_merge.sql") Resource drivesMergeResource,
            @Value("classpath:sql/tessie/route_type_backfill.sql") Resource routeTypeBackfillResource
    ) {
        this.jdbc = jdbc;
        this.ingestGateway = ingestGateway;
        this.chargesMergeSql = readResource(chargesMergeResource);
        this.drivesMergeSql = readResource(drivesMergeResource);
        this.routeTypeBackfillSql = readResource(routeTypeBackfillResource);
    }

    /** Eine Transaktion für Ladungen, Fahrten, Streckenart-Nachtrag und processed-Flag. */
    @Transactional
    public TessieProcessorResult processForCar(UUID userId, String vin, UUID carId) {
        int evLogsCreated;
        int evTripsCreated;
        try {
            // Das Gateway prüft den Besitz auch bei leerer Liste, also vor den Fahrten.
            evLogsCreated = processCharges(userId, vin, carId);
            evTripsCreated = processDrives(userId, vin, carId);
        } catch (NotFoundException e) {
            throw new IllegalArgumentException("Car not found: " + carId);
        } catch (ForbiddenException e) {
            throw new IllegalArgumentException("Car does not belong to user");
        }

        if (evLogsCreated > 0 && evTripsCreated > 0) {
            jdbc.update(routeTypeBackfillSql, new MapSqlParameterSource("carId", carId));
        }

        markProcessed(userId, vin);

        log.info("Tessie processor user={} vin={} car={}: ev_logs={} ev_trips={}",
                userId, vin, carId, evLogsCreated, evTripsCreated);
        return new TessieProcessorResult(evLogsCreated, evTripsCreated);
    }

    // ---------- charges -----------------------------------------------------

    private int processCharges(UUID userId, String vin, UUID carId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("vin", vin);

        List<MergedCharge> merged = jdbc.query(chargesMergeSql, params, (rs, n) -> new MergedCharge(
                rs.getLong("started_at_epoch"),
                rs.getLong("ended_at_epoch"),
                rs.getBigDecimal("kwh_charged"),
                getInteger(rs, "soc_start"),
                getInteger(rs, "soc_end"),
                rs.getBigDecimal("odometer_km"),
                rs.getBigDecimal("lat"),
                rs.getBigDecimal("lon"),
                rs.getBoolean("is_supercharger"),
                rs.getBoolean("is_fast_charger"),
                rs.getBigDecimal("charger_power_kw")
        ));

        List<ChargingEntry> entries = new ArrayList<>(merged.size());
        for (MergedCharge c : merged) {
            BigDecimal effectivePower = effectivePowerKw(c.kwhCharged(), c.startedAtEpoch(), c.endedAtEpoch(), c.chargerPowerKw());
            boolean isDc = c.isSupercharger() || c.isFastCharger();
            boolean isPublic = isDc || (effectivePower != null && effectivePower.compareTo(AC_PUBLIC_THRESHOLD_KW) > 0);
            entries.add(ChargingEntry.builder()
                    .loggedAt(LocalDateTime.ofEpochSecond(c.startedAtEpoch(), 0, ZoneOffset.UTC))
                    .kwhAtVehicle(c.kwhCharged() != null ? c.kwhCharged().setScale(2, RoundingMode.HALF_UP) : null)
                    .measurementType(EnergyMeasurementType.AT_VEHICLE)
                    .chargeDurationMinutes((int) Math.round((c.endedAtEpoch() - c.startedAtEpoch()) / 60.0))
                    .odometerKm(c.odometerKm() != null ? c.odometerKm().setScale(0, RoundingMode.HALF_UP).intValue() : null)
                    .socBefore(c.socStart() != null ? BigDecimal.valueOf(c.socStart()) : null)
                    .socAfter(c.socEnd() != null ? BigDecimal.valueOf(c.socEnd()) : null)
                    .geohash(geohash(c.lat(), c.lon(), isPublic ? 7 : 6))
                    // Langsames AC sagt nichts über daheim: ohne Angabe bleibt es unbekannt, der Heimtarif greift nicht.
                    .publicCharging(isPublic ? Boolean.TRUE : null)
                    .chargingType(isDc ? ChargingType.DC : ChargingType.AC)
                    .build());
        }

        // Auch leer: das Gateway prüft den Besitz und protokolliert den Import.
        return ingestGateway.ingestCharging(
                new IngestCommand(userId, carId, DataSource.TESSIE, IngestDoor.IMPORT_BATCH, entries)).imported();
    }

    // ---------- drives ------------------------------------------------------

    private int processDrives(UUID userId, String vin, UUID carId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("vin", vin);

        List<MergedDrive> merged = jdbc.query(drivesMergeSql, params, (rs, n) -> new MergedDrive(
                rs.getLong("started_at_epoch"),
                rs.getLong("ended_at_epoch"),
                rs.getBigDecimal("soc_start"),
                rs.getBigDecimal("soc_end"),
                rs.getBigDecimal("odo_start_km"),
                rs.getBigDecimal("odo_end_km"),
                rs.getBigDecimal("distance_km"),
                rs.getBigDecimal("energy_kwh"),
                rs.getBigDecimal("lat_start"),
                rs.getBigDecimal("lon_start"),
                rs.getBigDecimal("lat_end"),
                rs.getBigDecimal("lon_end"),
                rs.getBigDecimal("temp_celsius"),
                rs.getBigDecimal("weighted_avg_speed"),
                rs.getBigDecimal("max_speed")
        ));

        int inserted = 0;
        for (MergedDrive d : merged) {
            BigDecimal[] speeds = sanitizeSpeedPair(d.weightedAvgSpeed(), d.maxSpeed());
            InternalTripRequest trip = InternalTripRequest.builder()
                    .carId(carId)
                    .userId(userId)
                    .dataSource(DataSource.TESSIE.name())
                    .tripStartedAt(OffsetDateTime.ofInstant(Instant.ofEpochSecond(d.startedAtEpoch()), ZoneOffset.UTC))
                    .tripEndedAt(OffsetDateTime.ofInstant(Instant.ofEpochSecond(d.endedAtEpoch()), ZoneOffset.UTC))
                    .socStart(d.socStart())
                    .socEnd(d.socEnd())
                    .odometerStartKm(scale1(d.odoStartKm()))
                    .odometerEndKm(scale1(d.odoEndKm()))
                    .distanceKm(scale1(d.distanceKm()))
                    .estimatedConsumedKwh(scale2(d.energyKwh()))
                    .locationStartGeohash(geohash(d.latStart(), d.lonStart(), 6))
                    .locationEndGeohash(geohash(d.latEnd(), d.lonEnd(), 6))
                    .outsideTempCelsius(scale1(d.tempCelsius()))
                    .avgSpeedKmh(speeds[0])
                    .maxSpeedKmh(speeds[1])
                    .status("COMPLETED")
                    .build();
            // Ohne externalId keine Dedup, wie bisher: erneute Läufe verhindert das processed-Flag.
            if (ingestGateway.ingestTrip(trip, classifyRouteType(d.weightedAvgSpeed())).created()) inserted++;
        }
        return inserted;
    }

    // ---------- helpers -----------------------------------------------------

    private void markProcessed(UUID userId, String vin) {
        jdbc.update("""
                UPDATE tessie_raw_imports
                SET processed = true
                WHERE user_id = :userId AND vin = :vin AND processed = false
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("vin", vin));
    }

    static BigDecimal effectivePowerKw(BigDecimal kwhCharged, long startedAtEpoch, long endedAtEpoch, BigDecimal tessiePower) {
        BigDecimal derived = null;
        long durationSeconds = endedAtEpoch - startedAtEpoch;
        if (kwhCharged != null && durationSeconds > 0) {
            double hours = durationSeconds / 3600.0;
            derived = BigDecimal.valueOf(kwhCharged.doubleValue() / hours).setScale(2, RoundingMode.HALF_UP);
        }
        if (tessiePower == null && derived == null) return null;
        if (tessiePower == null) return derived;
        if (derived == null) return tessiePower;
        return tessiePower.compareTo(derived) >= 0 ? tessiePower : derived;
    }

    /**
     * Cappt aggregierte Trip-Geschwindigkeiten via {@link EvTrip#clampSpeedKmh} und
     * stellt zusaetzlich die avg <= max Invariante des V118-DB-CHECK sicher.
     * Verletzungen werden defensiv mit null beantwortet - lieber kein Wert als ein
     * Insert-Fail. Pair-Invariante lebt hier (Trip-Aggregat-Concern), das einzelne
     * Range-Clamp in der Domain.
     *
     * @return zweistelliges Array [avgKmh, maxKmh], jeweils ggf. null
     */
    static BigDecimal[] sanitizeSpeedPair(BigDecimal avgKmh, BigDecimal maxKmh) {
        BigDecimal avg = EvTrip.clampSpeedKmh(avgKmh);
        BigDecimal max = EvTrip.clampSpeedKmh(maxKmh);
        if (avg != null && max != null && avg.compareTo(max) > 0) {
            // Inkonsistente Quelldaten - kein Insert mit kaputter Invariante.
            return new BigDecimal[]{null, null};
        }
        return new BigDecimal[]{
                avg != null ? avg.setScale(2, RoundingMode.HALF_UP) : null,
                max != null ? max.setScale(2, RoundingMode.HALF_UP) : null
        };
    }

    static String classifyRouteType(BigDecimal weightedAvgSpeedKmh) {
        if (weightedAvgSpeedKmh == null) return null;
        double s = weightedAvgSpeedKmh.doubleValue();
        if (s >= 90) return "HIGHWAY";
        if (s < 60) return "CITY";
        return "COMBINED";
    }

    private static String geohash(BigDecimal lat, BigDecimal lon, int precision) {
        if (lat == null || lon == null) return null;
        return GeoHash.withCharacterPrecision(lat.doubleValue(), lon.doubleValue(), precision).toBase32();
    }

    private static BigDecimal scale1(BigDecimal v) {
        return v == null ? null : v.setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale2(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static Integer getInteger(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static String readResource(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read SQL resource: " + resource.getDescription(), e);
        }
    }

    record MergedCharge(
            long startedAtEpoch, long endedAtEpoch,
            BigDecimal kwhCharged, Integer socStart, Integer socEnd,
            BigDecimal odometerKm, BigDecimal lat, BigDecimal lon,
            boolean isSupercharger, boolean isFastCharger, BigDecimal chargerPowerKw
    ) {}

    record MergedDrive(
            long startedAtEpoch, long endedAtEpoch,
            BigDecimal socStart, BigDecimal socEnd,
            BigDecimal odoStartKm, BigDecimal odoEndKm,
            BigDecimal distanceKm, BigDecimal energyKwh,
            BigDecimal latStart, BigDecimal lonStart,
            BigDecimal latEnd, BigDecimal lonEnd,
            BigDecimal tempCelsius, BigDecimal weightedAvgSpeed,
            BigDecimal maxSpeed
    ) {}

    /** Side-effect counts of one processForCar run. */
    public record TessieProcessorResult(int evLogsCreated, int evTripsCreated) {}
}
