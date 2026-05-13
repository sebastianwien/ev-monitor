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
import com.evmonitor.infrastructure.persistence.xpeng.XpengConnection;
import com.evmonitor.infrastructure.persistence.xpeng.XpengConnectionRepository;
import com.evmonitor.infrastructure.persistence.xpeng.XpengImportJob;
import com.evmonitor.infrastructure.persistence.xpeng.XpengImportJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
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

@ExtendWith(MockitoExtension.class)
class XpengImportServiceTest {

    @Mock CarRepository carRepository;
    @Mock XpengConnectionRepository connectionRepo;
    @Mock XpengImportJobRepository jobRepo;
    @Mock TripService tripService;
    @Mock PublicApiImportService publicApiImportService;
    @Mock XpengChargeMatcher chargeMatcher;
    @Mock ApplicationContext applicationContext;
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
        // self-injection returns the same instance under test; @Async/@Transactional are no-ops in unit tests
        lenient().when(applicationContext.getBean(XpengImportService.class)).thenReturn(service);
    }

    @Test
    void rejectsUploadForCarOfOtherUser() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(OTHER_USER)));
        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.uploadXlsx(USER, CAR, validXlsxStream(), null, "1.1.1.1", "ua"));
        assertTrue(ex.getMessage().contains("gehört"));
        verify(jobRepo, never()).save(any());
    }

    @Test
    void rejectsUploadWithoutActiveConnection() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        when(connectionRepo.findByCarId(CAR)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> service.uploadXlsx(USER, CAR, validXlsxStream(), null, "1.1.1.1", "ua"));
        verify(jobRepo, never()).save(any());
    }

    @Test
    void rejectsUploadWithRevokedConnection() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        when(connectionRepo.findByCarId(CAR)).thenReturn(Optional.of(revokedConnection()));
        assertThrows(IllegalStateException.class,
                () -> service.uploadXlsx(USER, CAR, validXlsxStream(), null, "1.1.1.1", "ua"));
    }

    @Test
    void rejectsFileWithoutValidMagicBytes() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        when(connectionRepo.findByCarId(CAR)).thenReturn(Optional.of(activeConnection()));
        // Not a ZIP/xlsx - just text bytes
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.uploadXlsx(USER, CAR,
                        new ByteArrayInputStream("not an xlsx file".getBytes()),
                        null, "1.1.1.1", "ua"));
        assertTrue(ex.getMessage().toLowerCase().contains("magic"),
                "Expected magic-bytes failure, got: " + ex.getMessage());
    }

    @Test
    void acceptsEncryptedXlsxWithOleCfbMagicBytes() {
        // Password-protected xlsx uses OLE Compound File Format (D0 CF 11 E0 A1 B1 1A E1)
        // instead of plain ZIP magic. Upload validation must NOT reject it - decryption happens
        // later in the parser.
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        when(connectionRepo.findByCarId(CAR)).thenReturn(Optional.of(activeConnection()));
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        byte[] oleMagic = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0};
        assertDoesNotThrow(() -> service.uploadXlsx(USER, CAR,
                new ByteArrayInputStream(oleMagic), "pw", "1.1.1.1", "ua"));
    }

    @Test
    void rejectsTinyFile() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        when(connectionRepo.findByCarId(CAR)).thenReturn(Optional.of(activeConnection()));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.uploadXlsx(USER, CAR,
                        new ByteArrayInputStream(new byte[]{1, 2}),
                        null, "1.1.1.1", "ua"));
        assertTrue(ex.getMessage().contains("klein"));
    }

    @Test
    void allowsReuploadAfterPreviousAttemptFailed() {
        // Same file hash, previous job FAILED (e.g. wrong VIN) - user must be able to retry.
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        when(connectionRepo.findByCarId(CAR)).thenReturn(Optional.of(activeConnection()));
        when(jobRepo.findFirstByUserIdAndFileHashAndStatusIn(eq(USER), anyString(),
                anyCollection())).thenReturn(Optional.empty());
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertDoesNotThrow(() -> service.uploadXlsx(USER, CAR, validXlsxStream(),
                null, "1.1.1.1", "ua"));
        verify(jobRepo).save(any());
    }

    @Test
    void blocksReuploadWhilePreviousJobStillRunning() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        when(connectionRepo.findByCarId(CAR)).thenReturn(Optional.of(activeConnection()));
        XpengImportJob inFlight = XpengImportJob.builder()
                .id(UUID.randomUUID()).userId(USER).carId(CAR)
                .status(XpengImportJob.Status.PROCESSING).build();
        when(jobRepo.findFirstByUserIdAndFileHashAndStatusIn(eq(USER), anyString(),
                anyCollection())).thenReturn(Optional.of(inFlight));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.uploadXlsx(USER, CAR, validXlsxStream(),
                        null, "1.1.1.1", "ua"));
        assertTrue(ex.getMessage().contains("bereits"));
        verify(jobRepo, never()).save(any());
    }

    @Test
    void deleteAllImportedData_clearsLogsTripsAndJobs() {
        when(evLogRepository.countByUserIdAndDataSource(USER, DataSource.XPENG_IMPORT)).thenReturn(42);
        when(evTripRepository.softDeleteByUserIdAndDataSource(USER, "XPENG_IMPORT")).thenReturn(70);
        when(jobRepo.deleteAllByUserId(USER)).thenReturn(4L);

        XpengImportService.DeleteSummary s = service.deleteAllImportedData(USER);

        assertEquals(42, s.chargingLogs());
        assertEquals(70, s.trips());
        assertEquals(4L, s.importJobs());
        verify(evLogRepository).deleteAllByUserIdAndDataSource(USER, DataSource.XPENG_IMPORT);
        verify(evTripRepository).softDeleteByUserIdAndDataSource(USER, "XPENG_IMPORT");
        verify(jobRepo).deleteAllByUserId(USER);
    }

    @Test
    void getJobForUserOnlyReturnsOwnJobs() {
        UUID jobId = UUID.randomUUID();
        when(jobRepo.findByIdAndUserId(jobId, USER)).thenReturn(Optional.empty());
        assertTrue(service.getJobForUser(jobId, USER).isEmpty());
        verify(jobRepo).findByIdAndUserId(jobId, USER);
    }

    private ByteArrayInputStream validXlsxStream() {
        // ZIP magic bytes - enough to pass the magic-bytes check
        // (parsing would fail later, but uploadXlsx never reaches parsing in these tests)
        byte[] zipMagic = {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0};
        return new ByteArrayInputStream(zipMagic);
    }

    private Car ownedBy(UUID owner) {
        return Car.builder()
                .id(CAR).userId(owner)
                .model(CarBrand.CarModel.XPENG_G9).year(2025)
                .status(CarStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    private XpengConnection activeConnection() {
        return XpengConnection.builder()
                .id(UUID.randomUUID()).userId(USER).carId(CAR).vin(VIN)
                .consentGrantedAt(LocalDateTime.now())
                .consentVersion(XpengConnection.CURRENT_CONSENT_VERSION)
                .build();
    }

    private XpengConnection revokedConnection() {
        XpengConnection c = activeConnection();
        c.setConsentRevokedAt(LocalDateTime.now());
        return c;
    }
}
