package com.evmonitor.application.imports.vweuda;

import com.evmonitor.application.imports.sample.ImportSampleService;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.application.publicapi.PublicApiImportService;
import com.evmonitor.application.publicapi.PublicApiSessionRequest;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.InputStreamSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Slf4j
public class VwEudaImportService {

    // VW-Exporte komprimieren stark (MEB entpackt ~24 MB, PPE ~46 MB, Historien-Export ~170 MB
    // bei 13 MB gepackt). Hochgeladen wird beides, ZIP wie entpackte JSON. Der Parser streamt und
    // behaelt nur die benoetigten Eintraege - der Guard schuetzt nur noch gegen ZIP-Bomben.
    private static final long DEFAULT_MAX_UNZIPPED_BYTES = 1024 * 1024 * 1024L; // 1 GB
    private static final int MAX_ZIP_ENTRIES = 10;

    private final VwEudaJsonParser parser;
    private final CarRepository carRepository;
    private final PublicApiImportService publicApiImportService;
    private final long maxUnzippedBytes;
    private final ImportSampleService samples;
    private final VwEudaSampleAnonymizer anonymizer = new VwEudaSampleAnonymizer(new ObjectMapper());

    @Autowired
    public VwEudaImportService(VwEudaJsonParser parser, CarRepository carRepository,
                                  PublicApiImportService publicApiImportService, ImportSampleService samples) {
        this(parser, carRepository, publicApiImportService, samples, DEFAULT_MAX_UNZIPPED_BYTES);
    }

    /** Fuer Tests: der Entpack-Guard laesst sich nur mit kleinem Limit sinnvoll pruefen. */
    VwEudaImportService(VwEudaJsonParser parser, CarRepository carRepository,
                           PublicApiImportService publicApiImportService, ImportSampleService samples,
                           long maxUnzippedBytes) {
        this.parser = parser;
        this.carRepository = carRepository;
        this.publicApiImportService = publicApiImportService;
        this.samples = samples;
        this.maxUnzippedBytes = maxUnzippedBytes;
    }

    public VwEudaPreviewResult preview(UUID userId, UUID carId, InputStreamSource file, String originalFilename)
            throws IOException {
        Car car = requireOwnedCar(userId, carId);
        VwEudaParseResult parsed = parseAndRecord(file, originalFilename, car, userId, ImportSampleService.Channel.UPLOAD);
        return VwEudaPreviewResult.from(parsed);
    }

    public ImportApiResult importData(UUID userId, UUID carId, InputStreamSource file, String originalFilename)
            throws IOException {
        return importData(userId, carId, file, originalFilename, DataSource.EU_DATA_ACT_IMPORT);
    }

    /** {@code dataSource} unterscheidet manuellen Upload (IMPORT) und AutoSync (SYNC) - gleicher Parser. */
    public ImportApiResult importData(UUID userId, UUID carId, InputStreamSource file, String originalFilename,
                                      DataSource dataSource) throws IOException {
        return importData(userId, carId, file, originalFilename, dataSource, false);
    }

    /**
     * {@code lenient} nur fuer den laufenden 15-Minuten-Feed: dort ist eine Datei ohne Ladedaten
     * der Normalfall. Fuer den einmaligen Historien-Export und den manuellen Upload bleibt es
     * strikt - ein unerkanntes Format duerfte dort nicht als "keine Ladevorgaenge" durchgehen.
     */
    public ImportApiResult importData(UUID userId, UUID carId, InputStreamSource file, String originalFilename,
                                      DataSource dataSource, boolean lenient) throws IOException {
        Car car = requireOwnedCar(userId, carId);
        // Laufende 15-Minuten-Drops liegen roh in Connectors - kopiert werden nur Uploads und Historien-Exporte
        VwEudaParseResult parsed = lenient
                ? parse(file, originalFilename, car, true)
                : parseAndRecord(file, originalFilename, car, userId, dataSource == DataSource.EU_DATA_ACT_SYNC
                        ? ImportSampleService.Channel.HISTORY : ImportSampleService.Channel.UPLOAD);

        List<PublicApiSessionRequest.SessionEntry> entries = toSessionEntries(parsed.sessions(), car);
        if (entries.isEmpty()) {
            return ImportApiResult.withoutIds(0, 0, 0);
        }

        return publicApiImportService.importSessions(
                userId,
                new PublicApiSessionRequest(carId, entries),
                dataSource
        );
    }

