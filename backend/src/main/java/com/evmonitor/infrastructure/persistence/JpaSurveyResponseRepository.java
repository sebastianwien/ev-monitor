package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface JpaSurveyResponseRepository extends JpaRepository<SurveyResponseEntity, UUID> {
    boolean existsBySurveySlugAndUserId(String surveySlug, UUID userId);

    List<SurveyResponseEntity> findBySurveySlugOrderByCreatedAtAsc(String surveySlug);

    interface SlugCount {
        String getSlug();
        long getResponses();
    }

    @Query("SELECT r.surveySlug AS slug, COUNT(r) AS responses FROM SurveyResponseEntity r GROUP BY r.surveySlug ORDER BY COUNT(r) DESC")
    List<SlugCount> countBySlug();
}
