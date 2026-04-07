package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.ChargingSessionGroup;
import com.evmonitor.domain.ChargingSessionGroupRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PostgresChargingSessionGroupRepositoryImpl implements ChargingSessionGroupRepository {

    private final JpaChargingSessionGroupRepository jpa;

    public PostgresChargingSessionGroupRepositoryImpl(JpaChargingSessionGroupRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ChargingSessionGroup save(ChargingSessionGroup group) {
        ChargingSessionGroupEntity entity = toEntity(group);
        return toDomain(jpa.save(entity));
    }

    @Override
    public Optional<ChargingSessionGroup> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<ChargingSessionGroup> findAllByCarId(UUID carId) {
        return jpa.findAllByCarId(carId).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteAllByUserIdAndDataSource(UUID userId, String dataSource) {
        jpa.deleteAllByUserIdAndDataSource(userId, dataSource);
    }

    @Override
    public void updateCarId(UUID groupId, UUID targetCarId) {
        jpa.updateCarId(groupId, targetCarId);
    }

    @Override
    public Optional<ChargingSessionGroup> findOpenGroupForCar(UUID carId, LocalDateTime threshold,
            LocalDate sessionDay, String dataSource) {
        LocalDateTime dayStart = sessionDay.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        return jpa.findOpenGroupsForCar(carId, threshold, dayStart, dayEnd, dataSource)
                .stream()
                .findFirst()
                .map(this::toDomain);
    }

    private ChargingSessionGroupEntity toEntity(ChargingSessionGroup domain) {
        ChargingSessionGroupEntity e = new ChargingSessionGroupEntity();
        e.setId(domain.getId());
        e.setCarId(domain.getCarId());
        e.setTotalKwhCharged(domain.getTotalKwhCharged());
        e.setTotalDurationMinutes(domain.getTotalDurationMinutes());
        e.setSessionStart(domain.getSessionStart());
        e.setSessionEnd(domain.getSessionEnd());
        e.setSessionCount(domain.getSessionCount());
        e.setGeohash(domain.getGeohash());
        e.setCostEur(domain.getCostEur());
        e.setDataSource(domain.getDataSource());
        e.setCreatedAt(domain.getCreatedAt());
        e.setUpdatedAt(domain.getUpdatedAt());
        return e;
    }

    private ChargingSessionGroup toDomain(ChargingSessionGroupEntity e) {
        return new ChargingSessionGroup(
                e.getId(),
                e.getCarId(),
                e.getTotalKwhCharged(),
                e.getTotalDurationMinutes(),
                e.getSessionStart(),
                e.getSessionEnd(),
                e.getSessionCount(),
                e.getGeohash(),
                e.getCostEur(),
                e.getDataSource(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
