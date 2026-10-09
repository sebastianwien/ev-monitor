package com.evmonitor.domain.rating;

import com.evmonitor.domain.CarBrand;
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
import java.util.stream.Stream;

/**
 * Fahrerbewertung eines Autos. Eine Erstbewertung je Auto ({@link #FIRST_VERSION}), editierbar.
 * Modell, km und Herkunft sind beim Absenden eingefroren, damit die Bewertung nach Auto- und
 * Kontolöschung anonym im richtigen Modell weiterzählt.
 */
@Entity
@Table(name = "car_rating")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CarRating {

    public static final int FIRST_VERSION = 1;

    @Id
    private UUID id;
    @Column(name = "car_id")
    private UUID carId;
    @Column(name = "user_id")
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "car_model", nullable = false, length = 100)
    private CarBrand.CarModel carModel;
    @Column(name = "vehicle_specification_id")
    private UUID vehicleSpecificationId;
    @Column(nullable = false)
    private int version;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RebuyAnswer rebuy;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "rebuy_reasons")
    private List<String> rebuyReasons;
    @Column(name = "daily_fit", nullable = false)
    private int dailyFit;
    @Column(name = "long_distance")
    private Integer longDistance;
    @Column(nullable = false)
    private int software;
    @Column(name = "build_quality", nullable = false)
    private int buildQuality;
    @Column(name = "service")
    private Integer service;
    @Enumerated(EnumType.STRING)
    @Column(name = "total_cost", nullable = false, length = 20)
    private TotalCostVerdict totalCost;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "strengths")
    private List<String> strengths;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "weaknesses")
    private List<String> weaknesses;
    @Column(name = "km_driven", nullable = false)
    private int kmDriven;
    @Column(name = "log_count", nullable = false)
    private int logCount;
    @Column(name = "business_car", nullable = false)
    private boolean businessCar;
    @Column(nullable = false)
    private boolean seed;
    @Column(name = "anonymized_at")
    private LocalDateTime anonymizedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static CarRating create(UUID carId, UUID userId, RatingSnapshot snapshot, RatingAnswers answers) {
        CarRating rating = new CarRating();
        rating.id = UUID.randomUUID();
        rating.carId = carId;
        rating.userId = userId;
        rating.version = FIRST_VERSION;
        rating.createdAt = LocalDateTime.now();
        rating.update(snapshot, answers);
        return rating;
    }

    /** Bearbeiten überschreibt Antworten und Snapshot, es entsteht keine zweite Bewertung. */
    public void update(RatingSnapshot snapshot, RatingAnswers answers) {
        carModel = snapshot.carModel();
        vehicleSpecificationId = snapshot.vehicleSpecificationId();
        kmDriven = snapshot.kmDriven();
        logCount = snapshot.logCount();
        businessCar = snapshot.businessCar();
        seed = snapshot.seed();
        rebuy = answers.rebuy();
        rebuyReasons = names(answers.rebuyReasons());
        dailyFit = answers.dailyFit();
        longDistance = answers.longDistance();
        software = answers.software();
        buildQuality = answers.buildQuality();
        service = answers.service();
        totalCost = answers.totalCost();
        strengths = names(answers.strengths());
        weaknesses = names(answers.weaknesses());
        updatedAt = LocalDateTime.now();
    }

    /** Auto endgültig gelöscht: Bewertung zählt anonym weiter. Die FK setzt car_id ebenfalls auf NULL. */
    public void detachFromCar() {
        carId = null;
        anonymizedAt = LocalDateTime.now();
        updatedAt = anonymizedAt;
    }

    /** Konto gelöscht: Bewertung bleibt ohne Personenbezug. */
    public void anonymize() {
        userId = null;
        anonymizedAt = LocalDateTime.now();
        updatedAt = anonymizedAt;
    }

    /** Beantwortete Sterne-Fragen. Langstrecke und Werkstatt können fehlen. */
    public List<Integer> answeredStars() {
        return Stream.of(dailyFit, longDistance, software, buildQuality, service)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** Gesamtnote dieser Bewertung: Mittelwert der beantworteten Sterne-Fragen. */
    public double overallScore() {
        return answeredStars().stream().mapToInt(Integer::intValue).average().orElseThrow();
    }

    public RatingAnswers answers() {
        return new RatingAnswers(rebuy, enums(rebuyReasons, RebuyReason.class), dailyFit, longDistance, software,
                buildQuality, service, totalCost, enums(strengths, RatingChip.class), enums(weaknesses, RatingChip.class));
    }

    private static List<String> names(List<? extends Enum<?>> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values.stream().map(Enum::name).toList());
    }

    private static <E extends Enum<E>> List<E> enums(List<String> names, Class<E> type) {
        return names == null ? List.of() : names.stream().map(n -> Enum.valueOf(type, n)).toList();
    }
}
