package com.evmonitor.application.manualimport;

import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.application.publicapi.PublicApiImportService;
import com.evmonitor.application.publicapi.PublicApiSessionRequest;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ManualImportService {

    private final PublicApiImportService publicApiImportService;
    private final CarRepository carRepository;
    private final ImportRowParser rowParser;

    public ImportApiResult importData(UUID userId, UUID carId, String format, String data) {
        return importData(userId, carId, format, data, DataSource.API_UPLOAD);
    }

    public ImportApiResult importData(UUID userId, UUID carId, String format, String data, DataSource dataSource) {
        // Ownership check before parsing
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
        if (!car.isOwnedBy(userId)) {
            throw new SecurityException("Dieses Fahrzeug gehört dir nicht");
        }

        List<Map<String, String>> rows;
        int columnMismatches = 0;
        try {
            ImportRowParser.ParsedRows parsed = rowParser.parse(format, data);
            rows = parsed.rows();
            columnMismatches = parsed.columnMismatches();
        } catch (Exception e) {
            log.warn("ManualImport: Datei konnte nicht geparst werden: {}", e.getMessage());
            return ImportApiResult.withoutIds(0, 0, 1);
        }

        if (rows.isEmpty()) {
            return ImportApiResult.withoutIds(0, 0, 0);
        }

        List<PublicApiSessionRequest.SessionEntry> entries = new ArrayList<>();
        int parseErrors = 0;
        for (Map<String, String> row : rows) {
            PublicApiSessionRequest.SessionEntry entry = mapRowToEntry(row);
            if (entry != null) {
                entries.add(entry);
            } else {
                parseErrors++;
            }
        }

        if (entries.isEmpty()) {
            return ImportApiResult.withoutIds(0, 0, parseErrors, columnMismatches);
        }

        ImportApiResult result = publicApiImportService.importSessions(userId, new PublicApiSessionRequest(carId, entries), dataSource);
        int totalErrors = result.errors() + parseErrors;
        return ImportApiResult.withoutIds(result.imported(), result.skipped(), totalErrors, columnMismatches);
    }

    private PublicApiSessionRequest.SessionEntry mapRowToEntry(Map<String, String> row) {
        String date = rowParser.get(row, "date");
        if (date == null) return null;

        Double kwh = rowParser.parseDouble(rowParser.get(row, "kwh"));
        Double kwhAtVehicle = rowParser.parseDouble(rowParser.get(row, "kwh_at_vehicle"));
        // At least one of the two energy fields must be present and > 0 - otherwise
        // the row is rejected as an error (same rule as @AssertTrue on SessionEntry).
        boolean hasKwh = kwh != null && kwh > 0;
        boolean hasKwhAtVehicle = kwhAtVehicle != null && kwhAtVehicle > 0;
        if (!hasKwh && !hasKwhAtVehicle) return null;

        Integer odometerKm = rowParser.parseInteger(rowParser.get(row, "odometer_km"));
        BigDecimal socBefore = rowParser.parseBigDecimal(rowParser.get(row, "soc_before"));
        BigDecimal socAfter = rowParser.parseBigDecimal(rowParser.get(row, "soc_after"));
        Double costEur = rowParser.parseDouble(rowParser.get(row, "cost_eur"));
        Integer durationMin = rowParser.parseInteger(rowParser.get(row, "duration_min"));
        String location = rowParser.get(row, "location");
        String chargingType = rowParser.get(row, "charging_type");
        Double maxChargingPowerKw = rowParser.parseDouble(rowParser.get(row, "max_charging_power_kw"));
        String routeType = rowParser.get(row, "route_type");
        String tireType = rowParser.get(row, "tire_type");
        String rawImportData = rowParser.get(row, "raw_import_data");
        Boolean isPublicCharging = rowParser.parseBoolean(rowParser.get(row, "is_public_charging"));
        String cpoName = rowParser.get(row, "cpo_name");
        String measurementType = rowParser.get(row, "measurement_type");

        return new PublicApiSessionRequest.SessionEntry(
                date, kwh, kwhAtVehicle, odometerKm, socBefore, socAfter,
                costEur, durationMin, location, chargingType,
                maxChargingPowerKw, routeType, tireType, rawImportData,
                isPublicCharging, cpoName, measurementType,
                null  // temperatureCelsius not available via manual import
        );
    }
}
