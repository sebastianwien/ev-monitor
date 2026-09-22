package com.evmonitor.application.manualimport;

import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.application.publicapi.PublicApiTripRequest;
import com.evmonitor.application.publicapi.PublicApiTripService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manual CSV/JSON upload of driving trips. Parses the raw text into trip requests
 * and hands them to {@link PublicApiTripService#createTrips} which owns validation,
 * ownership and duplicate rules.
 *
 * Columns (snake_case, same as the public API):
 *   started_at, ended_at, distance_km, odometer_start_km, odometer_end_km,
 *   soc_start, soc_end, route_type
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ManualTripImportService {

    private final PublicApiTripService tripService;
    private final ImportRowParser rowParser;

    public ImportApiResult importData(UUID userId, UUID carId, String format, String data) {
        List<Map<String, String>> rows;
        int columnMismatches;
        try {
            ImportRowParser.ParsedRows parsed = rowParser.parse(format, data);
            rows = parsed.rows();
            columnMismatches = parsed.columnMismatches();
        } catch (Exception e) {
            log.warn("ManualTripImport: Datei konnte nicht geparst werden: {}", e.getMessage());
            return ImportApiResult.withoutIds(0, 0, 1);
        }

        List<PublicApiTripRequest> entries = new ArrayList<>();
        int parseErrors = 0;
        for (Map<String, String> row : rows) {
            String startedAt = rowParser.get(row, "started_at");
            String endedAt = rowParser.get(row, "ended_at");
            if (startedAt == null || endedAt == null) {
                parseErrors++;
                continue;
            }
            entries.add(new PublicApiTripRequest(
                    carId,
                    startedAt,
                    endedAt,
                    rowParser.parseBigDecimal(rowParser.get(row, "distance_km")),
                    rowParser.parseBigDecimal(rowParser.get(row, "odometer_start_km")),
                    rowParser.parseBigDecimal(rowParser.get(row, "odometer_end_km")),
                    rowParser.parseBigDecimal(rowParser.get(row, "soc_start")),
                    rowParser.parseBigDecimal(rowParser.get(row, "soc_end")),
                    rowParser.get(row, "route_type")
            ));
        }

        // Ownership is enforced inside createTrips even for an all-invalid file.
        ImportApiResult result = tripService.createTrips(userId, carId, entries);
        return ImportApiResult.withoutIds(
                result.imported(), result.skipped(), result.errors() + parseErrors, columnMismatches);
    }
}
