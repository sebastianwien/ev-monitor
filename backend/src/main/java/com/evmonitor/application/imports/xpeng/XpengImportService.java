package com.evmonitor.application.imports.xpeng;

import com.evmonitor.application.InternalTripRequest;
import com.evmonitor.application.TripService;
import com.evmonitor.application.publicapi.PublicApiImportService;
import com.evmonitor.application.publicapi.PublicApiSessionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.xpeng.XpengImportFormat;
import com.evmonitor.domain.xpeng.DetectedChargingSession;
import com.evmonitor.domain.xpeng.DetectedTrip;
import com.evmonitor.domain.xpeng.XpengChargeDetector;
import com.evmonitor.domain.xpeng.XpengParseException;
import com.evmonitor.domain.xpeng.XpengTripDetector;
import com.evmonitor.infrastructure.persistence.xpeng.XpengImportJob;
import com.evmonitor.infrastructure.persistence.xpeng.XpengImportJobRepository;
import com.evmonitor.application.imports.sample.ImportSampleService;
import com.evmonitor.application.ingest.event.ImportEventErrors;
import com.evmonitor.application.ingest.event.ImportEventOutcome;
import com.evmonitor.application.ingest.event.ImportEventRecorder;
import com.evmonitor.infrastructure.persistence.ingest.ImportEvent;
import com.evmonitor.domain.xpeng.XpengSampleAnonymizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Set;

