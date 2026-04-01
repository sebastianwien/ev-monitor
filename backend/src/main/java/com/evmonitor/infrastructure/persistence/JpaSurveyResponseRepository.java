package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaSurveyResponseRepository extends JpaRepository<SurveyResponseEntity, UUID> {
    boolean existsBySurveySlugAndUserId(String surveySlug, UUID userId);
}
