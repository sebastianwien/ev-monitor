package com.evmonitor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FixedCostRepository {
    FixedCost save(FixedCost fixedCost);

    Optional<FixedCost> findById(UUID id);

    List<FixedCost> findAllByCarId(UUID carId);

    void deleteById(UUID id);
}
