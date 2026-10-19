package com.evmonitor.domain.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataFeedbackRepository extends JpaRepository<DataFeedback, UUID> {

    Optional<DataFeedback> findBySubjectTypeAndSubjectId(FeedbackSubjectType subjectType, UUID subjectId);

    List<DataFeedback> findAllByUserId(UUID userId);
}
