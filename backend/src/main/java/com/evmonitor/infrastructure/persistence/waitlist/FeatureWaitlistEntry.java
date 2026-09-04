package com.evmonitor.infrastructure.persistence.waitlist;

import com.evmonitor.domain.WaitlistFeature;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ein Opt-in eines Users auf die Warteliste eines noch nicht verfuegbaren Features.
 * Eindeutig pro (user_id, feature) - wiederholtes Eintragen ist idempotent.
 */
@Entity
@Table(name = "feature_waitlist",
        uniqueConstraints = @UniqueConstraint(name = "uq_feature_waitlist_user_feature",
                columnNames = {"user_id", "feature"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureWaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 64)
    private WaitlistFeature feature;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
