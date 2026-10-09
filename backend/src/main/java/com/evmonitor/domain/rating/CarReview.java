package com.evmonitor.domain.rating;

import com.evmonitor.domain.exception.ConflictException;
import com.evmonitor.domain.exception.ValidationException;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Eine Fassung des Erfahrungstexts zu einer Bewertung. Jede Einreichung ist eine eigene Zeile,
 * öffentlich ist nur {@link ReviewStatus#APPROVED} mit {@code publishedBody}.
 */
@Entity
@Table(name = "car_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CarReview {

    /** Ab dieser Länge erscheint der Text als Erfahrungsbericht, darunter als Zitat. */
    public static final int REPORT_MIN_CHARS = 600;
    /** Einzige erlaubte Änderung bei der Freigabe: Stellen durch diese Markierung ersetzen. */
    public static final String REDACTION = "[entfernt]";

    @Id
    private UUID id;
    @Column(name = "car_rating_id", nullable = false)
    private UUID carRatingId;
    @Column(nullable = false, columnDefinition = "text")
    private String body;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "pros")
    private List<String> pros;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "cons")
    private List<String> cons;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status;
    @Column(name = "published_body", columnDefinition = "text")
    private String publishedBody;
    @Column(name = "rejection_reason", length = 30)
    private String rejectionReason;
    @Column(name = "rejection_note", columnDefinition = "text")
    private String rejectionNote;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "llm_check", columnDefinition = "jsonb")
    private String llmCheck;
    @Column(name = "delete_with_car", nullable = false)
    private boolean deleteWithCar;
    @Column(name = "proof_km_driven")
    private Integer proofKmDriven;
    @Column(name = "proof_log_count")
    private Integer proofLogCount;
    @Column(name = "proof_consumption", precision = 5, scale = 2)
    private BigDecimal proofConsumption;
    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static CarReview submit(UUID carRatingId, ReviewDraft draft, ReviewProof proof, boolean deleteWithCar) {
        CarReview review = new CarReview();
        review.id = UUID.randomUUID();
        review.carRatingId = carRatingId;
        review.body = draft.body();
        review.pros = new ArrayList<>(draft.pros());
        review.cons = new ArrayList<>(draft.cons());
        review.status = ReviewStatus.PENDING;
        review.deleteWithCar = deleteWithCar;
        review.proofKmDriven = proof.kmDriven();
        review.proofLogCount = proof.logCount();
        review.proofConsumption = proof.consumptionKwhPer100km() == null ? null
                : proof.consumptionKwhPer100km().setScale(2, RoundingMode.HALF_UP);
        review.createdAt = LocalDateTime.now();
        review.updatedAt = review.createdAt;
        return review;
    }

    /** Offen oder öffentlich - also die Fassung, die der Fahrer gerade "hat". */
    public boolean isActive() {
        return status == ReviewStatus.PENDING || status == ReviewStatus.APPROVED;
    }

    public boolean hasSameContent(ReviewDraft draft) {
        return body.equals(draft.body()) && listOrEmpty(pros).equals(draft.pros())
                && listOrEmpty(cons).equals(draft.cons());
    }

    /** Eine neuere Fassung ersetzt diese offene. */
    public void supersede() {
        status = ReviewStatus.SUPERSEDED;
        updatedAt = LocalDateTime.now();
    }

    public void changeDeleteWithCar(boolean deleteWithCar) {
        if (this.deleteWithCar == deleteWithCar) return;
        this.deleteWithCar = deleteWithCar;
        updatedAt = LocalDateTime.now();
    }

    /**
     * Freigabe durch einen Admin. {@code publishedBody} null heißt: Text unverändert.
     *
     * @throws ConflictException wenn die Fassung nicht offen ist
     * @throws ValidationException wenn sich die Fassung nicht nur durch Schwärzungen unterscheidet
     */
    public void approve(String publishedBody, UUID adminId) {
        requirePending();
        String published = publishedBody == null ? body : publishedBody;
        if (!isRedactionOf(body, published)) {
            throw new ValidationException("REVIEW_REDACTION_ONLY",
                    "Erlaubt ist nur das Ersetzen von Stellen durch " + REDACTION + ".");
        }
        status = ReviewStatus.APPROVED;
        this.publishedBody = published;
        markReviewed(adminId);
    }

    /** @throws ValidationException bei {@link RejectionReason#OTHER} ohne Notiz */
    public void reject(RejectionReason reason, String note, UUID adminId) {
        requirePending();
        String trimmedNote = note == null ? null : note.strip();
        if (reason == null || (reason == RejectionReason.OTHER && (trimmedNote == null || trimmedNote.isEmpty()))) {
            throw new ValidationException("REVIEW_REJECTION_NOTE", "Grund fehlt, bei Sonstiges ist eine Notiz Pflicht.");
        }
        status = ReviewStatus.REJECTED;
        rejectionReason = reason.name();
        rejectionNote = trimmedNote == null || trimmedNote.isEmpty() ? null : trimmedNote;
        markReviewed(adminId);
    }

    /** Ältere freigegebene Fassung, die von einer neuen Freigabe abgelöst wird. */
    public void replaceApproved() {
        if (status == ReviewStatus.APPROVED) supersede();
    }

    public void storeLlmCheck(String json) {
        llmCheck = json;
        updatedAt = LocalDateTime.now();
    }

    /** Wahr, wenn {@code published} aus {@code body} nur durch Ersetzen nicht leerer Stellen mit [entfernt] entsteht. */
    public static boolean isRedactionOf(String body, String published) {
        if (body == null || published == null) return false;
        String[] parts = published.split(Pattern.quote(REDACTION), -1);
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) regex.append("(.+?)");
            regex.append(Pattern.quote(parts[i]));
        }
        regex.append("$");
        return Pattern.compile(regex.toString(), Pattern.DOTALL).matcher(body).matches();
    }

    private void requirePending() {
        if (status != ReviewStatus.PENDING) {
            throw new ConflictException("REVIEW_NOT_PENDING", "Diese Fassung ist nicht mehr offen.");
        }
    }

    private void markReviewed(UUID adminId) {
        reviewedBy = adminId;
        reviewedAt = LocalDateTime.now();
        updatedAt = reviewedAt;
    }

    public boolean isReport() {
        return body.length() >= REPORT_MIN_CHARS;
    }

    private static List<String> listOrEmpty(List<String> list) {
        return list == null ? List.of() : list;
    }
}
