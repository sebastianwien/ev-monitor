package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.BatterySohEntry;
import com.evmonitor.domain.BatterySohRepository;
import com.evmonitor.domain.BatterySohSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostgresBatterySohRepositoryImpl implements BatterySohRepository {

    private final JpaBatterySohRepository jpaRepository;

    @Override
    public List<BatterySohEntry> findByCarId(UUID carId) {
        return jpaRepository.findByCarIdOrderByRecordedAtDesc(carId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<BatterySohEntry> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public BatterySohEntry save(BatterySohEntry entry) {
        return toDomain(jpaRepository.save(toEntity(entry)));
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    private BatterySohEntity toEntity(BatterySohEntry domain) {
        BatterySohEntity entity = new BatterySohEntity();
        entity.setId(domain.getId());
        entity.setCarId(domain.getCarId());
        entity.setSohPercent(domain.getSohPercent());
        entity.setRecordedAt(domain.getRecordedAt());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setSource(domain.getSource().name());
        entity.setSampleSize(domain.getSampleSize());
        entity.setSocHubPercent(domain.getSocHubPercent());
        return entity;
    }

    private BatterySohEntry toDomain(BatterySohEntity entity) {
        return new BatterySohEntry(
                entity.getId(),
                entity.getCarId(),
                entity.getSohPercent(),
                entity.getRecordedAt(),
                entity.getCreatedAt(),
                BatterySohSource.valueOf(entity.getSource()),
                entity.getSampleSize(),
                entity.getSocHubPercent());
    }
}
