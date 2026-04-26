package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PublicModelByEnumControllerTest extends AbstractIntegrationTest {

    @Test
    void getModelStatsByEnum_returnsOkWithModelMetadata() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/by-enum/MODEL_3", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Tesla"), "Response should contain brand display name");
        assertTrue(response.getBody().contains("Model 3"), "Response should contain model display name");
    }

    @Test
    void getModelStatsByEnum_isCaseInsensitive() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/by-enum/model_3", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getModelStatsByEnum_returns404ForUnknownEnum() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/by-enum/UNKNOWN_FLYING_CAR", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getModelStatsByEnum_includesLogCountWhenDataExists() {
        User user = createAndSaveUser("byenum-" + System.nanoTime() + "@example.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.IONIQ_5);

        for (int i = 0; i < 5; i++) {
            evLogRepository.save(EvLog.createNew(car.getId(), new BigDecimal("18.0"), null,
                    45, "u33db", 40000 + (i * 200), null, new java.math.BigDecimal("80"),
                    LocalDateTime.now().minusDays(5 - i), ChargingType.UNKNOWN, null, null, false, null));
        }

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/by-enum/IONIQ_5", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Ioniq 5"), "Response should contain model display name");
        assertTrue(response.getBody().contains("Hyundai"), "Response should contain brand display name");
    }
}
