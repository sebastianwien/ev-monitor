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

import java.io.FilterInputStream;
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

    // VW-Exporte komprimieren stark (MEB entpackt ~24 MB, PPE ~46 MB - gepackt jeweils wenige MB).
    // Hochgeladen wird beides, ZIP wie entpackte JSON. Der Guard schuetzt gegen ZIP-Bomben und
    // deckelt zugleich den Heap; gleicher Wert wie MAX_UPLOAD_BYTES im Controller.
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

            // Der Parser streamt - also auch hier streamen statt entpackt zu puffern.
            // Der Bomben-Guard zaehlt die Bytes im Vorbeifliegen.
            return new SizeLimitedInputStream(zip, MAX_UNZIPPED_BYTES);
        }
        throw new IllegalArgumentException("No JSON file found in ZIP");
    }

    /** Bricht ab, sobald mehr als {@code limit} Bytes gelesen wurden (ZIP-Bomben-Schutz). */
    private static class SizeLimitedInputStream extends FilterInputStream {

        private final long limit;
        private long total;

        SizeLimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) count(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = super.read(b, off, len);
            if (read != -1) count(read);
            return read;
        }

        private void count(int read) {
            total += read;
            if (total > limit) {
                throw new IllegalArgumentException(
                        "Entpackte Datei zu groß (max. " + limit / (1024 * 1024) + " MB)");
            }
        }
    }
}
