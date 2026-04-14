package com.evmonitor.application;

import com.evmonitor.domain.CoinType;
import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.domain.VehicleSpecificationRepository;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for VehicleSpecificationService.
 * Tests WLTP data crowdsourcing, coin rewards, and input sanitization.
 *
 * SECURITY CRITICAL: XSS prevention via input sanitization!
 * BUSINESS CRITICAL: Coin rewards must be atomic with WLTP save!
 */
@ExtendWith(MockitoExtension.class)
class VehicleSpecificationServiceTest {

    @Mock
    private VehicleSpecificationRepository vehicleSpecificationRepository;

    @Mock
    private CoinLogService coinLogService;

    private VehicleSpecificationService vehicleSpecificationService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        vehicleSpecificationService = new VehicleSpecificationService(
                vehicleSpecificationRepository,
                coinLogService
        );
        userId = UUID.randomUUID();
    }

    @Test
    void shouldCreateWltpData_AndAwardCoins() {
        // Given
        String brand = "TESLA";
        String model = "MODEL_3";
        BigDecimal capacity = new BigDecimal("75.0");

        VehicleSpecificationRequest request = new VehicleSpecificationRequest(
                brand, model, capacity,
                new BigDecimal("450.0"),
                new BigDecimal("16.5"),
                null  // ratingSource null → defaults to WLTP
        );

        when(vehicleSpecificationRepository.existsByCarBrandAndModelAndCapacityAndTypeAndSource(
                brand, model, capacity, VehicleSpecification.WltpType.COMBINED, VehicleSpecification.RatingSource.WLTP
        )).thenReturn(false);
        when(vehicleSpecificationRepository.save(any(VehicleSpecification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        VehicleSpecificationCreateResponse response = vehicleSpecificationService.create(userId, request);

        // Then
        assertNotNull(response);
        assertEquals(50, response.coinsAwarded());

        // Verify WLTP data was saved
        ArgumentCaptor<VehicleSpecification> specCaptor = ArgumentCaptor.forClass(VehicleSpecification.class);
        verify(vehicleSpecificationRepository).save(specCaptor.capture());
        VehicleSpecification savedSpec = specCaptor.getValue();
        assertEquals(brand, savedSpec.getCarBrand());
        assertEquals(model, savedSpec.getCarModel());
        assertEquals(capacity, savedSpec.getBatteryCapacityKwh());

        // Verify coins were awarded
        verify(coinLogService).awardCoins(
                eq(userId),
                eq(CoinType.SOCIAL_COIN),
                eq(50),
                contains("WLTP data contribution")
        );
    }

    @Test
    void shouldRejectDuplicateWltpData() {
        // Given: WLTP data already exists
        String brand = "TESLA";
        String model = "MODEL_3";
        BigDecimal capacity = new BigDecimal("75.0");

        VehicleSpecificationRequest request = new VehicleSpecificationRequest(
                brand, model, capacity,
                new BigDecimal("450.0"),
                new BigDecimal("16.5"),
                null  // defaults to WLTP
        );

        when(vehicleSpecificationRepository.existsByCarBrandAndModelAndCapacityAndTypeAndSource(
                brand, model, capacity, VehicleSpecification.WltpType.COMBINED, VehicleSpecification.RatingSource.WLTP
        )).thenReturn(true); // Already exists!

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            vehicleSpecificationService.create(userId, request);
        });

        assertTrue(exception.getMessage().contains("already exists"));

        // Verify no save or coin award happened
        verify(vehicleSpecificationRepository, never()).save(any(VehicleSpecification.class));
        verify(coinLogService, never()).awardCoins(any(), any(), anyInt(), any());
    }

    @Test
    void shouldHandleRaceCondition_DuplicateInsert() {
        // Given: Two concurrent requests try to create same WLTP data
        // First check says "doesn't exist", but save fails due to unique constraint
        VehicleSpecificationRequest request = new VehicleSpecificationRequest(
                "TESLA", "MODEL_3", new BigDecimal("75.0"),
                new BigDecimal("450.0"), new BigDecimal("16.5"),
                null  // defaults to WLTP
        );

        when(vehicleSpecificationRepository.existsByCarBrandAndModelAndCapacityAndTypeAndSource(
                any(), any(), any(), any(), any()
        )).thenReturn(false); // Check says "doesn't exist"

        when(vehicleSpecificationRepository.save(any(VehicleSpecification.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key")); // But save fails!

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            vehicleSpecificationService.create(userId, request);
        });

        assertTrue(exception.getMessage().contains("concurrent insert"));

        // Verify no coins were awarded (transaction rolled back)
        verify(coinLogService, never()).awardCoins(any(), any(), anyInt(), any());
    }

    @Test
    void shouldSanitizeInput_XssProtection() {
        // Given: Malicious input with HTML tags
        String maliciousBrand = "TESLA<script>alert('XSS')</script>";
        String maliciousModel = "MODEL_3<img src=x onerror=alert(1)>";
        BigDecimal capacity = new BigDecimal("75.0");

        VehicleSpecificationRequest request = new VehicleSpecificationRequest(
                maliciousBrand, maliciousModel, capacity,
                new BigDecimal("450.0"), new BigDecimal("16.5"),
                null
        );

        when(vehicleSpecificationRepository.existsByCarBrandAndModelAndCapacityAndTypeAndSource(
                any(), any(), any(), any(), any()
        )).thenReturn(false);
        when(vehicleSpecificationRepository.save(any(VehicleSpecification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        vehicleSpecificationService.create(userId, request);

        // Then
        ArgumentCaptor<VehicleSpecification> specCaptor = ArgumentCaptor.forClass(VehicleSpecification.class);
        verify(vehicleSpecificationRepository).save(specCaptor.capture());
        VehicleSpecification savedSpec = specCaptor.getValue();

        // HTML tags should be removed (but content between tags remains)
        assertFalse(savedSpec.getCarBrand().contains("<script>"));
        assertFalse(savedSpec.getCarBrand().contains("</script>"));
        assertFalse(savedSpec.getCarModel().contains("<img"));
        // Note: Current sanitizer removes tags but not content between them
        assertEquals("TESLAalert('XSS')", savedSpec.getCarBrand());
        assertEquals("MODEL_3", savedSpec.getCarModel());

        // Verify sanitized input in coin description
        ArgumentCaptor<String> descriptionCaptor = ArgumentCaptor.forClass(String.class);
        verify(coinLogService).awardCoins(any(), any(), anyInt(), descriptionCaptor.capture());
        String description = descriptionCaptor.getValue();
        assertFalse(description.contains("<script>"));
        assertFalse(description.contains("<img"));
    }

    @Test
    void shouldTrimWhitespace_InInputs() {
        // Given: Input with extra whitespace
        String brand = "  TESLA  ";
        String model = "  MODEL_3  ";

        VehicleSpecificationRequest request = new VehicleSpecificationRequest(
                brand, model, new BigDecimal("75.0"),
                new BigDecimal("450.0"), new BigDecimal("16.5"),
                null
        );

        when(vehicleSpecificationRepository.existsByCarBrandAndModelAndCapacityAndTypeAndSource(
                any(), any(), any(), any(), any()
        )).thenReturn(false);
        when(vehicleSpecificationRepository.save(any(VehicleSpecification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        vehicleSpecificationService.create(userId, request);

        // Then
        ArgumentCaptor<VehicleSpecification> specCaptor = ArgumentCaptor.forClass(VehicleSpecification.class);
        verify(vehicleSpecificationRepository).save(specCaptor.capture());
        VehicleSpecification savedSpec = specCaptor.getValue();

        // Whitespace should be trimmed
        assertEquals("TESLA", savedSpec.getCarBrand());
        assertEquals("MODEL_3", savedSpec.getCarModel());
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

    // --- EPA Rating Source Tests ---

    @Test
    void shouldCreateEpaData_AndAwardCoins() {
        // Given: request with ratingSource = EPA
        String brand = "TESLA";
        String model = "MODEL_3";
        BigDecimal capacity = new BigDecimal("75.0");

        VehicleSpecificationRequest request = new VehicleSpecificationRequest(
                brand, model, capacity,
                new BigDecimal("531.1"),  // EPA range in km
                new BigDecimal("15.54"),  // EPA consumption kWh/100km
                "EPA"
        );

        when(vehicleSpecificationRepository.existsByCarBrandAndModelAndCapacityAndTypeAndSource(
                brand, model, capacity, VehicleSpecification.WltpType.COMBINED, VehicleSpecification.RatingSource.EPA
        )).thenReturn(false);
        when(vehicleSpecificationRepository.save(any(VehicleSpecification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        VehicleSpecificationCreateResponse response = vehicleSpecificationService.create(userId, request);

        // Then
        assertNotNull(response);
        assertEquals(50, response.coinsAwarded());

        ArgumentCaptor<VehicleSpecification> specCaptor = ArgumentCaptor.forClass(VehicleSpecification.class);
        verify(vehicleSpecificationRepository).save(specCaptor.capture());
        VehicleSpecification savedSpec = specCaptor.getValue();
        assertEquals(VehicleSpecification.RatingSource.EPA, savedSpec.getRatingSource());
    }

    @Test
    void shouldNotBlockEpaCreation_WhenWltpAlreadyExists() {
        // Given: WLTP exists, but we're creating EPA
        String brand = "TESLA";
        String model = "MODEL_3";
        BigDecimal capacity = new BigDecimal("75.0");

        VehicleSpecificationRequest request = new VehicleSpecificationRequest(
                brand, model, capacity,
                new BigDecimal("531.1"),
                new BigDecimal("15.54"),
                "EPA"
        );

        // EPA does NOT exist (WLTP does, but we don't check for it)
        when(vehicleSpecificationRepository.existsByCarBrandAndModelAndCapacityAndTypeAndSource(
                brand, model, capacity, VehicleSpecification.WltpType.COMBINED, VehicleSpecification.RatingSource.EPA
        )).thenReturn(false);
        when(vehicleSpecificationRepository.save(any(VehicleSpecification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When - should NOT throw
        assertDoesNotThrow(() -> vehicleSpecificationService.create(userId, request));
    }

    @Test
    void shouldDefaultToWltp_WhenRatingSourceMissing() {
        // Given: request without ratingSource (backward compat - old frontend)
        VehicleSpecificationRequest request = new VehicleSpecificationRequest(
                "TESLA", "MODEL_3", new BigDecimal("75.0"),
                new BigDecimal("450.0"), new BigDecimal("16.5"),
                null  // no ratingSource
        );

        when(vehicleSpecificationRepository.existsByCarBrandAndModelAndCapacityAndTypeAndSource(
                any(), any(), any(), any(), eq(VehicleSpecification.RatingSource.WLTP)
        )).thenReturn(false);
        when(vehicleSpecificationRepository.save(any(VehicleSpecification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        vehicleSpecificationService.create(userId, request);

        // Then: WLTP should be used
        ArgumentCaptor<VehicleSpecification> specCaptor = ArgumentCaptor.forClass(VehicleSpecification.class);
        verify(vehicleSpecificationRepository).save(specCaptor.capture());
        assertEquals(VehicleSpecification.RatingSource.WLTP, specCaptor.getValue().getRatingSource());
    }

    @Test
    void shouldRejectDuplicateEpaData() {
        // Given: EPA entry already exists
        VehicleSpecificationRequest request = new VehicleSpecificationRequest(
                "TESLA", "MODEL_3", new BigDecimal("75.0"),
                new BigDecimal("531.1"), new BigDecimal("15.54"),
                "EPA"
        );

        when(vehicleSpecificationRepository.existsByCarBrandAndModelAndCapacityAndTypeAndSource(
                any(), any(), any(), any(), eq(VehicleSpecification.RatingSource.EPA)
        )).thenReturn(true);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                vehicleSpecificationService.create(userId, request));
        assertTrue(exception.getMessage().contains("already exists"));
        verify(vehicleSpecificationRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidRatingSource() {
        // Given: unknown rating source
        VehicleSpecificationRequest request = new VehicleSpecificationRequest(
                "TESLA", "MODEL_3", new BigDecimal("75.0"),
                new BigDecimal("450.0"), new BigDecimal("16.5"),
                "CLTC"  // not yet supported
        );

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
                vehicleSpecificationService.create(userId, request));
        verify(vehicleSpecificationRepository, never()).save(any());
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
