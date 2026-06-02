package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.FixedCost;
import com.evmonitor.domain.FixedCostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostgresFixedCostRepositoryImpl implements FixedCostRepository {

    private final JpaFixedCostRepository jpaFixedCostRepository;

    @Override
    public FixedCost save(FixedCost fixedCost) {
        return toDomain(jpaFixedCostRepository.save(toEntity(fixedCost)));
    }

    @Override
    public Optional<FixedCost> findById(UUID id) {
        return jpaFixedCostRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<FixedCost> findAllByCarId(UUID carId) {
        return jpaFixedCostRepository.findAllByCarId(carId, Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpaFixedCostRepository.deleteById(id);
    }

    private FixedCostEntity toEntity(FixedCost fc) {
        FixedCostEntity e = new FixedCostEntity();
        e.setId(fc.getId());
        e.setCarId(fc.getCarId());
        e.setUserId(fc.getUserId());
        e.setDescription(fc.getDescription());
        e.setAmount(fc.getAmount());
        e.setCategory(fc.getCategory());
        e.setRecurrence(fc.getRecurrence());
        e.setDate(fc.getDate());
        e.setStartDate(fc.getStartDate());
        e.setEndDate(fc.getEndDate());
        e.setCreatedAt(fc.getCreatedAt());
        return e;
    }

    private FixedCost toDomain(FixedCostEntity e) {
        return FixedCost.builder()
                .id(e.getId())
                .carId(e.getCarId())
                .userId(e.getUserId())
                .description(e.getDescription())
                .amount(e.getAmount())
                .category(e.getCategory())
                .recurrence(e.getRecurrence())
                .date(e.getDate())
                .startDate(e.getStartDate())
                .endDate(e.getEndDate())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
