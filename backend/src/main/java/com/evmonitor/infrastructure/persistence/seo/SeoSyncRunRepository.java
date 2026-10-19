package com.evmonitor.infrastructure.persistence.seo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SeoSyncRunRepository extends JpaRepository<SeoSyncRunEntity, UUID> {

    List<SeoSyncRunEntity> findTop10ByOrderByStartedAtDesc();
}
