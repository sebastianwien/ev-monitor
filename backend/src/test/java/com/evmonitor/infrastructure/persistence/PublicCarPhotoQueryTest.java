package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarStatus;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the public-photo read queries against a real PostgreSQL instance. The key
 * guarantee is a privacy one: only cars with {@code image_public = true} AND a stored image
 * are ever returned, and results are newest-first (so the first per model is the hero).
 * Auto-skips when Docker is unavailable.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PublicCarPhotoQueryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JpaCarRepository repository;

    private UUID persistCar(CarBrand.CarModel model, boolean imagePublic, boolean hasImage,
                            LocalDateTime updatedAt) {
        CarEntity c = new CarEntity();
        UUID id = UUID.randomUUID();
        c.setId(id);
        c.setUserId(UUID.randomUUID());
        c.setModel(model);
        c.setYear(2023);
        c.setStatus(CarStatus.ACTIVE);
        c.setCreatedAt(updatedAt);
        c.setUpdatedAt(updatedAt);
        c.setImagePublic(imagePublic);
        c.setImagePath(hasImage ? "/img/" + id + ".jpg" : null);
        c.setPrimary(true);
        c.setBusinessCar(false);
        c.setHeatPump(false);
        c.setRegistrationDate(LocalDate.of(2023, 1, 1));
        return repository.save(c).getId();
    }

    @Test
    void refsContainOnlyPublicPhotos_newestFirst() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 12, 0);
        UUID newest = persistCar(CarBrand.CarModel.MODEL_3, true, true, base.plusDays(2));
        UUID older = persistCar(CarBrand.CarModel.MODEL_3, true, true, base.plusDays(1));
        persistCar(CarBrand.CarModel.MODEL_3, false, true, base.plusDays(3));  // private -> excluded
        persistCar(CarBrand.CarModel.MODEL_3, true, false, base.plusDays(3));  // public but no image -> excluded
        repository.flush();

        List<UUID> ids = repository.findPublicPhotoRefsNewestFirst().stream()
                .map(JpaCarRepository.PublicPhotoProjection::getId).toList();

        assertThat(ids).containsExactly(newest, older);
    }

    @Test
    void perModelQueryFiltersByModelAndPublicFlag() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 12, 0);
        UUID m3Newest = persistCar(CarBrand.CarModel.MODEL_3, true, true, base.plusDays(2));
        UUID m3Older = persistCar(CarBrand.CarModel.MODEL_3, true, true, base.plusDays(1));
        persistCar(CarBrand.CarModel.MODEL_3, false, true, base.plusDays(5)); // private -> excluded
        persistCar(CarBrand.CarModel.MODEL_Y, true, true, base.plusDays(9));  // other model -> excluded
        repository.flush();

        List<UUID> m3 = repository.findPublicPhotoCarIdsByModel(CarBrand.CarModel.MODEL_3.name());

        assertThat(m3).containsExactly(m3Newest, m3Older);
    }
}
