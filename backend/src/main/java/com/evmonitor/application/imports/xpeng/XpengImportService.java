package com.evmonitor.application.imports.xpeng;

import com.evmonitor.application.InternalTripRequest;
import com.evmonitor.application.TripService;
import com.evmonitor.application.publicapi.PublicApiImportService;
import com.evmonitor.application.publicapi.PublicApiSessionRequest;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.xpeng.DetectedChargingSession;
import com.evmonitor.domain.xpeng.DetectedTrip;
import com.evmonitor.domain.xpeng.VinUtils;
import com.evmonitor.domain.xpeng.XpengChargeDetector;
import com.evmonitor.domain.xpeng.XpengExcelStreamingParser;
import com.evmonitor.domain.xpeng.XpengParseException;
import com.evmonitor.domain.xpeng.XpengTripDetector;
import com.evmonitor.infrastructure.persistence.xpeng.XpengConnection;
import com.evmonitor.infrastructure.persistence.xpeng.XpengConnectionRepository;
import com.evmonitor.infrastructure.persistence.xpeng.XpengImportJob;
import com.evmonitor.infrastructure.persistence.xpeng.XpengImportJobRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pipeline for the XPeng XLSX import. Flow:
 *
 *   uploadXlsx() (sync, caller thread):
 *     - validate file + ownership + connection consent
 *     - hash file + reject duplicates
 *     - persist tempfile under restrictive permissions
 *     - create QUEUED XpengImportJob
 *     - hand off to async processJobAsync()
 *
 *   processJobAsync() (xpeng-import-* worker thread):
 *     - parse → state machines → bulk-insert trips + sessions
 *     - delete tempfile (always)
 *     - update job stats
 *
 * On backend crash, any QUEUED/PROCESSING jobs are marked FAILED at startup
 * via {@link #recoverInFlightJobs()}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class XpengImportService {

    private static final long MAX_UPLOAD_BYTES = 100L * 1024 * 1024;
    // Plain xlsx (ZIP container) starts with "PK\x03\x04".
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    // Password-protected xlsx uses OLE Compound File Format - POIFSFileSystem decrypts these.
    private static final byte[] OLE_CFB_MAGIC = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };

    private final XpengConnectionRepository connectionRepo;
    private final XpengImportJobRepository jobRepo;
    private final CarRepository carRepository;
    private final TripService tripService;
    private final PublicApiImportService publicApiImportService;
    private final ApplicationContext applicationContext;

    @Value("${xpeng.import.tempdir}")
    private String tempDir;

    /** Self-injection of the Spring proxy so @Async/@Transactional kick in on internal calls. */
    private XpengImportService self() {
        return applicationContext.getBean(XpengImportService.class);
    }

    @PostConstruct
    void initOnStartup() {
        try {
            Path dir = Paths.get(tempDir);
            Files.createDirectories(dir);
            cleanupStaleTempfiles(dir);
            self().recoverInFlightJobs();
        } catch (Exception e) {
            log.error("XpengImportService startup init failed", e);
        }
    }

    @Transactional
    public XpengImportJob uploadXlsx(UUID userId, UUID carId, InputStream content, String password,
                                      String clientIp, String userAgent) throws IOException {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
        if (!car.getUserId().equals(userId)) {
            throw new SecurityException("Dieses Fahrzeug gehört dir nicht");
        }
        XpengConnection connection = connectionRepo.findByCarId(carId)
                .filter(XpengConnection::isActive)
                .orElseThrow(() -> new IllegalStateException("Keine aktive XPeng-Vollmacht für dieses Fahrzeug"));

        Path tempfile = persistUpload(content);
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
                .connectionId(connection.getId())
                .status(XpengImportJob.Status.QUEUED)
                .fileHash(hash)
                .fileSizeBytes(size)
                .build());

        log.info("XpengImport: queued job={} car={} size={}MB hash={}",
                job.getId(), carId, size / 1_048_576, hash.substring(0, 8));

        // Trigger async only after the surrounding transaction has actually committed.
        // Otherwise a rollback would leave a dangling tempfile + non-existent job for the worker.
        final UUID jobId = job.getId();
        final String tempfilePath = tempfile.toString();
        final UUID connectionId = connection.getId();
        final XpengImportService proxy = self();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    proxy.processJobAsync(jobId, tempfilePath, password, connectionId);
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try { Files.deleteIfExists(Paths.get(tempfilePath)); } catch (Exception ignored) {}
                    }
                }
            });
        } else {
            // Should not happen given @Transactional on this method, but be defensive.
            proxy.processJobAsync(jobId, tempfilePath, password, connectionId);
        }
        return job;
    }

    @Async(XpengImportExecutorConfig.EXECUTOR_BEAN)
    public void processJobAsync(UUID jobId, String tempfilePath, String password, UUID connectionId) {
        Path tempfile = Paths.get(tempfilePath);
        try {
            jobRepo.findById(jobId).ifPresent(j -> {
                j.setStatus(XpengImportJob.Status.PROCESSING);
                j.setStartedAt(LocalDateTime.now());
                jobRepo.save(j);
            });

            ImportStats stats = runImport(jobId, tempfile, password);

            jobRepo.findById(jobId).ifPresent(j -> {
                j.setStatus(XpengImportJob.Status.DONE);
                j.setImportedTrips(stats.importedTrips);
                j.setImportedSessions(stats.importedSessions);
                j.setSkippedDuplicates(stats.skipped);
                j.setDataRangeStart(stats.rangeStart);
                j.setDataRangeEnd(stats.rangeEnd);
                j.setCompletedAt(LocalDateTime.now());
                jobRepo.save(j);
            });

            connectionRepo.findById(connectionId).ifPresent(c -> {
                c.setLastSuccessfulImportAt(LocalDateTime.now());
                c.setTotalImportsCount(c.getTotalImportsCount() + 1);
                connectionRepo.save(c);
            });

            log.info("XpengImport: job={} DONE trips={} sessions={} skipped={}",
                    jobId, stats.importedTrips, stats.importedSessions, stats.skipped);
        } catch (Exception e) {
            log.error("XpengImport: job={} FAILED", jobId, e);
            jobRepo.findById(jobId).ifPresent(j -> {
                j.setStatus(XpengImportJob.Status.FAILED);
                j.setErrorMessage(truncate(e.getMessage(), 500));
                j.setCompletedAt(LocalDateTime.now());
                jobRepo.save(j);
            });
        } finally {
            try { Files.deleteIfExists(tempfile); } catch (Exception ignored) {}
        }
    }

    private ImportStats runImport(UUID jobId, Path tempfile, String password) throws Exception {
        XpengImportJob job = jobRepo.findById(jobId).orElseThrow();
        UUID userId = job.getUserId();
        UUID carId = job.getCarId();

        XpengConnection connection = connectionRepo.findByCarId(carId).orElseThrow();
        String expectedVin = connection.getVin();

        XpengExcelStreamingParser parser = new XpengExcelStreamingParser();
        XpengTripDetector tripDet = new XpengTripDetector();
        XpengChargeDetector chargeDet = new XpengChargeDetector();

        List<DetectedTrip> trips = new ArrayList<>();
        List<DetectedChargingSession> sessions = new ArrayList<>();
        LocalDateTime[] range = new LocalDateTime[2];

        XpengExcelStreamingParser.ParseResult result = parser.parse(tempfile, password, row -> {
            if (range[0] == null || row.timer().isBefore(range[0])) range[0] = row.timer();
            if (range[1] == null || row.timer().isAfter(range[1])) range[1] = row.timer();
            tripDet.consume(row).ifPresent(trips::add);
            chargeDet.consume(row).ifPresent(sessions::add);
        });
        tripDet.finish().ifPresent(trips::add);
        chargeDet.finish().ifPresent(sessions::add);

        // VIN guard (strict): the file MUST contain a VIN, and it MUST match the connection.
        // A missing VIN means we cannot verify the file belongs to this car - reject rather than
        // letting potentially mismatched data land in the user's account.
        String fileVin = result.vehicleInfo().vin();
        if (fileVin == null || fileVin.isBlank()) {
            throw new XpengParseException("Die Excel-Datei enthält keine VIN - Zuordnung zum Fahrzeug nicht möglich.");
        }
        if (!fileVin.equalsIgnoreCase(expectedVin)) {
            throw new XpengParseException("VIN in der Datei (" + VinUtils.mask(fileVin)
                    + ") passt nicht zum gewählten Fahrzeug (" + VinUtils.mask(expectedVin) + ")");
        }

        ImportStats stats = new ImportStats();
        stats.rangeStart = range[0];
        stats.rangeEnd = range[1];

        // Trips → TripService (dedup via externalId)
        for (DetectedTrip t : trips) {
            try {
                UUID externalId = deterministicTripId(expectedVin, t.startedAt());
                UUID before = tripService.saveTrip(toTripRequest(externalId, carId, userId, t));
                stats.importedTrips++;
            } catch (Exception e) {
                log.warn("XpengImport: trip insert failed", e);
            }
        }

        // Charging sessions → PublicApiImportService (dedup via timestamp+kwh)
        if (!sessions.isEmpty()) {
            List<PublicApiSessionRequest.SessionEntry> entries = new ArrayList<>();
            for (DetectedChargingSession s : sessions) {
                entries.add(toSessionEntry(s));
            }
            var apiResult = publicApiImportService.importSessions(userId,
                    new PublicApiSessionRequest(carId, entries), DataSource.XPENG_IMPORT);
            stats.importedSessions = apiResult.imported();
            stats.skipped = apiResult.skipped();
        }

        return stats;
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
                .status("COMPLETED")
                // XPeng Phase 1: no GPS, no outside temp, no energy-remaining snapshots
                .build();
    }

    private PublicApiSessionRequest.SessionEntry toSessionEntry(DetectedChargingSession s) {
        return new PublicApiSessionRequest.SessionEntry(
                s.startedAt().atOffset(ZoneOffset.UTC).toString(),
                s.kwhCharged().doubleValue(),
                s.odometerKm() == null ? null : s.odometerKm().intValue(),
                s.socStart(), s.socEnd(),
                null,                              // costEur unknown
                durationMinutes(s),
                null,                              // location unknown
                s.chargingType(),
                s.maxPowerKw() == null ? null : s.maxPowerKw().doubleValue(),
                null, null, null,                  // routeType, tireType, rawImportData
                null, null,                        // isPublic, cpoName (user editiert nach)
                "AT_VEHICLE");
    }

    private Integer durationMinutes(DetectedChargingSession s) {
        if (s.startedAt() == null || s.endedAt() == null) return null;
        long minutes = java.time.Duration.between(s.startedAt(), s.endedAt()).toMinutes();
        return minutes <= 0 ? 1 : (int) minutes;
    }

    private static UUID deterministicTripId(String vin, LocalDateTime startedAt) {
        String key = (vin == null ? "" : vin) + "@" + startedAt.toString();
        return UUID.nameUUIDFromBytes(("xpeng:" + key).getBytes(StandardCharsets.UTF_8));
    }

    private Path persistUpload(InputStream content) throws IOException {
        Path dir = Paths.get(tempDir);
        Files.createDirectories(dir);
        Path tempfile;
        try {
            tempfile = Files.createTempFile(dir, "xpeng-", ".xlsx",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException e) {
            tempfile = Files.createTempFile(dir, "xpeng-", ".xlsx");
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
            if (matches(header, ZIP_MAGIC) || matches(header, OLE_CFB_MAGIC)) return;
            Files.deleteIfExists(file);
            throw new IllegalArgumentException("Keine gültige xlsx-Datei (Magic Bytes fehlen)");
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

    private void cleanupStaleTempfiles(Path dir) {
        try (var stream = Files.list(dir)) {
            long cutoff = System.currentTimeMillis() - 3_600_000L;
            stream.filter(p -> p.getFileName().toString().startsWith("xpeng-"))
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

    @Transactional
    public void recoverInFlightJobs() {
        int count = jobRepo.markAllInFlightAsFailed("Server-Neustart - Job wurde abgebrochen", LocalDateTime.now());
        if (count > 0) log.warn("XpengImport: marked {} stale jobs as FAILED on startup", count);
    }

    public Optional<XpengImportJob> getJobForUser(UUID jobId, UUID userId) {
        return jobRepo.findByIdAndUserId(jobId, userId);
    }

    public List<XpengImportJob> recentJobsForUser(UUID userId) {
        return jobRepo.findTop10ByUserIdOrderByCreatedAtDesc(userId);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static class ImportStats {
        int importedTrips, importedSessions, skipped;
        LocalDateTime rangeStart, rangeEnd;
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
