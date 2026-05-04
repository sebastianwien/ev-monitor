package com.evmonitor.application.tessie;

import com.evmonitor.domain.TessieRawImport;
import com.evmonitor.infrastructure.external.TessieClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TessieImportService {

    private final TessieClient client;
    private final TessieRawImportJdbcWriter writer;
    private final TessieProcessorService processor;

    public List<TessieVehicleDTO> fetchVehicles(String token) {
        return client.getVehicles(token);
    }

    /**
     * Fetches one VIN's drives and charges from Tessie, persists the raw payloads
     * (deduplicated via tessie_raw_imports unique key), and immediately processes
     * the unprocessed rows into ev_log + ev_trip for the supplied {@code carId}.
     *
     * The Tessie HTTP fetch runs OUTSIDE any database transaction so the connection
     * pool stays free during the (possibly slow) external call. The raw write and
     * the processor each manage their own transactions internally.
     */
    public TessieImportResult importForVin(UUID userId, String token, String vin, UUID carId) {
        List<JsonNode> driveNodes = client.getDrives(token, vin);
        List<JsonNode> chargeNodes = client.getCharges(token, vin);

        List<TessieRawImport> drives = toEntities(userId, vin, "drive", driveNodes);
        List<TessieRawImport> charges = toEntities(userId, vin, "charge", chargeNodes);

        int drivesImported = sum(writer.batchInsertIfNew(drives));
        int chargesImported = sum(writer.batchInsertIfNew(charges));
        int skipped = (drives.size() + charges.size()) - drivesImported - chargesImported;

        TessieProcessorService.TessieProcessorResult procResult = processor.processForCar(userId, vin, carId);

        log.info("Tessie import user={} vin={} car={}: drives={} charges={} skipped={} ev_logs={} ev_trips={}",
                userId, vin, carId, drivesImported, chargesImported, skipped,
                procResult.evLogsCreated(), procResult.evTripsCreated());
        return new TessieImportResult(
                drivesImported, chargesImported, skipped,
                procResult.evLogsCreated(), procResult.evTripsCreated());
    }

    private List<TessieRawImport> toEntities(UUID userId, String vin, String type, List<JsonNode> nodes) {
        List<TessieRawImport> result = new ArrayList<>(nodes.size());
        for (JsonNode node : nodes) {
            long tessieId = node.path("id").asLong(-1);
            if (tessieId < 0) {
                log.warn("Tessie {} entry missing id field, skipping", type);
                continue;
            }
            long startedAt = node.path("started_at").asLong(0);
            OffsetDateTime recordedAt = startedAt > 0
                    ? OffsetDateTime.ofInstant(Instant.ofEpochSecond(startedAt), ZoneOffset.UTC)
                    : OffsetDateTime.now(ZoneOffset.UTC);

            result.add(TessieRawImport.builder()
                    .userId(userId)
                    .vin(vin)
                    .type(type)
                    .tessieId(tessieId)
                    .recordedAt(recordedAt)
                    .raw(node.toString())
                    .processed(false)
                    .build());
        }
        return result;
    }

    private int sum(int[] counts) {
        int total = 0;
        for (int c : counts) total += c;
        return total;
    }
}
