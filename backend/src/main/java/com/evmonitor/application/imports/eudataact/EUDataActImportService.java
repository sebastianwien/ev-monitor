package com.evmonitor.application.imports.eudataact;

import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.application.publicapi.PublicApiImportService;
import com.evmonitor.application.publicapi.PublicApiSessionRequest;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class EUDataActImportService {

    // MEB-Exports (ID.3) sind entpackt ~24 MB, gepackt nur ~1,7 MB. Der Upload ist via nginx
    // ohnehin auf 5 MB begrenzt; dieser Guard schuetzt gegen ZIP-Bomben, nicht gegen grosse Exports.
    private static final long MAX_UNZIPPED_BYTES = 64 * 1024 * 1024L; // 64 MB
    private static final int MAX_ZIP_ENTRIES = 10;

    private final EUDataActJsonParser parser;
    private final CarRepository carRepository;
    private final PublicApiImportService publicApiImportService;

    public EUDataActPreviewResult preview(UUID userId, UUID carId, InputStream fileStream, String originalFilename)
            throws IOException {
        Car car = requireOwnedCar(userId, carId);
        EUDataActParseResult parsed = parse(fileStream, originalFilename, car);
        return EUDataActPreviewResult.from(parsed);
    }

    public ImportApiResult importData(UUID userId, UUID carId, InputStream fileStream, String originalFilename)
            throws IOException {
        Car car = requireOwnedCar(userId, carId);
        EUDataActParseResult parsed = parse(fileStream, originalFilename, car);

        List<PublicApiSessionRequest.SessionEntry> entries = toSessionEntries(parsed.sessions(), car);
        if (entries.isEmpty()) {
            return ImportApiResult.withoutIds(0, 0, 0);
        }

        return publicApiImportService.importSessions(
                userId,
                new PublicApiSessionRequest(carId, entries),
                DataSource.EU_DATA_ACT_IMPORT
        );
    }

    private Car requireOwnedCar(UUID userId, UUID carId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
        if (!car.getUserId().equals(userId)) {
            throw new SecurityException("Dieses Fahrzeug gehört dir nicht");
        }
        return car;
    }

    private EUDataActParseResult parse(InputStream fileStream, String originalFilename, Car car) throws IOException {
        EUDataActParseResult parsed = parser.parse(toJsonStream(fileStream, originalFilename));
        List<EUDataActSession> sessions = parsed.sessions().stream()
                .map(s -> withKwhFromSocIfMissing(s, car))
                .toList();
        return new EUDataActParseResult(parsed.vin(), sessions);
    }

    /**
     * Die MEB-Variante liefert kein Leistungssignal - dort ergeben sich die kWh aus dem
     * SoC-Zuwachs und der SoH-adjustierten Kapazitaet. Beide Werte sind fahrzeugseitig
     * gemessen, es entsteht also kein Bruch zur Integration des anderen Formats.
     */
    private EUDataActSession withKwhFromSocIfMissing(EUDataActSession s, Car car) {
        BigDecimal capacity = car.getEffectiveBatteryCapacityKwh();
        if (s.calculatedKwh() != null || s.socDeltaPct() == null || capacity == null) return s;

        double kwh = s.socDeltaPct() / 100.0 * capacity.doubleValue();
        return new EUDataActSession(
                s.startedAt(), s.endedAt(), s.durationMin(),
                s.socBefore(), s.socAfter(), s.socDeltaPct(),
                s.chargeType(), s.maxChargingPowerKw(), kwh,
                s.odometerKm(), s.temperatureCelsius());
    }

    private List<PublicApiSessionRequest.SessionEntry> toSessionEntries(List<EUDataActSession> sessions, Car car) {
        BigDecimal batteryKwh = car.getEffectiveBatteryCapacityKwh();
        List<PublicApiSessionRequest.SessionEntry> entries = new ArrayList<>();

        for (EUDataActSession s : sessions) {
            Double kwh = s.calculatedKwh();
            if (kwh == null) {
                log.debug("EU Data Act: session at {} skipped - no kWh calculable", s.startedAt());
                continue;
            }
            // Sanity check: integrated energy must not exceed battery capacity by more than 10%
            if (batteryKwh != null && kwh > batteryKwh.doubleValue() * 1.1) {
                log.warn("EU Data Act: session at {} has implausible kWh={} vs battery={}kWh - capping",
                        s.startedAt(), kwh, batteryKwh);
                kwh = batteryKwh.doubleValue();
            }

            entries.add(new PublicApiSessionRequest.SessionEntry(
                    s.startedAt().toString(),
                    null,
                    kwh,
                    s.odometerKm(),
                    s.socBefore() != null ? BigDecimal.valueOf(s.socBefore()) : null,
                    s.socAfter() != null ? BigDecimal.valueOf(s.socAfter()) : null,
                    null,
                    s.durationMin(),
                    null,
                    s.chargeType(),
                    s.maxChargingPowerKw(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    "AT_VEHICLE",
                    s.temperatureCelsius()
            ));
        }
        return entries;
    }

    // Accepts both plain JSON and ZIP containing a single JSON file
    private InputStream toJsonStream(InputStream in, String filename) throws IOException {
        if (filename != null && filename.toLowerCase().endsWith(".zip")) {
            return extractJsonFromZip(in);
        }
        return in;
    }

    private InputStream extractJsonFromZip(InputStream in) throws IOException {
        ZipInputStream zip = new ZipInputStream(in);
        int entryCount = 0;
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            entryCount++;
            if (entryCount > MAX_ZIP_ENTRIES) {
                throw new IllegalArgumentException("ZIP contains too many entries (max " + MAX_ZIP_ENTRIES + ")");
            }
            // Path traversal guard
            String name = entry.getName();
            if (name.contains("..") || name.contains("/")) continue;
            if (!name.toLowerCase().endsWith(".json")) continue;

            // Read with bomb protection
            byte[] buf = new byte[8192];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            long total = 0;
            int read;
            while ((read = zip.read(buf)) != -1) {
                total += read;
                if (total > MAX_UNZIPPED_BYTES) {
                    throw new IllegalArgumentException("ZIP entry exceeds size limit of 20 MB");
                }
                out.write(buf, 0, read);
            }
            return new java.io.ByteArrayInputStream(out.toByteArray());
        }
        throw new IllegalArgumentException("No JSON file found in ZIP");
    }
}
