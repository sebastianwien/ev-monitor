package com.evmonitor.application.imports.eudataact;

import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.application.publicapi.PublicApiImportService;
import com.evmonitor.application.publicapi.PublicApiSessionRequest;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.DataSource;
import com.evmonitor.testutil.TestDataBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EUDataActImportServiceTest {

    @Mock private CarRepository carRepository;
    @Mock private PublicApiImportService publicApiImportService;

    private EUDataActImportService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID carId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EUDataActImportService(
                new EUDataActJsonParser(new ObjectMapper()),
                carRepository,
                publicApiImportService
        );
    }

    // ── Ownership ─────────────────────────────────────────────────────────────

    @Test
    void importData_carNotFound_throwsIllegalArgument() {
        when(carRepository.findById(carId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                service.importData(userId, carId, sampleJsonStream(), "export.json"));
    }

    @Test
    void importData_wrongOwner_throwsSecurityException() {
        Car foreignCar = TestDataBuilder.createTestCarWithId(carId, UUID.randomUUID(), CarBrand.CarModel.ID_4);
        when(carRepository.findById(carId)).thenReturn(Optional.of(foreignCar));

        assertThrows(SecurityException.class, () ->
                service.importData(userId, carId, sampleJsonStream(), "export.json"));
    }

    // ── kWh sanity check ──────────────────────────────────────────────────────

    @Test
    void importData_kwhExceedsBatteryCapacity_isCapped() throws Exception {
        // Use a very small capacity (15 kWh) so that session 2 DC (~46 kWh) triggers the cap
        Car car = carWithCapacity(BigDecimal.valueOf(15.0));
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));
        when(publicApiImportService.importSessions(any(), any(), any(DataSource.class)))
                .thenReturn(ImportApiResult.withoutIds(1, 0, 0));

        service.importData(userId, carId, realSampleStream(), "export.json");

        ArgumentCaptor<PublicApiSessionRequest> captor = ArgumentCaptor.forClass(PublicApiSessionRequest.class);
        verify(publicApiImportService).importSessions(eq(userId), captor.capture(), eq(DataSource.EU_DATA_ACT_IMPORT));

        double cap = 15.0 * 1.1;
        captor.getValue().sessions().forEach(s ->
                assertTrue(s.kwhAtVehicle() == null || s.kwhAtVehicle() <= cap,
                        "kWh exceeds cap: " + s.kwhAtVehicle()));
    }

    @Test
    void importData_sessionWithNullKwh_isSkipped() throws Exception {
        Car car = carWithCapacity(null);
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));

        // Inject a session that has no kWh (parser returns null)
        EUDataActJsonParser mockParser = mock(EUDataActJsonParser.class);
        EUDataActSession sessionWithoutKwh = new EUDataActSession(
                OffsetDateTime.now(), OffsetDateTime.now().plusHours(1),
                60, 50, 80, null, "AC", 11.0, null, 1000, 10.0);
        when(mockParser.parse(any())).thenReturn(new EUDataActParseResult("VIN", List.of(sessionWithoutKwh)));

        EUDataActImportService serviceWithMockParser = new EUDataActImportService(
                mockParser, carRepository, publicApiImportService);
        ImportApiResult result = serviceWithMockParser.importData(userId, carId, sampleJsonStream(), "export.json");

        // Service returns early without calling importSessions when all sessions have no kWh
        verifyNoInteractions(publicApiImportService);
        assertEquals(0, result.imported());
    }

    /** Echter (anonymisierter) ID.3-Export - der Service entpackt das ZIP selbst. */
    private java.io.InputStream mebZipStream() {
        return getClass().getClassLoader().getResourceAsStream("eudataact/MEB_signal_ids.zip");
    }

    // ── MEB-Variante: kWh aus SoC-Delta ───────────────────────────────────────

    @Test
    void importData_mebFormat_calculatesKwhFromSocDeltaAndCapacity() throws Exception {
        // Echter ID.3-Export (Signal-IDs, kein Leistungssignal). Die Datei enthaelt 9 Ladungen;
        // erste: SoC 43,0 -> 91,5 = 48,5 % von 58 kWh = 28,1 kWh.
        Car car = carWithCapacity(new BigDecimal("58.0"));
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));
        when(publicApiImportService.importSessions(any(), any(), any(DataSource.class)))
                .thenReturn(ImportApiResult.withoutIds(9, 0, 0));

        service.importData(userId, carId, mebZipStream(), "export.zip");

        ArgumentCaptor<PublicApiSessionRequest> captor = ArgumentCaptor.forClass(PublicApiSessionRequest.class);
        verify(publicApiImportService).importSessions(eq(userId), captor.capture(), any(DataSource.class));

        List<PublicApiSessionRequest.SessionEntry> sessions = captor.getValue().sessions();
        assertEquals(9, sessions.size());
        assertEquals(28.1, sessions.get(0).kwhAtVehicle(), 0.1);

        double total = sessions.stream().mapToDouble(PublicApiSessionRequest.SessionEntry::kwhAtVehicle).sum();
        assertEquals(170.5, total, 1.0);
    }

    @Test
    void importData_mebFormat_withoutBatteryCapacity_skipsSessions() throws Exception {
        // Ohne Kapazitaet ist der SoC-Delta nicht in kWh umrechenbar - lieber nichts importieren.
        Car car = carWithCapacity(null);
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));

        ImportApiResult result = service.importData(userId, carId, mebZipStream(), "export.zip");

        verifyNoInteractions(publicApiImportService);
        assertEquals(0, result.imported());
    }

    // ── DataSource tagging ────────────────────────────────────────────────────

    @Test
    void importData_usesEuDataActDataSource() throws Exception {
        Car car = carWithCapacity(null);
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));
        when(publicApiImportService.importSessions(any(), any(), any(DataSource.class)))
                .thenReturn(ImportApiResult.withoutIds(3, 0, 0));

        service.importData(userId, carId, realSampleStream(), "export.json");

        verify(publicApiImportService).importSessions(
                eq(userId), any(), eq(DataSource.EU_DATA_ACT_IMPORT));
    }

    // ── Session fields ────────────────────────────────────────────────────────

    @Test
    void importData_mapsSessionFieldsCorrectly() throws Exception {
        Car car = carWithCapacity(null);
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));
        when(publicApiImportService.importSessions(any(), any(), any(DataSource.class)))
                .thenReturn(ImportApiResult.withoutIds(3, 0, 0));

        service.importData(userId, carId, realSampleStream(), "export.json");

        ArgumentCaptor<PublicApiSessionRequest> captor = ArgumentCaptor.forClass(PublicApiSessionRequest.class);
        verify(publicApiImportService).importSessions(eq(userId), captor.capture(), any(DataSource.class));

        List<PublicApiSessionRequest.SessionEntry> sessions = captor.getValue().sessions();
        assertEquals(3, sessions.size());

        // Session 1: AC, SoC 74→100, ~115 min
        PublicApiSessionRequest.SessionEntry s1 = sessions.get(0);
        assertEquals("AC", s1.chargingType());
        assertEquals(new BigDecimal("74"), s1.socBefore());
        assertEquals(new BigDecimal("100"), s1.socAfter());
        assertTrue(s1.durationMin() >= 110 && s1.durationMin() <= 120);
        assertNotNull(s1.kwhAtVehicle());
        assertEquals("AT_VEHICLE", s1.measurementType());
        assertNotNull(s1.odometerKm());

        // Session 2: DC fast charge
        PublicApiSessionRequest.SessionEntry s2 = sessions.get(1);
        assertEquals("DC", s2.chargingType());
        assertNotNull(s2.maxChargingPowerKw());
        assertTrue(s2.maxChargingPowerKw() > 100.0);
    }

    // ── ZIP upload ────────────────────────────────────────────────────────────

    @Test
    void importData_acceptsZipContainingJson() throws Exception {
        Car car = carWithCapacity(null);
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));
        when(publicApiImportService.importSessions(any(), any(), any(DataSource.class)))
                .thenReturn(ImportApiResult.withoutIds(3, 0, 0));

        byte[] zipBytes = wrapInZip("export.json", readSampleJson());
        service.importData(userId, carId, new ByteArrayInputStream(zipBytes), "export.zip");

        verify(publicApiImportService).importSessions(any(UUID.class), any(), eq(DataSource.EU_DATA_ACT_IMPORT));
    }

    @Test
    void importData_zipWithNoJson_throwsIllegalArgument() {
        Car car = carWithCapacity(null);
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));

        byte[] zipBytes = wrapInZip("export.txt", "not json".getBytes());
        assertThrows(IllegalArgumentException.class, () ->
                service.importData(userId, carId, new ByteArrayInputStream(zipBytes), "export.zip"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Car carWithCapacity(BigDecimal capacityKwh) {
        // createTestCarWithId sets customNetCapacityKwh=75.0 by default; override here
        return TestDataBuilder.createTestCarWithId(carId, userId, CarBrand.CarModel.ID_4)
                .toBuilder().customNetCapacityKwh(capacityKwh).build();
    }

    private ByteArrayInputStream sampleJsonStream() {
        return new ByteArrayInputStream("{\"vin\":\"TEST\",\"Data\":[]}".getBytes(StandardCharsets.UTF_8));
    }

    private ByteArrayInputStream realSampleStream() {
        try (var in = getClass().getClassLoader().getResourceAsStream(
                "eudataact/WVWZZZ-ID7_20251213015510.json")) {
            assertNotNull(in, "Sample JSON not found in test resources");
            return new ByteArrayInputStream(in.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] readSampleJson() {
        try (var in = getClass().getClassLoader().getResourceAsStream(
                "eudataact/WVWZZZ-ID7_20251213015510.json")) {
            assertNotNull(in);
            return in.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] wrapInZip(String entryName, byte[] content) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content);
            zos.closeEntry();
            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
