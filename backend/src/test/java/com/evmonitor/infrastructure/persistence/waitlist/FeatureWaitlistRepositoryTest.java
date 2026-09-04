package com.evmonitor.infrastructure.persistence.waitlist;

import com.evmonitor.domain.WaitlistFeature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifiziert die derived Queries und den (user_id, feature)-Unique-Constraint gegen ein
 * echtes PostgreSQL - ein reiner Context-Load wuerde weder Constraint noch deleteBy-Semantik pruefen.
 *
 * <p>Testcontainers mit Hibernate-generiertem Schema (kein Flyway), damit es auf CI aktiv bleibt;
 * ueberspringt automatisch, wenn Docker fehlt.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class FeatureWaitlistRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private FeatureWaitlistRepository repo;

    private FeatureWaitlistEntry entry(UUID userId) {
        return FeatureWaitlistEntry.builder()
                .userId(userId)
                .feature(WaitlistFeature.XPENG_AUTOSYNC)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void findByUserIdAndFeature_returnsSavedEntry() {
        UUID user = UUID.randomUUID();
        repo.saveAndFlush(entry(user));

        assertThat(repo.findByUserIdAndFeature(user, WaitlistFeature.XPENG_AUTOSYNC)).isPresent();
        assertThat(repo.findByUserIdAndFeature(UUID.randomUUID(), WaitlistFeature.XPENG_AUTOSYNC)).isEmpty();
    }

    @Test
    void deleteByUserIdAndFeature_removesOnlyThatUsersEntry() {
        UUID user = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        repo.saveAndFlush(entry(user));
        repo.saveAndFlush(entry(other));

        repo.deleteByUserIdAndFeature(user, WaitlistFeature.XPENG_AUTOSYNC);

        assertThat(repo.findByUserIdAndFeature(user, WaitlistFeature.XPENG_AUTOSYNC)).isEmpty();
        assertThat(repo.findByUserIdAndFeature(other, WaitlistFeature.XPENG_AUTOSYNC)).isPresent();
    }

    @Test
    void uniqueConstraint_blocksDuplicateUserFeature() {
        UUID user = UUID.randomUUID();
        repo.saveAndFlush(entry(user));

        assertThatThrownBy(() -> repo.saveAndFlush(entry(user)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