    /** Parst strikt und legt eine pseudonymisierte Kopie ab - auch (gerade) wenn die Datei nicht lesbar ist. */
    private VwEudaParseResult parseAndRecord(InputStreamSource file, String originalFilename, Car car, UUID userId,
                                                ImportSampleService.Channel channel) throws IOException {
        try {
            VwEudaParseResult parsed = parse(file, originalFilename, car, false);
            recordSample(file, originalFilename, car, userId, channel, parsed.sessions().isEmpty()
                    ? ImportSampleService.Outcome.NO_SESSIONS : ImportSampleService.Outcome.OK, parsed.sessions().size(), null);
            return parsed;
        } catch (VwEudaUnreadableException e) {
            recordSample(file, originalFilename, car, userId, channel, ImportSampleService.Outcome.UNREADABLE, null, e.getMessage());
            throw e;
        }
    }

    private void recordSample(InputStreamSource file, String originalFilename, Car car, UUID userId,
                              ImportSampleService.Channel channel, ImportSampleService.Outcome outcome,
                              Integer sessions, String error) {
        if (!samples.isEnabled()) return;
        String sha;
        try {
            sha = ImportSampleService.sha256(file.getInputStream());
        } catch (IOException e) {
            return;
        }
        samples.record(new ImportSampleService.SampleMeta(userId, ImportSampleService.Provider.VW_EUDA, channel,
                        car.getModel() != null ? car.getModel().name() : null, sha, outcome, sessions, null, error),
                () -> {
                    VwEudaSampleAnonymizer.Result r = anonymizer.anonymize(
                            toJsonStream(file.getInputStream(), originalFilename), originalFilename);
                    return Optional.of(new ImportSampleService.Anonymized(r.fileName(), r.zip()));
                });
    }

    private Car requireOwnedCar(UUID userId, UUID carId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
        if (!car.isOwnedBy(userId)) {
            throw new SecurityException("Dieses Fahrzeug gehört dir nicht");
        }
        return car;
    }

    private VwEudaParseResult parse(InputStreamSource file, String originalFilename, Car car, boolean lenient)
            throws IOException {
        VwEudaParseResult parsed;
        try {
            parsed = parser.parse(() -> toJsonStream(file.getInputStream(), originalFilename), lenient);
        } catch (VwEudaUnreadableException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            // Parser, Entpacker und Groessen-Guard melden Dateiprobleme als IllegalArgumentException.
            // Als eigene Klasse weitergeben, damit der AutoSync sie von fachlichen Fehlern
            // (unbekanntes Fahrzeug, fehlende Rechte) unterscheiden kann.
            throw new VwEudaUnreadableException(e.getMessage(), e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Kaputtes JSON ist ebenfalls ein Dateiproblem - als IOException wuerde es sonst
            // einen Serverfehler ergeben und der AutoSync liefe endlos dagegen.
            throw new VwEudaUnreadableException("Datei ist kein lesbares JSON", e);
        }
        List<VwEudaSession> sessions = parsed.sessions().stream()
                .map(s -> withKwhFromSocIfMissing(s, car))
                .toList();
        return new VwEudaParseResult(parsed.vin(), sessions);
    }

    /**
     * Die MEB-Variante liefert kein Leistungssignal - dort ergeben sich die kWh aus dem
     * SoC-Zuwachs und der SoH-adjustierten Kapazitaet. Beide Werte sind fahrzeugseitig
     * gemessen, es entsteht also kein Bruch zur Integration des anderen Formats.
     */
    private VwEudaSession withKwhFromSocIfMissing(VwEudaSession s, Car car) {
        BigDecimal capacity = car.getEffectiveBatteryCapacityKwh();
        if (s.calculatedKwh() != null || s.socDeltaPct() == null || capacity == null) return s;

        double kwh = s.socDeltaPct() / 100.0 * capacity.doubleValue();
        return new VwEudaSession(
                s.startedAt(), s.endedAt(), s.durationMin(),
                s.socBefore(), s.socAfter(), s.socDeltaPct(),
                s.chargeType(), s.maxChargingPowerKw(), kwh,
                s.odometerKm(), s.temperatureCelsius());
    }

    private List<PublicApiSessionRequest.SessionEntry> toSessionEntries(List<VwEudaSession> sessions, Car car) {
        BigDecimal batteryKwh = car.getEffectiveBatteryCapacityKwh();
        List<PublicApiSessionRequest.SessionEntry> entries = new ArrayList<>();

        for (VwEudaSession s : sessions) {
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
            return new SizeLimitedInputStream(zip, maxUnzippedBytes);
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
