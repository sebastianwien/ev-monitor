package com.evmonitor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatterySohRepository {
    List<BatterySohEntry> findByCarId(UUID carId);
    Optional<BatterySohEntry> findById(UUID id);
    BatterySohEntry save(BatterySohEntry entry);
    void deleteById(UUID id);
}
