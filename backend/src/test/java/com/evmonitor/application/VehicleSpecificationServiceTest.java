package com.evmonitor.application;

import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.domain.VehicleSpecificationRepository;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Lookup-Tests des Spec-Service. Das Nutzer-Anlegen (create, Coins) entfiel mit A2; Zeilen entstehen
 * nur im Admin-Tab, siehe AdminVehicleSpecificationServiceTest.
 *
 * SECURITY CRITICAL: XSS prevention via input sanitization!
 */
@ExtendWith(MockitoExtension.class)
class VehicleSpecificationServiceTest {

    @Mock
    private VehicleSpecificationRepository vehicleSpecificationRepository;

    private VehicleSpecificationService vehicleSpecificationService;

    @BeforeEach
    void setUp() {
        vehicleSpecificationService = new VehicleSpecificationService(vehicleSpecificationRepository);
    }

    @Test
    void shouldLookupWltpData_Success() {
        // Given: WLTP data exists
        String brand = "TESLA";
        String model = "MODEL_3";
        BigDecimal capacity = new BigDecimal("75.0");

        VehicleSpecification existingSpec = TestDataBuilder.createTestVehicleSpecification(brand, model, capacity);
        when(vehicleSpecificationRepository.findByCarBrandAndModelAndCapacityAndType(
                brand, model, capacity, VehicleSpecification.WltpType.COMBINED
        )).thenReturn(Optional.of(existingSpec));

        // When
        Optional<VehicleSpecificationResponse> result = vehicleSpecificationService.lookup(brand, model, capacity);

        // Then
        assertTrue(result.isPresent());
        assertEquals(brand, result.get().carBrand());
        assertEquals(model, result.get().carModel());
    }

    @Test
    void shouldLookupWltpData_NotFound() {
        // Given: No WLTP data exists
        String brand = "UNKNOWN";
        String model = "UNKNOWN_MODEL";
        BigDecimal capacity = new BigDecimal("100.0");

        when(vehicleSpecificationRepository.findByCarBrandAndModelAndCapacityAndType(
                any(), any(), any(), any()
        )).thenReturn(Optional.empty());

        // When
        Optional<VehicleSpecificationResponse> result = vehicleSpecificationService.lookup(brand, model, capacity);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void shouldSanitizeLookupInput() {
        // Given: Malicious lookup input
        String maliciousBrand = "TESLA<script>alert('XSS')</script>";
        String maliciousModel = "MODEL_3<img src=x>";
        BigDecimal capacity = new BigDecimal("75.0");

        when(vehicleSpecificationRepository.findByCarBrandAndModelAndCapacityAndType(
                any(), any(), any(), any()
        )).thenReturn(Optional.empty());

        // When
        vehicleSpecificationService.lookup(maliciousBrand, maliciousModel, capacity);

        // Then: Verify sanitized input was used for lookup
        ArgumentCaptor<String> brandCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> modelCaptor = ArgumentCaptor.forClass(String.class);

        verify(vehicleSpecificationRepository).findByCarBrandAndModelAndCapacityAndType(
                brandCaptor.capture(),
                modelCaptor.capture(),
                eq(capacity),
                any()
        );

        String sanitizedBrand = brandCaptor.getValue();
        String sanitizedModel = modelCaptor.getValue();

        assertFalse(sanitizedBrand.contains("<script>"));
        assertFalse(sanitizedModel.contains("<img"));
    }
}
