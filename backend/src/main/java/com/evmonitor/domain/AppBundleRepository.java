package com.evmonitor.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface AppBundleRepository extends JpaRepository<AppBundle, Long> {

    /** Das zuletzt veroeffentlichte Bundle (neuestes per createdAt). */
    Optional<AppBundle> findTopByOrderByCreatedAtDesc();

    Optional<AppBundle> findByVersion(String version);

    /** Loescht Bundles aelter als der Stichtag, schliesst aber das neueste (per id) aus. */
    int deleteByCreatedAtBeforeAndIdNot(OffsetDateTime cutoff, Long keepId);
}
