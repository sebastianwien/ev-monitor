package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SitemapControllerTest extends AbstractIntegrationTest {

    @Test
    void sitemap_includesModelWithExactlyThresholdLogs() {
        User user = createAndSaveUser("sitemap-above-" + System.nanoTime() + "@example.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);

        // Incrementing odometer so each log counts as a distinct trip in the query
        for (int i = 0; i < 25; i++) {
            evLogRepository.save(EvLog.createNew(car.getId(), new BigDecimal("20.0"), null,
                    45, "u33db", 50000 + (i * 200), null, 80,
                    LocalDateTime.now().minusDays(25 - i), ChargingType.UNKNOWN, null, null, false, null));
        }

        ResponseEntity<String> response = restTemplate.getForEntity("/sitemap.xml", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("/modelle/Tesla/Model_3"),
                "Tesla Model 3 with exactly 25 logs should be in sitemap");
    }

    @Test
    void sitemap_excludesModelWithFewerThanThresholdLogs() {
        User user = createAndSaveUser("sitemap-below-" + System.nanoTime() + "@example.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.CUPRA_BORN);

        for (int i = 0; i < 24; i++) {
            evLogRepository.save(EvLog.createNew(car.getId(), new BigDecimal("15.0"), null,
                    45, "u33db", 30000 + (i * 200), null, 80,
                    LocalDateTime.now().minusDays(24 - i), ChargingType.UNKNOWN, null, null, false, null));
        }

        ResponseEntity<String> response = restTemplate.getForEntity("/sitemap.xml", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().contains("/Cupra/Born"),
                "Cupra Born with only 24 logs should not be in sitemap");
    }

    @Test
    void sitemap_excludesBrandWithNoQualifyingModels() {
        // Toyota/BZ4X with only 5 logs (below threshold) - Toyota brand should not appear
        User user = createAndSaveUser("sitemap-brand-" + System.nanoTime() + "@example.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.BZ4X);

        for (int i = 0; i < 5; i++) {
            evLogRepository.save(EvLog.createNew(car.getId(), new BigDecimal("18.0"), null,
                    45, "u33db", 40000 + (i * 200), null, 80,
                    LocalDateTime.now().minusDays(5 - i), ChargingType.UNKNOWN, null, null, false, null));
        }

        ResponseEntity<String> response = restTemplate.getForEntity("/sitemap.xml", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().contains("/modelle/Toyota"),
                "Toyota brand with no qualifying models should not appear in sitemap");
    }

    @Test
    void sitemap_returnsValidXml() {
        ResponseEntity<String> response = restTemplate.getForEntity("/sitemap.xml", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().startsWith("<?xml"), "Response should be valid XML");
        assertTrue(response.getBody().contains("<urlset"), "Response should contain urlset element");
        assertTrue(response.getBody().contains("/modelle"), "Static /modelle URL should always be in sitemap");
    }
}
