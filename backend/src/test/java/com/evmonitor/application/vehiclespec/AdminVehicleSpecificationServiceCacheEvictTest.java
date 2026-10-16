package com.evmonitor.application.vehiclespec;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.testutil.AbstractServiceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Rangliste und Modellseite rechnen aus den Zeilen; jeder Admin-Write muss die Modell-Caches leeren. */
@Transactional
class AdminVehicleSpecificationServiceCacheEvictTest extends AbstractServiceTest {

    @Autowired private AdminVehicleSpecificationService service;
    @Autowired private CacheManager cacheManager;

    @Test
    void create_evictsAllModelCaches() {
        for (String name : AdminVehicleSpecificationService.MODEL_CACHES) {
            Cache cache = cacheManager.getCache(name);
            assertThat(cache).as("Cache %s muss konfiguriert sein", name).isNotNull();
            cache.put("k", "v");
        }
        UUID adminId = createAndSaveUser("admin-" + UUID.randomUUID() + "@test.de").getId();

        service.create(adminId, new SpecCommand(CarBrand.CarModel.ID_3, new BigDecimal("62.0"), new BigDecimal("58.0"),
                new BigDecimal("426"), new BigDecimal("15.4"), "Pro", "Pro", LocalDate.of(2021, 1, 1), null,
                "https://ev-database.org/car/1531", VehicleSpecification.RatingSource.WLTP, null, null));

        for (String name : AdminVehicleSpecificationService.MODEL_CACHES) {
            assertThat(cacheManager.getCache(name).get("k")).as("Cache %s nach Write", name).isNull();
        }
    }
}
