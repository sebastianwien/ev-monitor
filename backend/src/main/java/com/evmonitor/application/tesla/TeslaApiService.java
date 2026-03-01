package com.evmonitor.application.tesla;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeslaApiService {

    private static final String TESLA_API_BASE = "https://owner-api.teslamotors.com/api/1";
    private static final String ALGORITHM = "AES";

    private final TeslaConnectionRepository teslaConnectionRepository;
    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${tesla.encryption.key:change-this-32-char-secret-key!!}")
    private String encryptionKey;

    /**
     * Saves Tesla connection for a user
     */
    public TeslaConnection saveConnection(UUID userId, String accessToken, String vehicleId, String vehicleName) {
        // Delete existing connection if any
        teslaConnectionRepository.findByUserId(userId).ifPresent(teslaConnectionRepository::delete);

        // Encrypt token
        String encryptedToken = encryptToken(accessToken);

        TeslaConnection connection = TeslaConnection.builder()
            .userId(userId)
            .accessToken(encryptedToken)
            .vehicleId(vehicleId)
            .vehicleName(vehicleName)
            .autoImportEnabled(false)
            .build();

        return teslaConnectionRepository.save(connection);
    }

    /**
     * Fetches vehicle data from Tesla API and creates EvLog entries
     */
    public TeslaSyncResult syncChargingData(UUID userId) {
        TeslaConnection connection = teslaConnectionRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("No Tesla connection found for user"));

        // Decrypt token
        String accessToken = decryptToken(connection.getAccessToken());

        // Fetch vehicle data
        TeslaVehicleDataResponse vehicleData = fetchVehicleData(connection.getVehicleId(), accessToken);

        // Find or create car for this user
        Car car = findOrCreateCar(userId, vehicleData);

        // Check if there's charging data to import
        TeslaVehicleDataResponse.ChargeState chargeState = vehicleData.chargeState();
        TeslaVehicleDataResponse.DriveState driveState = vehicleData.driveState();

        int importedLogs = 0;

        // Only create log if there's meaningful charging data
        if (chargeState.chargeEnergyAdded() != null && chargeState.chargeEnergyAdded() > 0.1) {
            // Check if we already have a log with similar timestamp (prevent duplicates)
            LocalDateTime loggedAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(chargeState.timestamp()),
                ZoneOffset.UTC
            );

            boolean alreadyExists = evLogRepository.existsByCarIdAndLoggedAtBetween(
                car.getId(),
                loggedAt.minusHours(1),
                loggedAt.plusHours(1)
            );

            if (!alreadyExists) {
                EvLog evLog = createEvLogFromTeslaData(car.getId(), vehicleData);
                evLogRepository.save(evLog);
                importedLogs++;
                log.info("Imported charging log for user {} from Tesla: {} kWh", userId, chargeState.chargeEnergyAdded());
            } else {
                log.debug("Skipping duplicate log for user {} at {}", userId, loggedAt);
            }
        }

        // Update connection
        connection.setLastSyncAt(LocalDateTime.now());
        teslaConnectionRepository.save(connection);

        return new TeslaSyncResult(importedLogs, vehicleData.displayName(), vehicleData.chargeState().batteryLevel());
    }

    /**
     * Gets Tesla connection status for a user
     */
    public Optional<TeslaConnectionStatus> getConnectionStatus(UUID userId) {
        return teslaConnectionRepository.findByUserId(userId)
            .map(conn -> new TeslaConnectionStatus(
                true,
                conn.getVehicleName(),
                conn.getLastSyncAt(),
                conn.isAutoImportEnabled()
            ));
    }

    /**
     * Disconnects Tesla account
     */
    public void disconnect(UUID userId) {
        teslaConnectionRepository.deleteByUserId(userId);
        log.info("Disconnected Tesla for user {}", userId);
    }

    // ===== PRIVATE HELPERS =====

    private TeslaVehicleDataResponse fetchVehicleData(String vehicleId, String accessToken) {
        String url = TESLA_API_BASE + "/vehicles/" + vehicleId + "/vehicle_data";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<TeslaApiWrapper> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                TeslaApiWrapper.class
            );

            if (response.getBody() == null || response.getBody().response() == null) {
                throw new IllegalStateException("Empty response from Tesla API");
            }

            return response.getBody().response();
        } catch (Exception e) {
            log.error("Failed to fetch Tesla vehicle data: {}", e.getMessage());
            throw new IllegalStateException("Failed to fetch data from Tesla API: " + e.getMessage());
        }
    }

    private Car findOrCreateCar(UUID userId, TeslaVehicleDataResponse vehicleData) {
        // Try to find existing Tesla car by name
        List<Car> userCars = carRepository.findAllByUserId(userId);

        return userCars.stream()
            .filter(car -> {
                String modelName = car.getModel().name();
                return modelName.contains("MODEL_3") || modelName.contains("MODEL_S") ||
                       modelName.contains("MODEL_X") || modelName.contains("MODEL_Y") ||
                       (vehicleData.displayName() != null &&
                        car.getLicensePlate() != null &&
                        car.getLicensePlate().contains(vehicleData.displayName()));
            })
            .findFirst()
            .orElseGet(() -> {
                // Create new car
                CarBrand.CarModel model = mapTeslaModelToCarModel(vehicleData.vehicleConfig().carType());
                Integer year = extractYearFromVin(vehicleData.vin());
                String licensePlate = vehicleData.displayName();
                BigDecimal batteryCapacity = extractBatteryCapacity(vehicleData.vehicleConfig().trimBadging());

                Car newCar = Car.createNew(
                    userId,
                    model,
                    year,
                    licensePlate,
                    null, // trim
                    batteryCapacity,
                    null  // powerKw
                );
                return carRepository.save(newCar);
            });
    }

    private EvLog createEvLogFromTeslaData(UUID carId, TeslaVehicleDataResponse vehicleData) {
        TeslaVehicleDataResponse.ChargeState chargeState = vehicleData.chargeState();
        TeslaVehicleDataResponse.DriveState driveState = vehicleData.driveState();

        // Use native coordinates if available, fallback to regular
        Double lat = driveState.nativeLatitude() != null ? driveState.nativeLatitude() : driveState.latitude();
        Double lon = driveState.nativeLongitude() != null ? driveState.nativeLongitude() : driveState.longitude();

        // Generate geohash (5 chars = ~5km precision)
        String geohash = GeoHash.withCharacterPrecision(lat, lon, 5).toBase32();

        // Convert timestamp to LocalDateTime
        LocalDateTime loggedAt = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(chargeState.timestamp()),
            ZoneOffset.UTC
        );

        return EvLog.createNewWithSource(
            carId,
            BigDecimal.valueOf(chargeState.chargeEnergyAdded()),
            null, // costEur - User needs to fill this manually
            null, // chargeDurationMinutes - Not available in current data
            geohash,
            null, // odometerKm - Could add vehicleState.odometer if needed
            chargeState.chargerPower() != null ? BigDecimal.valueOf(chargeState.chargerPower()) : null, // maxChargingPowerKw
            loggedAt,
            "TESLA_IMPORT"
        );
    }

    private CarBrand.CarModel mapTeslaModelToCarModel(String carType) {
        // Map Tesla API car_type to our CarBrand.CarModel enum
        return switch (carType.toLowerCase()) {
            case "model3" -> CarBrand.CarModel.MODEL_3;
            case "models" -> CarBrand.CarModel.MODEL_S;
            case "modelx" -> CarBrand.CarModel.MODEL_X;
            case "modely" -> CarBrand.CarModel.MODEL_Y;
            default -> CarBrand.CarModel.MODEL_3; // Default fallback
        };
    }

    private Integer extractYearFromVin(String vin) {
        // Tesla VIN position 10 = year code (simplified)
        // For MVP, just return current year
        return LocalDateTime.now().getYear() - 2; // Rough estimate
    }

    private BigDecimal extractBatteryCapacity(String trimBadging) {
        // Tesla trim badging sometimes indicates capacity (e.g., "74" = 74 kWh)
        try {
            if (trimBadging != null && trimBadging.matches("\\d+")) {
                return new BigDecimal(trimBadging);
            }
        } catch (NumberFormatException ignored) {
        }
        return BigDecimal.valueOf(75); // Default for Model 3 LR
    }

    private String encryptToken(String token) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                encryptionKey.substring(0, 16).getBytes(StandardCharsets.UTF_8),
                ALGORITHM
            );
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt token", e);
        }
    }

    private String decryptToken(String encryptedToken) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                encryptionKey.substring(0, 16).getBytes(StandardCharsets.UTF_8),
                ALGORITHM
            );
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedToken));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt token", e);
        }
    }

    // Wrapper for Tesla API response structure
    private record TeslaApiWrapper(TeslaVehicleDataResponse response) {}
}
