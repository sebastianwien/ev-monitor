package com.evmonitor.application;

import com.evmonitor.infrastructure.persistence.JpaVehicleSpecificationRepository;
import com.evmonitor.infrastructure.persistence.VehicleSpecificationEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes the median implicit charging efficiency per vehicle specification and charging type
 * (DC and AC) from qualifying ev_log entries, then persists the results back into
 * vehicle_specification.charging_efficiency_dc / charging_efficiency_ac.
 *
 * A log qualifies when:
 * - soc_start_percent, soc_after_charge_percent, kwh_charged are all non-null
 * - kwh_charged > 0
 * - include_in_statistics = true
 * - (soc_after_charge_percent - soc_start_percent) >= 75
 * - charging_type IN ('DC', 'AC')
 * - the spec has a net_battery_capacity_kwh
 *
 * At least 5 qualifying logs are required per (spec, charging_type) group.
 * Fields that do not have 5+ qualifying logs are left unchanged (not nulled out).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpecChargingEfficiencyJob {

    private static final String QUERY = """
            SELECT
                c.vehicle_specification_id,
                e.charging_type,
                PERCENTILE_CONT(0.5) WITHIN GROUP (
                    ORDER BY (e.soc_after_charge_percent - e.soc_start_percent) / 100.0
                             * vs.net_battery_capacity_kwh / e.kwh_charged
                ) AS median_efficiency,
                COUNT(*) AS log_count
            FROM ev_log e
            JOIN car c ON c.id = e.car_id
            JOIN vehicle_specification vs ON vs.id = c.vehicle_specification_id
            WHERE e.soc_start_percent IS NOT NULL
              AND e.soc_after_charge_percent IS NOT NULL
              AND e.kwh_charged IS NOT NULL
              AND e.kwh_charged > 0
              AND e.include_in_statistics = true
              AND (e.soc_after_charge_percent - e.soc_start_percent) >= 75
              AND e.charging_type IN ('DC', 'AC')
              AND vs.net_battery_capacity_kwh IS NOT NULL
            GROUP BY c.vehicle_specification_id, e.charging_type
            HAVING COUNT(*) >= 5
            """;

    private final EntityManager entityManager;
    private final JpaVehicleSpecificationRepository jpaRepository;

    /**
     * Runs the aggregation and persists computed efficiencies.
     *
     * @return summary string, e.g. "Updated 3 specs (DC: 2, AC: 1)"
     */
    @Transactional
    public String run() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(QUERY).getResultList();

        // Collect results grouped by specId
        Map<UUID, BigDecimal> dcBySpec = new HashMap<>();
        Map<UUID, BigDecimal> acBySpec = new HashMap<>();

        for (Object[] row : rows) {
            UUID specId = toUuid(row[0]);
            String chargingType = (String) row[1];
            BigDecimal medianEfficiency = toBigDecimal(row[2]);

            if ("DC".equals(chargingType)) {
                dcBySpec.put(specId, medianEfficiency);
            } else if ("AC".equals(chargingType)) {
                acBySpec.put(specId, medianEfficiency);
            }
        }

        // Merge all affected spec IDs
        Map<UUID, BigDecimal[]> updates = new HashMap<>();
        for (UUID id : dcBySpec.keySet()) {
            updates.computeIfAbsent(id, k -> new BigDecimal[2])[0] = dcBySpec.get(id);
        }
        for (UUID id : acBySpec.keySet()) {
            updates.computeIfAbsent(id, k -> new BigDecimal[2])[1] = acBySpec.get(id);
        }

        // Load all affected specs in one query (avoid N+1)
        Map<UUID, VehicleSpecificationEntity> specById = new HashMap<>();
        jpaRepository.findAllById(updates.keySet())
                .forEach(e -> specById.put(e.getId(), e));

        int updatedSpecs = 0;
        int dcCount = 0;
        int acCount = 0;
        List<VehicleSpecificationEntity> entitiesToSave = new ArrayList<>();

        for (Map.Entry<UUID, BigDecimal[]> entry : updates.entrySet()) {
            UUID specId = entry.getKey();
            BigDecimal dcValue = entry.getValue()[0];
            BigDecimal acValue = entry.getValue()[1];

            VehicleSpecificationEntity entity = specById.get(specId);
            if (entity == null) {
                log.warn("SpecChargingEfficiencyJob: spec {} not found, skipping", specId);
                continue;
            }

            boolean changed = false;

            if (dcValue != null) {
                if (dcValue.compareTo(BigDecimal.valueOf(0.5)) < 0 || dcValue.compareTo(BigDecimal.ONE) >= 0) {
                    log.warn("Skipping implausible efficiency {} for spec {} charging_type DC", dcValue, specId);
                } else {
                    entity.setChargingEfficiencyDc(dcValue);
                    dcCount++;
                    changed = true;
                }
            }
            if (acValue != null) {
                if (acValue.compareTo(BigDecimal.valueOf(0.5)) < 0 || acValue.compareTo(BigDecimal.ONE) >= 0) {
                    log.warn("Skipping implausible efficiency {} for spec {} charging_type AC", acValue, specId);
                } else {
                    entity.setChargingEfficiencyAc(acValue);
                    acCount++;
                    changed = true;
                }
            }

            if (changed) {
                entity.setUpdatedAt(LocalDateTime.now());
                entitiesToSave.add(entity);
                updatedSpecs++;
            }
        }

        jpaRepository.saveAll(entitiesToSave);

        String summary = "Updated %d specs (DC: %d, AC: %d)".formatted(updatedSpecs, dcCount, acCount);
        log.info("SpecChargingEfficiencyJob completed: {}", summary);
        return summary;
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof byte[] bytes) {
            // H2 in-memory returns UUID columns as big-endian 16-byte arrays
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(bytes);
            return new UUID(bb.getLong(), bb.getLong());
        }
        return UUID.fromString(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
