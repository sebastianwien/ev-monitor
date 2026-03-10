package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.CarBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaCarRepository extends JpaRepository<CarEntity, UUID> {
    List<CarEntity> findAllByUserId(UUID userId);

    List<CarEntity> findAllByModel(CarBrand.CarModel model);

    long countByUserId(UUID userId);
}
