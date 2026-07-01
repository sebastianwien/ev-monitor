package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.AuthProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link JpaUserRepository#findAutoSyncSurveyCandidates} filters correctly
 * against a real PostgreSQL instance - specifically the {@code cast(autosyncStartedAt as
 * LocalDate)} on an {@link Instant} column, the tier whitelist, and the opt-in/seed guards.
 * A parsing-only check (context load) would not catch a wrong cast or tier predicate.
 *
 * <p>Uses Testcontainers with a Hibernate-generated schema (no Flyway) so it stays enabled
 * on CI; auto-skips when Docker is unavailable.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AutoSyncSurveyCandidateQueryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JpaUserRepository userRepository;

    private UUID persistUser(String suffix, String tier, boolean emailNotifications,
                             boolean seedData, Instant autosyncStartedAt) {
        UserEntity u = new UserEntity();
        UUID id = UUID.randomUUID();
        u.setId(id);
        u.setEmail("u-" + suffix + "@example.com");
        u.setUsername("user-" + suffix);
        u.setPasswordHash("hash");
        u.setAuthProvider(AuthProvider.LOCAL);
        u.setRole("USER");
        u.setEmailVerified(true);
        u.setSeedData(seedData);
        u.setEmailNotificationsEnabled(emailNotifications);
        u.setSubscriptionTier(tier);
        u.setReferralCode("REF-" + suffix);
        u.setAutosyncStartedAt(autosyncStartedAt);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(u).getId();
    }

    @Test
    void returnsOnlyActiveAutoSyncSubscribersWhoPurchasedOnTheTargetDay() {
        LocalDate targetDay = LocalDate.of(2026, 5, 20);
        // Midday UTC keeps the cast()->date stable regardless of the DB session timezone.
        Instant onDay = targetDay.atTime(12, 0).toInstant(ZoneOffset.UTC);
        Instant dayBefore = targetDay.minusDays(1).atTime(12, 0).toInstant(ZoneOffset.UTC);

        UUID autosync = persistUser("autosync", "AUTOSYNC", true, false, onDay);        // match
        UUID live = persistUser("live", "AUTOSYNC_LIVE", true, false, onDay);           // match
        persistUser("supporter", "SUPPORTER", true, false, onDay);                      // wrong tier
        persistUser("none", "NONE", true, false, onDay);                                // wrong tier
        persistUser("optout", "AUTOSYNC", false, false, onDay);                         // email opt-out
        persistUser("seed", "AUTOSYNC", true, true, onDay);                             // seed data
        persistUser("wrongday", "AUTOSYNC", true, false, dayBefore);                    // different day
        persistUser("nostamp", "AUTOSYNC", true, false, null);                          // never purchased

        List<UserEntity> result = userRepository.findAutoSyncSurveyCandidates(targetDay);

        assertThat(result).extracting(UserEntity::getId)
                .containsExactlyInAnyOrder(autosync, live);
    }
}
