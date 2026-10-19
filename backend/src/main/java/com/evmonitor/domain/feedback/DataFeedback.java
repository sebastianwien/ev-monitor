package com.evmonitor.domain.feedback;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Nutzerbewertung "Korrekt erkannt?" eines automatisch erfassten Ladevorgangs oder einer Fahrt.
 * Eine je Gegenstand. Die Quelle wird beim Bewerten eingefroren, damit die Auswertung je Quelle
 * ohne Join auskommt und auch nach dem Löschen des Gegenstands stimmt.
 */
@Entity
@Table(name = "data_feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataFeedback {

    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 10)
    private FeedbackSubjectType subjectType;
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;
    @Column(name = "data_source", nullable = false, length = 40)
    private String dataSource;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FeedbackRating rating;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", nullable = false)
    private List<String> tags = new ArrayList<>();
    @Column(name = "comment")
    private String comment;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static DataFeedback create(UUID userId, FeedbackSubjectType subjectType, UUID subjectId, String dataSource) {
        DataFeedback feedback = new DataFeedback();
        feedback.id = UUID.randomUUID();
        feedback.userId = userId;
        feedback.subjectType = subjectType;
        feedback.subjectId = subjectId;
        feedback.dataSource = dataSource;
        feedback.createdAt = LocalDateTime.now();
        return feedback;
    }

    /** Positiv hat keine Tags und keinen Kommentar - die gehören nur zu "Problem melden". */
    public void rate(FeedbackRating rating, List<FeedbackTag> tags, String comment) {
        this.rating = rating;
        boolean negative = rating == FeedbackRating.NEGATIVE;
        this.tags = negative ? new ArrayList<>(tags.stream().distinct().map(Enum::name).toList()) : new ArrayList<>();
        this.comment = negative ? comment : null;
        this.updatedAt = LocalDateTime.now();
    }

    public List<FeedbackTag> tagList() {
        return tags.stream().map(FeedbackTag::valueOf).toList();
    }
}
