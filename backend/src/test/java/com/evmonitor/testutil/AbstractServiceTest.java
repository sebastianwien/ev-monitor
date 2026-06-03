package com.evmonitor.testutil;

import com.evmonitor.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for service-layer integration tests that don't need HTTP.
 * Uses WebEnvironment.NONE so @Transactional rollback works correctly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestAsyncConfig.class)
public abstract class AbstractServiceTest {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected CarRepository carRepository;

    @Autowired
    protected EvLogRepository evLogRepository;

    @Autowired
    protected VehicleSpecificationRepository vehicleSpecificationRepository;

    protected User createUser(String email, String country) {
        User user = TestDataBuilder.createTestUser(email).toBuilder().country(country).build();
        return userRepository.save(user);
    }

    protected User createAndSaveUser(String email) {
        return userRepository.save(TestDataBuilder.createTestUser(email));
    }
}
