package com.evmonitor.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripStoryRepository extends JpaRepository<TripStory, UUID> {

    List<TripStory> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<TripStory> findBySlugAndStatus(String slug, String status);

    List<TripStory> findByStatusOrderByPublishedAtDesc(String status, Pageable pageable);

    boolean existsBySlug(String slug);
}
