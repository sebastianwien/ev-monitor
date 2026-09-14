package com.evmonitor.application.imports.xpeng;

import com.evmonitor.application.TripService;
import com.evmonitor.application.publicapi.PublicApiImportService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.CarStatus;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.xpeng.XpengImportFormat;
import com.evmonitor.infrastructure.persistence.xpeng.XpengImportJob;
import com.evmonitor.infrastructure.persistence.xpeng.XpengImportJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Manueller EU-Data-Act-ZIP-Import. Der Mail-Weg / die Vollmacht (xpeng_connection) sind
 * entfernt - der Import haengt nur noch an userId + carId + der VIN aus der Datei.
 */
@ExtendWith(MockitoExtension.class)
class XpengImportServiceTest {

    @Mock CarRepository carRepository;
    @Mock XpengImportJobRepository jobRepo;
    @Mock TripService tripService;
    @Mock PublicApiImportService publicApiImportService;
    @Mock XpengChargeMatcher chargeMatcher;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock EvLogRepository evLogRepository;
    @Mock EvTripRepository evTripRepository;

    @InjectMocks XpengImportService service;

    private static final UUID USER = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();
    private static final UUID CAR = UUID.randomUUID();
    private static final String VIN = "L1NN12345678ABCDE";

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("xpeng-test-");
        ReflectionTestUtils.setField(service, "tempDir", tempDir.toString());
    }

    @Test
    void rejectsUploadForCarOfOtherUser() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(OTHER_USER)));
        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.uploadCsvZip(USER, CAR, validZipStream(), "1.1.1.1", "ua"));
        assertTrue(ex.getMessage().contains("gehört"));
        verify(jobRepo, never()).save(any());
    }

    @Test
    void rejectsNonZipByMagicBytes() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.uploadCsvZip(USER, CAR,
                        new ByteArrayInputStream("not a zip file".getBytes()), "1.1.1.1", "ua"));
        assertTrue(ex.getMessage().toLowerCase().contains("magic"),
                "Expected magic-bytes failure, got: " + ex.getMessage());
        verify(jobRepo, never()).save(any());
    }

    @Test
    void rejectsOleEncryptedFile() {
        // Der Export ist immer ein echtes ZIP - verschluesseltes OLE ist ungueltig.
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        byte[] ole = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0};
        assertThrows(IllegalArgumentException.class,
                () -> service.uploadCsvZip(USER, CAR, new ByteArrayInputStream(ole), "1.1.1.1", "ua"));
        verify(jobRepo, never()).save(any());
    }

    @Test
    void rejectsTinyFile() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.uploadCsvZip(USER, CAR,
                        new ByteArrayInputStream(new byte[]{1, 2}), "1.1.1.1", "ua"));
        assertTrue(ex.getMessage().contains("klein"));
    }

    @Test
    void rejectsZipWithoutValidVin() throws Exception {
        // Ein gueltiges ZIP ohne verwertbare VIN wird vor dem Anlegen eines Jobs abgelehnt.
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        byte[] zip = Files.readAllBytes(writeCsvZip("SHORTVIN"));
        assertThrows(IllegalArgumentException.class,
                () -> service.uploadCsvZip(USER, CAR, new ByteArrayInputStream(zip), "1.1.1.1", "ua"));
        verify(jobRepo, never()).save(any());
    }

    @Test
    void queuesJobForValidZip() throws Exception {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        when(jobRepo.findFirstByUserIdAndFileHashAndStatusIn(eq(USER), anyString(),
                anyCollection())).thenReturn(Optional.empty());
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        byte[] zip = Files.readAllBytes(writeCsvZip(VIN));

        XpengImportJob job = service.uploadCsvZip(USER, CAR, new ByteArrayInputStream(zip), "1.1.1.1", "ua");

        assertEquals(XpengImportJob.Status.QUEUED, job.getStatus());
        assertEquals(XpengImportFormat.CSV_ZIP, job.getFormat());
        assertTrue(Files.exists(Path.of(job.getTempfilePath())), "Tempfile bleibt fuer den Worker liegen");
        verify(eventPublisher).publishEvent(any(XpengImportJobQueuedEvent.class));
    }

    @Test
    void allowsReuploadAfterPreviousAttemptFailed() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        when(jobRepo.findFirstByUserIdAndFileHashAndStatusIn(eq(USER), anyString(),
                anyCollection())).thenReturn(Optional.empty());
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertDoesNotThrow(() -> service.uploadCsvZip(USER, CAR, validZipStream(), "1.1.1.1", "ua"));
        verify(jobRepo).save(any());
    }

    @Test
    void blocksReuploadWhilePreviousJobStillRunning() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        XpengImportJob inFlight = XpengImportJob.builder()
                .id(UUID.randomUUID()).userId(USER).carId(CAR)
                .status(XpengImportJob.Status.PROCESSING).build();
        when(jobRepo.findFirstByUserIdAndFileHashAndStatusIn(eq(USER), anyString(),
                anyCollection())).thenReturn(Optional.of(inFlight));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.uploadCsvZip(USER, CAR, validZipStream(), "1.1.1.1", "ua"));
        assertTrue(ex.getMessage().contains("bereits"));
        verify(jobRepo, never()).save(any());
    }

    @Test
    void process_validZip_marksDoneAndClearsFileReferences() throws Exception {
        Path zip = writeCsvZip(VIN);
        XpengImportJob job = processingJob(zip);
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(job);

        assertEquals(XpengImportJob.Status.DONE, job.getStatus());
        assertNull(job.getTempfilePath(), "Datei-Referenz wird nach Verarbeitung entfernt");
        assertFalse(Files.exists(zip), "Tempfile wird nach Verarbeitung geloescht");
    }

    @Test
    void process_marksFailedWhenProcessingThrows() {
        // Regression: ein Fehler beim Verarbeiten (hier: fehlendes Tempfile) darf den Job NICHT
        // stumm in PROCESSING haengen lassen - er muss als FAILED enden.
        XpengImportJob job = processingJob(tempDir.resolve("nonexistent.zip"));
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(job);

        ArgumentCaptor<XpengImportJob> captor = ArgumentCaptor.forClass(XpengImportJob.class);
        verify(jobRepo, atLeastOnce()).save(captor.capture());
        assertEquals(XpengImportJob.Status.FAILED, captor.getValue().getStatus());
    }

    @Test
    void deleteAllImportedData_clearsLogsTripsAndJobs() {
        when(evLogRepository.countByUserIdAndDataSource(USER, DataSource.XPENG_IMPORT)).thenReturn(42);
        when(evTripRepository.deleteAllByUserIdAndDataSource(USER, "XPENG_IMPORT")).thenReturn(70);
        when(jobRepo.deleteAllByUserId(USER)).thenReturn(4L);

        XpengImportService.DeleteSummary s = service.deleteAllImportedData(USER);

        assertEquals(42, s.chargingLogs());
        assertEquals(70, s.trips());
        assertEquals(4L, s.importJobs());
        verify(evLogRepository).deleteAllByUserIdAndDataSource(USER, DataSource.XPENG_IMPORT);
        verify(evTripRepository).deleteAllByUserIdAndDataSource(USER, "XPENG_IMPORT");
        verify(jobRepo).deleteAllByUserId(USER);
    }

    @Test
    void getJobForUserOnlyReturnsOwnJobs() {
        UUID jobId = UUID.randomUUID();
        when(jobRepo.findByIdAndUserId(jobId, USER)).thenReturn(Optional.empty());
        assertTrue(service.getJobForUser(jobId, USER).isEmpty());
        verify(jobRepo).findByIdAndUserId(jobId, USER);
    }

    // --- helpers ---

    private ByteArrayInputStream validZipStream() {
        // ZIP-Magic reicht, um den Magic-Byte-Check zu passieren (die VIN-Pruefung liest weiter,
        // wird aber in diesen Faellen ueber ein echtes ZIP-Fixture abgedeckt).
        try {
            return new ByteArrayInputStream(Files.readAllBytes(writeCsvZip(VIN)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Car ownedBy(UUID owner) {
        return Car.builder()
                .id(CAR).userId(owner)
                .model(CarBrand.CarModel.XPENG_G9).year(2025)
                .status(CarStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    /** Baut ein minimales CSV-ZIP (operation + power_energy Cluster) mit der gegebenen VIN. */
    private Path writeCsvZip(String vin) throws IOException {
        String op = "vin,vmodel,timer,ds,esp_vehspd,ldcu_currentgearlev,cdcu_totalodometer\n"
                + vin + ",F57a,1000,20260901,0.0,4,12345.0\n"
                + vin + ",F57a,1001,20260901,30.0,1,12345.0\n"
                + vin + ",F57a,1002,20260901,0.0,4,12346.0\n";
        String pe = "vin,vmodel,timer,ds,ldcu_chrgpwr,ldcu_bms_soc_disp\n"
                + vin + ",F57a,1000,20260901,0.0,80.0\n"
                + vin + ",F57a,1001,20260901,0.0,79.0\n"
                + vin + ",F57a,1002,20260901,0.0,78.0\n";
        Path zip = Files.createTempFile(tempDir, "xpeng-", ".zip");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("driving_operation_di.csv"));
            zos.write(op.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("driving_power_energy_di.csv"));
            zos.write(pe.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return zip;
    }

    private static XpengImportJob processingJob(Path tempfile) {
        return XpengImportJob.builder()
                .id(UUID.randomUUID()).userId(USER).carId(CAR)
                .format(XpengImportFormat.CSV_ZIP).tempfilePath(tempfile.toString())
                .status(XpengImportJob.Status.PROCESSING).build();
    }
}
