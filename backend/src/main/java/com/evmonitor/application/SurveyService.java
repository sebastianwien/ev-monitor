package com.evmonitor.application;

import com.evmonitor.infrastructure.persistence.JpaSurveyResponseRepository;
import com.evmonitor.infrastructure.persistence.SurveyResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SurveyService {

    private final JpaSurveyResponseRepository repo;

    public boolean hasResponded(String surveySlug, UUID userId) {
        return repo.existsBySurveySlugAndUserId(surveySlug, userId);
    }

    /**
     * Saves a survey response. Returns false if user already responded (idempotent).
     */
    public boolean submit(String surveySlug, UUID userId, Map<String, Object> answers) {
        if (repo.existsBySurveySlugAndUserId(surveySlug, userId)) {
            return false;
        }
        try {
            SurveyResponseEntity entity = new SurveyResponseEntity();
            entity.setSurveySlug(surveySlug);
            entity.setUserId(userId);
            entity.setAnswers(answers);
            repo.save(entity);
            return true;
        } catch (DataIntegrityViolationException e) {
            // Race condition: duplicate submission
            return false;
        }
    }
}
