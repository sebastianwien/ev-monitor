package com.evmonitor.application;

import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.infrastructure.persistence.JpaSurveyResponseRepository;
import com.evmonitor.infrastructure.persistence.SurveyResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SurveyService {

    /** Freitextantworten sind bewusst grosszuegig, aber nicht unbegrenzt (Storage-Missbrauch). */
    public static final int MAX_ANSWER_LENGTH = 2000;
    public static final int MAX_ANSWER_KEYS = 40;
    public static final int MAX_LIST_SIZE = 50;
    private static final int MAX_SLUG_LENGTH = 100; // entspricht survey_response.survey_slug

    private final JpaSurveyResponseRepository repo;

    public boolean hasResponded(String surveySlug, UUID userId) {
        return repo.existsBySurveySlugAndUserId(surveySlug, userId);
    }

    /**
     * Saves a survey response. Returns false if user already responded (idempotent).
     * Throws {@link ValidationException} if the payload exceeds the size limits.
     */
    public boolean submit(String surveySlug, UUID userId, Map<String, Object> answers) {
        validate(surveySlug, answers);
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

    private void validate(String surveySlug, Map<String, Object> answers) {
        if (surveySlug == null || surveySlug.isBlank() || surveySlug.length() > MAX_SLUG_LENGTH) {
            throw new ValidationException("Ungueltiger Umfrage-Slug.");
        }
        if (answers.size() > MAX_ANSWER_KEYS) {
            throw new ValidationException("Zu viele Antworten.");
        }
        for (Object value : answers.values()) {
            if (value instanceof Collection<?> list) {
                if (list.size() > MAX_LIST_SIZE) {
                    throw new ValidationException("Zu viele ausgewaehlte Optionen.");
                }
                list.forEach(this::validateScalar);
            } else {
                validateScalar(value);
            }
        }
    }

    private void validateScalar(Object value) {
        if (value instanceof String text && text.length() > MAX_ANSWER_LENGTH) {
            throw new ValidationException("Antwort ist zu lang (max. " + MAX_ANSWER_LENGTH + " Zeichen).");
        }
    }
}
