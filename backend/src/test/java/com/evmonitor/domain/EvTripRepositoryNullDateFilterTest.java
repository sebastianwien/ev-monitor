package com.evmonitor.domain;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.infrastructure.persistence.JpaUserRepository;
import com.evmonitor.infrastructure.persistence.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression test for PostgreSQL "could not determine data type of parameter"
 * when carId/from/to filters in findByUserIdAndFilters are null.
 *
 * Root cause: (:carId IS NULL OR ...) sends an untyped null - fixed by cast(:carId as UUID) etc.
 */
@Disabled("CI: Testcontainers/Docker not reliably available - run locally")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@org.springframework.test.context.ActiveProfiles("test")
class EvTripRepositoryNullDateFilterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.task.scheduling.pool.size", () -> "0");
    }

    @Autowired
    private EvTripRepository tripRepository;

    @Autowired
    private JpaUserRepository userRepository;

    private UUID userId;

    @BeforeEach
    void setup() {
        tripRepository.deleteAll();
        userRepository.deleteAll();

        userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("test@ev-monitor.net");
        user.setUsername("testuser");
        user.setPasswordHash("hash");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole("USER");
        user.setEmailVerified(true);
        user.setSeedData(false);
        user.setLeaderboardVisible(true);
        user.setReferralCode("TESTCODE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        EvTrip trip = new EvTrip();
        trip.setUserId(userId);
        trip.setCarId(UUID.randomUUID());
        trip.setDataSource(EvTrip.DATA_SOURCE_API_UPLOAD);
        trip.setTripStartedAt(OffsetDateTime.now().minusHours(2));
        trip.setTripEndedAt(OffsetDateTime.now().minusHours(1));
        tripRepository.save(trip);
    }

    @Test
    void findByUserIdAndFilters_withAllFiltersNull_doesNotThrow() {
        assertThatCode(() ->
            tripRepository.findByUserIdAndFilters(userId, null, null, null, PageRequest.of(0, 20))
        ).doesNotThrowAnyException();
    }

    @Test
    void findByUserIdAndFilters_withAllFiltersNull_returnsResults() {
        Page<EvTrip> page = tripRepository.findByUserIdAndFilters(userId, null, null, null, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findByUserIdAndFilters_withFromOnly_doesNotThrow() {
        assertThatCode(() ->
            tripRepository.findByUserIdAndFilters(userId, null, OffsetDateTime.now().minusDays(1), null, PageRequest.of(0, 20))
        ).doesNotThrowAnyException();
    }

    @Test
    void findByUserIdAndFilters_withToOnly_doesNotThrow() {
        assertThatCode(() ->
            tripRepository.findByUserIdAndFilters(userId, null, null, OffsetDateTime.now().plusDays(1), PageRequest.of(0, 20))
        ).doesNotThrowAnyException();
    }

    @Test
    void findByUserIdAndFilters_withCarIdOnly_doesNotThrow() {
        assertThatCode(() ->
            tripRepository.findByUserIdAndFilters(userId, UUID.randomUUID(), null, null, PageRequest.of(0, 20))
        ).doesNotThrowAnyException();
    }
}
