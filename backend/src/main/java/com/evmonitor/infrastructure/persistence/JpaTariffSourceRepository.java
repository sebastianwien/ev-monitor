package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaTariffSourceRepository extends JpaRepository<TariffSourceEntity, UUID> {

    List<TariffSourceEntity> findByEnabledTrueOrderByEmpNameAsc();

    List<TariffSourceEntity> findAllByOrderByEmpNameAsc();

    boolean existsByUrl(String url);
}
