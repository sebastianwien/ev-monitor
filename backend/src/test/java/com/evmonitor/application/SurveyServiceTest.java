package com.evmonitor.application;

import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.infrastructure.persistence.JpaSurveyResponseRepository;
import com.evmonitor.infrastructure.persistence.SurveyResponseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SurveyServiceTest {

    private JpaSurveyResponseRepository repo;
    private SurveyService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repo = mock(JpaSurveyResponseRepository.class);
        service = new SurveyService(repo);
    }

    @Test
    void savesValidResponse() {
        when(repo.existsBySurveySlugAndUserId("s", userId)).thenReturn(false);

        boolean saved = service.submit("s", userId, Map.of("q1", "a", "q2", List.of("x", "y")));

        assertThat(saved).isTrue();
        verify(repo).save(any(SurveyResponseEntity.class));
    }

    @Test
    void returnsFalseWhenAlreadyResponded() {
        when(repo.existsBySurveySlugAndUserId("s", userId)).thenReturn(true);

        assertThat(service.submit("s", userId, Map.of("q1", "a"))).isFalse();
        verify(repo, never()).save(any());
    }

    @Test
    void returnsFalseOnRaceCondition() {
        when(repo.existsBySurveySlugAndUserId("s", userId)).thenReturn(false);
        when(repo.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThat(service.submit("s", userId, Map.of("q1", "a"))).isFalse();
    }

    @Test
    void rejectsFreeTextLongerThanLimit() {
        String tooLong = "x".repeat(SurveyService.MAX_ANSWER_LENGTH + 1);

        assertThatThrownBy(() -> service.submit("s", userId, Map.of("q1", tooLong)))
                .isInstanceOf(ValidationException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void rejectsLongTextInsideList() {
        String tooLong = "x".repeat(SurveyService.MAX_ANSWER_LENGTH + 1);

        assertThatThrownBy(() -> service.submit("s", userId, Map.of("q1", List.of(tooLong))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsTooManyAnswerKeys() {
        Map<String, Object> answers = new HashMap<>();
        for (int i = 0; i <= SurveyService.MAX_ANSWER_KEYS; i++) {
            answers.put("q" + i, "a");
        }

        assertThatThrownBy(() -> service.submit("s", userId, answers))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsTooManySelectedOptions() {
        List<String> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i <= SurveyService.MAX_LIST_SIZE; i++) {
            tooMany.add("o" + i);
        }

        assertThatThrownBy(() -> service.submit("s", userId, Map.of("q1", tooMany)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsOverlongSlug() {
        String slug = "s".repeat(101);

        assertThatThrownBy(() -> service.submit(slug, userId, Map.of("q1", "a")))
                .isInstanceOf(ValidationException.class);
    }
}