/**
 * Pipeline for the manual XPeng EU-Data-Act ZIP import. Flow:
 *
 *   uploadCsvZip() (sync, caller thread):
 *     - validate file + ownership + VIN present in export
 *     - hash file + reject duplicates
 *     - persist tempfile under restrictive permissions
 *     - create QUEUED XpengImportJob
 *     - publish XpengImportJobQueuedEvent (wakes the worker after commit)
 *
 *   process() (called by XpengImportJobWorker, sequentially):
 *     - parse → state machines → bulk-insert trips + sessions
 *     - delete tempfile (always)
 *     - update job stats
 *
 * The job table is the queue; crash/restart recovery lives in {@link XpengImportJobWorker}.
 * Der User laedt seinen eigenen Data-Act-Export hoch - es gibt keine Vollmacht/Connection
 * und keinen Mail-Weg mehr; die VIN kommt ausschliesslich aus der Datei.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class XpengImportService {

    private static final long MAX_UPLOAD_BYTES = 100L * 1024 * 1024;
    private static final ObjectMapper EXTRAS_MAPPER = new ObjectMapper();
    // Der EU-Data-Act-Export ist ein ZIP-Container ("PK\x03\x04").
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    private final XpengImportJobRepository jobRepo;
    private final CarRepository carRepository;
    private final TripService tripService;
    private final PublicApiImportService publicApiImportService;
    private final XpengChargeMatcher chargeMatcher;
    private final ApplicationEventPublisher eventPublisher;
    private final com.evmonitor.domain.EvLogRepository evLogRepository;
    private final com.evmonitor.domain.EvTripRepository evTripRepository;
    private final ImportSampleService samples;
    private final ImportEventRecorder importEvents;

    @Value("${xpeng.import.tempdir}")
    private String tempDir;

    @EventListener(ApplicationReadyEvent.class)
    void initOnStartup() {
        try {
            Path dir = Paths.get(tempDir);
            Files.createDirectories(dir);
            cleanupStaleTempfiles(dir);
        } catch (Exception e) {
            log.error("XpengImportService startup init failed", e);
        }
    }

    /** EU-Data-Act-Format: ZIP mit unverschluesselten CSV-Clustern, kein Passwort, keine Vollmacht. */
    @Transactional
    public XpengImportJob uploadCsvZip(UUID userId, UUID carId, InputStream content,
                                       String clientIp, String userAgent) throws IOException {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
        if (!car.isOwnedBy(userId)) {
            throw new SecurityException("Dieses Fahrzeug gehört dir nicht");
        }
        Path tempfile;
        try {
            tempfile = persistUpload(content);
        } catch (IllegalArgumentException e) {
            logParseError(userId, carId, e);
            throw e;
        }
        try {
            validateZipHasVin(tempfile);
        } catch (RuntimeException e) {
            try { Files.deleteIfExists(tempfile); } catch (Exception ignored) {}
            if (e instanceof IllegalArgumentException) logParseError(userId, carId, e);
            throw e;
        }
        long size = Files.size(tempfile);
        String hash = sha256(tempfile);

        // Dedup nur fuer erfolgreiche/laufende Imports - FAILED-Jobs duerfen mit derselben Datei
        // wiederholt werden (z.B. nach VIN-Korrektur beim Auto).
        Optional<XpengImportJob> existing = jobRepo.findFirstByUserIdAndFileHashAndStatusIn(
                userId, hash, List.of(XpengImportJob.Status.QUEUED,
                        XpengImportJob.Status.PROCESSING, XpengImportJob.Status.DONE));
        if (existing.isPresent()) {
            try { Files.deleteIfExists(tempfile); } catch (Exception ignored) {}
            String msg = existing.get().getStatus() == XpengImportJob.Status.DONE
                    ? "Diese Datei wurde bereits importiert (Job " + existing.get().getId() + ")"
                    : "Ein Import dieser Datei laeuft bereits (Job " + existing.get().getId() + ")";
            throw new IllegalStateException(msg);
        }

        XpengImportJob job = jobRepo.save(XpengImportJob.builder()
                .userId(userId)
                .carId(carId)
                .status(XpengImportJob.Status.QUEUED)
                .fileHash(hash)
                .fileSizeBytes(size)
                .tempfilePath(tempfile.toString())
                .format(XpengImportFormat.CSV_ZIP)
                .build());

        log.info("XpengImport: queued job={} car={} size={}MB hash={}",
                job.getId(), carId, size / 1_048_576, hash.substring(0, 8));

        // Der Worker claimt den Job nach dem Commit; ein Rollback laesst das Tempfile liegen,
        // das raeumt cleanupStaleTempfiles() beim naechsten Start auf.
        eventPublisher.publishEvent(new XpengImportJobQueuedEvent(job.getId()));
        return job;
    }

    /**
     * Verarbeitet einen vom Worker geclaimten Job (Status PROCESSING) bis DONE oder FAILED.
     * Loescht das Tempfile in jedem Fall und entfernt die Datei-Referenzen aus dem Job.
     */
    public void process(XpengImportJob job) {
        UUID jobId = job.getId();
        Path tempfile = Paths.get(job.getTempfilePath());
        try {
            ImportStats stats = runImport(job, tempfile);

            job.setStatus(XpengImportJob.Status.DONE);
            job.setImportedTrips(stats.importedTrips);
            job.setImportedSessions(stats.importedSessions);
            job.setSkippedDuplicates(stats.skipped + stats.skippedTrips);
            job.setDataRangeStart(toUtc(stats.rangeStart));
            job.setDataRangeEnd(toUtc(stats.rangeEnd));
            job.setCompletedAt(LocalDateTime.now());

            log.info("XpengImport: job={} DONE trips={} sessions={} skipped={} skippedTrips={}",
                    jobId, stats.importedTrips, stats.importedSessions, stats.skipped, stats.skippedTrips);
        } catch (Throwable e) {
            // Bewusst Throwable, nicht nur Exception: ein grosser Export kann beim Parsen einen
            // OutOfMemoryError ausloesen. Der Job muss auch dann als FAILED enden, statt in
            // PROCESSING haengen zu bleiben.
            log.error("XpengImport: job={} FAILED", jobId, e);
            job.setStatus(XpengImportJob.Status.FAILED);
            job.setErrorMessage(truncate(e.getMessage(), 500));
            job.setCompletedAt(LocalDateTime.now());
            // Was bis zum Gateway kam, protokolliert das Gateway selbst; hier nur der Abbruch des Jobs.
            boolean unreadable = e instanceof XpengParseException || e instanceof IllegalArgumentException;
            importEvents.record(ImportEvent.of(DataSource.XPENG_IMPORT, job.getUserId(), job.getCarId())
                    .outcome(unreadable ? ImportEventOutcome.PARSE_ERROR : ImportEventOutcome.FAILED)
                    .error(ImportEventErrors.describe(e))
                    .build());
        } finally {
            recordSample(job, tempfile);
            try { Files.deleteIfExists(tempfile); } catch (Exception ignored) {}
            job.clearFileReferences();
            jobRepo.save(job);
        }
    }

    /** Nicht lesbarer Upload: das Gateway wird nie gerufen, also hier ins Import-Protokoll. */
    private void logParseError(UUID userId, UUID carId, RuntimeException e) {
        importEvents.record(ImportEvent.of(DataSource.XPENG_IMPORT, userId, carId)
                .outcome(ImportEventOutcome.PARSE_ERROR)
                .error(ImportEventErrors.describe(e))
                .build());
    }

    /** Pseudonymisierte Kopie des CSV-Exports ablegen (Best Effort, XLSX wird uebersprungen). */
    private void recordSample(XpengImportJob job, Path tempfile) {
        if (!samples.isEnabled() || job.getFileHash() == null) return;
        boolean done = job.getStatus() == XpengImportJob.Status.DONE;
        String carModel = carRepository.findById(job.getCarId())
                .map(c -> c.getModel() != null ? c.getModel().name() : null).orElse(null);
        samples.record(new ImportSampleService.SampleMeta(
                        job.getUserId(), ImportSampleService.Provider.XPENG,
                        ImportSampleService.Channel.UPLOAD, carModel, job.getFileHash(),
                        done ? ImportSampleService.Outcome.OK
                             : ImportSampleService.Outcome.FAILED,
                        job.getImportedSessions(), job.getImportedTrips(), job.getErrorMessage()),
                () -> new XpengSampleAnonymizer(samples.maxBytes())
                        .anonymize(tempfile, tempfile.getFileName().toString())
                        .map(r -> new ImportSampleService.Anonymized(r.fileName(), r.zip())));
    }

    private ImportStats runImport(XpengImportJob job, Path tempfile) throws Exception {
        UUID userId = job.getUserId();
        UUID carId = job.getCarId();

        XpengTripDetector tripDet = new XpengTripDetector();
        XpengChargeDetector chargeDet = new XpengChargeDetector();

        List<DetectedTrip> trips = new ArrayList<>();
        List<DetectedChargingSession> sessions = new ArrayList<>();
        Instant[] range = new Instant[2];
        java.util.function.Consumer<com.evmonitor.domain.xpeng.XpengTelematicsRow> rowHandler = row -> {
            if (range[0] == null || row.timer().isBefore(range[0])) range[0] = row.timer();
            if (range[1] == null || row.timer().isAfter(range[1])) range[1] = row.timer();
            tripDet.consume(row).ifPresent(trips::add);
            chargeDet.consume(row).ifPresent(sessions::add);
        };

        String fileVin = parseTelematics(tempfile, rowHandler);
        tripDet.finish().ifPresent(trips::add);
        chargeDet.finish().ifPresent(sessions::add);

        // VIN guard: die Datei MUSS eine VIN enthalten - sie ist der stabile Schluessel fuer die
        // deterministische Trip-ID (Re-Import-Dedup). Das gewaehlte Auto bestimmt der User in der UI;
        // eine gespeicherte Fahrzeug-VIN zum Gegenpruefen gibt es seit dem Wegfall der Connection nicht.
        if (fileVin == null || fileVin.isBlank()) {
            throw new XpengParseException("Die Datei enthält keine VIN - Zuordnung zum Fahrzeug nicht möglich.");
        }

        ImportStats stats = new ImportStats();
        stats.rangeStart = range[0];
        stats.rangeEnd = range[1];

        // Trips → TripService. Primaer-Dedup ueber externalId (VIN@startedAt);
        // zusaetzlich ein stabiler Guard ueber den Start-Kilometerstand, damit ein
        // kuenftiges Detektor-Update (verschobene Startsekunde) beim Re-Import keine
        // Doppel-Trips erzeugt.
        for (DetectedTrip t : trips) {
            try {
                if (isTripAlreadyImported(carId, t)) {
                    stats.skippedTrips++;
                    continue;
                }
                UUID externalId = deterministicTripId(fileVin, t.startedAt());
                tripService.saveTrip(toTripRequest(externalId, carId, userId, t));
                stats.importedTrips++;
            } catch (Exception e) {
                log.warn("XpengImport: trip insert failed", e);
            }
        }

        // Charging sessions: erst existierende Logs anreichern (Odo ±1 km + Zeit ±10 min),
        // restliche unmatched Sessions gehen den normalen API-Sink-Weg.
        if (!sessions.isEmpty()) {
            XpengChargeMatcher.MatchResult match = chargeMatcher.matchAndEnrich(carId, sessions);
            stats.importedSessions = match.enriched();

            List<DetectedChargingSession> unmatched = match.unmatched();
            if (!unmatched.isEmpty()) {
                List<PublicApiSessionRequest.SessionEntry> entries = new ArrayList<>();
                for (DetectedChargingSession s : unmatched) {
                    entries.add(toSessionEntry(s));
                }
                var apiResult = publicApiImportService.importSessions(userId,
                        new PublicApiSessionRequest(carId, entries), DataSource.XPENG_IMPORT);
                stats.importedSessions += apiResult.imported();
                stats.skipped = apiResult.skipped();

                // telemetry_extras kann SessionEntry (public API) nicht durchreichen -
                // nach dem Import per Direkt-Update setzen, identifiziert ueber (carId, loggedAt).
                for (DetectedChargingSession s : unmatched) {
                    String json = serializeExtras(s.telemetryExtras());
                    if (json != null) {
                        try {
                            evLogRepository.updateTelemetryExtras(carId, toUtc(s.startedAt()), json);
                        } catch (Exception e) {
                            log.warn("XpengImport: telemetry_extras update for ev_log failed", e);
                        }
                    }
                }
            }
        }

        return stats;
    }

    /** Parst die CSV-Cluster aus dem ZIP und liefert die im Export gefundene VIN. */
    private String parseTelematics(Path tempfile,
                                   java.util.function.Consumer<com.evmonitor.domain.xpeng.XpengTelematicsRow> rowHandler)
            throws Exception {
        return new com.evmonitor.domain.xpeng.XpengCsvExportParser()
                .parse(tempfile, rowHandler).vehicleInfo().vin();
    }

    private String serializeExtras(java.util.Map<String, Object> extras) {
        if (extras == null || extras.isEmpty()) return null;
        try {
            return EXTRAS_MAPPER.writeValueAsString(extras);
        } catch (Exception e) {
            log.warn("XpengImport: telemetry_extras JSON serialize failed", e);
            return null;
        }
    }

    private InternalTripRequest toTripRequest(UUID externalId, UUID carId, UUID userId, DetectedTrip t) {
        return InternalTripRequest.builder()
                .externalId(externalId)
                .carId(carId)
                .userId(userId)
                .dataSource(DataSource.XPENG_IMPORT.name())
                .tripStartedAt(t.startedAt().atOffset(ZoneOffset.UTC))
                .tripEndedAt(t.endedAt().atOffset(ZoneOffset.UTC))
                .socStart(t.socStart())
                .socEnd(t.socEnd())
                .odometerStartKm(t.odometerStartKm())
                .odometerEndKm(t.odometerEndKm())
                .distanceKm(t.distanceKm())
                .estimatedConsumedKwh(t.consumedKwh())
                .avgSpeedKmh(t.avgSpeedKmh())
                .maxSpeedKmh(t.maxSpeedKmh())
                .status("COMPLETED")
                .telemetryExtras(serializeExtras(t.telemetryExtras()))
                // XPeng Phase 1: no GPS, no outside temp, no energy-remaining snapshots
                .build();
    }

    /**
     * Mappt eine erkannte XPeng-Charging-Session auf die Public-API-DTO.
     *
     * <p>Energie-Konvention: {@code chrgpwr} (XPeng) ist die AC-Leistung am Onboard-Charger-Eingang -
     * also <b>brutto</b> wie ein Wallbox-Zaehler, daher {@code measurement_type = AT_CHARGER}.
     * {@code kwhAtVehicle} (Netto, aus U x I integriert) wird zusaetzlich uebergeben, falls
     * vorhanden - die Public-API speichert beides nebeneinander.
     *
     * <p>Static + package-private, damit der Mapper ohne Service-Bean testbar ist.
     */
    static PublicApiSessionRequest.SessionEntry toSessionEntry(DetectedChargingSession s) {
        return new PublicApiSessionRequest.SessionEntry(
                s.startedAt().atOffset(ZoneOffset.UTC).toString(),
                s.kwhCharged().doubleValue(),
                s.kwhAtVehicle() == null ? null : s.kwhAtVehicle().doubleValue(),
                s.odometerKm() == null ? null : s.odometerKm().intValue(),
                s.socStart(), s.socEnd(),
                null,                              // costEur unknown
                durationMinutes(s),
                null,                              // location unknown
                s.chargingType(),
                s.maxPowerKw() == null ? null : s.maxPowerKw().doubleValue(),
                null, null, null,                  // routeType, tireType, rawImportData
                null, null,                        // isPublic, cpoName (user editiert nach)
                "AT_CHARGER",
                null);                             // temperatureCelsius unknown
    }

    static Integer durationMinutes(DetectedChargingSession s) {
        if (s.startedAt() == null || s.endedAt() == null) return null;
        long minutes = java.time.Duration.between(s.startedAt(), s.endedAt()).toMinutes();
        return minutes <= 0 ? 1 : (int) minutes;
    }

    /** Zeitfenster, in dem nach einem bereits importierten Gegenstueck gesucht wird. */
    private static final java.time.Duration TRIP_DEDUP_WINDOW = java.time.Duration.ofMinutes(15);

    /**
     * Stabiler Zweit-Dedup: gibt es fuer dieses Fahrzeug bereits einen XPeng-Trip mit
     * praktisch gleichem Start-Kilometerstand und nahezu gleicher Startzeit? Faengt
     * Re-Imports ab, deren Startsekunde sich durch ein Detektor-Update verschoben hat.
     */
    private boolean isTripAlreadyImported(UUID carId, DetectedTrip t) {
        if (t.startedAt() == null || t.odometerStartKm() == null) return false;
        OffsetDateTime start = t.startedAt().atOffset(ZoneOffset.UTC);
        List<com.evmonitor.domain.EvTrip> candidates =
                evTripRepository.findByCarIdAndTripStartedAtBetweenOrderByTripStartedAtAsc(
                        carId, start.minus(TRIP_DEDUP_WINDOW), start.plus(TRIP_DEDUP_WINDOW));
        return com.evmonitor.domain.xpeng.XpengTripDeduplicator.isAlreadyImported(candidates, t);
    }

    /** {@code ev_log.logged_at} / Job-Zeitraum sind UTC-Wanduhrzeit - einzige Umrechnung vom absoluten Zeitpunkt. */
    private static LocalDateTime toUtc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static UUID deterministicTripId(String vin, Instant startedAt) {
        String key = (vin == null ? "" : vin) + "@" + startedAt.toString();
        return UUID.nameUUIDFromBytes(("xpeng:" + key).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Guenstiger Vorab-Check: der Export muss eine gueltige 17-stellige VIN enthalten, sonst wird
     * der Upload sofort abgelehnt (bevor ein Job entsteht). Keine Persistenz, keine Vollmacht -
     * das gewaehlte Auto bestimmt der User in der UI.
     */
    private void validateZipHasVin(Path tempfile) {
        String fileVin;
        try {
            fileVin = com.evmonitor.domain.xpeng.XpengCsvExportParser.peekVin(tempfile);
        } catch (IOException e) {
            throw new IllegalArgumentException("ZIP konnte nicht gelesen werden");
        }
        if (fileVin == null || fileVin.length() != 17) {
            throw new IllegalArgumentException("Keine gültige VIN im Export gefunden");
        }
    }

    private Path persistUpload(InputStream content) throws IOException {
        Path dir = Paths.get(tempDir);
        Files.createDirectories(dir);
        String suffix = ".zip";
        Path tempfile;
        try {
            tempfile = Files.createTempFile(dir, "xpeng-", suffix,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException e) {
            tempfile = Files.createTempFile(dir, "xpeng-", suffix);
        }
        long bytes;
        try {
            bytes = Files.copy(new BoundedInputStream(content, MAX_UPLOAD_BYTES), tempfile,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tempfile);
            throw e;
        }
        if (bytes < 4) {
            Files.deleteIfExists(tempfile);
            throw new IllegalArgumentException("Datei ist zu klein");
        }
        validateMagicBytes(tempfile);
        return tempfile;
    }

    private void validateMagicBytes(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] header = in.readNBytes(8);
            if (matches(header, ZIP_MAGIC)) return;
            Files.deleteIfExists(file);
            throw new IllegalArgumentException("Keine gültige ZIP-Datei (Magic Bytes fehlen)");
        }
    }

    private static boolean matches(byte[] header, byte[] magic) {
        if (header.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (header[i] != magic[i]) return false;
        }
        return true;
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) digest.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IOException("hash computation failed", e);
        }
    }

    /** Loescht alte Tempfiles, die kein wartender Job mehr referenziert (z.B. nach Rollback). */
    private void cleanupStaleTempfiles(Path dir) {
        Set<String> referenced = jobRepo.findAllByStatus(XpengImportJob.Status.QUEUED).stream()
                .map(XpengImportJob::getTempfilePath)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        try (var stream = Files.list(dir)) {
            long cutoff = System.currentTimeMillis() - 3_600_000L;
            stream.filter(p -> p.getFileName().toString().startsWith("xpeng-"))
                  .filter(p -> !referenced.contains(p.toString()))
                  .filter(p -> {
                      try { return Files.getLastModifiedTime(p).toMillis() < cutoff; }
                      catch (Exception e) { return false; }
                  })
                  .forEach(p -> {
                      try { Files.deleteIfExists(p); }
                      catch (Exception e) { log.warn("could not delete stale tempfile {}", p, e); }
                  });
        } catch (Exception e) {
            log.warn("cleanupStaleTempfiles failed", e);
        }
    }

    public Optional<XpengImportJob> getJobForUser(UUID jobId, UUID userId) {
        return jobRepo.findByIdAndUserId(jobId, userId);
    }

    public List<XpengImportJob> recentJobsForUser(UUID userId) {
        return jobRepo.findTop10ByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Loescht alle XPENG_IMPORT-Daten des Users: ev_log, ev_trip und xpeng_import_jobs
     * (alles Hard-Delete, damit der User die Daten ohne Dedup-Block neu hochladen kann).
     */
    @Transactional
    public DeleteSummary deleteAllImportedData(UUID userId) {
        int chargingLogs = evLogRepository.countByUserIdAndDataSource(
                userId, com.evmonitor.domain.DataSource.XPENG_IMPORT);
        int trips = evTripRepository.deleteAllByUserIdAndDataSource(userId, "XPENG_IMPORT");
        long jobs = jobRepo.deleteAllByUserId(userId);
        evLogRepository.deleteAllByUserIdAndDataSource(
                userId, com.evmonitor.domain.DataSource.XPENG_IMPORT);
        log.info("XpengImport delete-all by user {}: logs={} trips={} jobs={}",
                userId, chargingLogs, trips, jobs);
        return new DeleteSummary(chargingLogs, trips, jobs);
    }

    public record DeleteSummary(int chargingLogs, int trips, long importJobs) {}

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static class ImportStats {
        int importedTrips, importedSessions, skipped, skippedTrips;
        Instant rangeStart, rangeEnd;
    }

    /** Caps total bytes read to prevent oversized uploads from filling tempdir. */
    private static class BoundedInputStream extends java.io.FilterInputStream {
        private final long limit;
        private long count;

        BoundedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            if (count >= limit) throw new IOException("Datei überschreitet Größenlimit von " + limit + " Bytes");
            int b = super.read();
            if (b >= 0) count++;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (count >= limit) throw new IOException("Datei überschreitet Größenlimit von " + limit + " Bytes");
            int max = (int) Math.min(len, limit - count);
            int n = super.read(b, off, max);
            if (n > 0) count += n;
            return n;
        }
    }
}
