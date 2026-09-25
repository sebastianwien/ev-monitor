package com.evmonitor.application.imports.vweuda;

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
import org.springframework.core.io.InputStreamSource;
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
import com.evmonitor.application.imports.sample.ImportSampleService;
import com.evmonitor.application.ingest.event.ImportEventOutcome;
import com.evmonitor.application.ingest.event.ImportEventRecorder;
import com.evmonitor.infrastructure.persistence.ingest.ImportEvent;
import com.evmonitor.application.imports.sample.ImportSampleService.SampleMeta;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VwEudaImportServiceTest {

    @Mock private CarRepository carRepository;
    @Mock private PublicApiImportService publicApiImportService;
    @Mock private ImportSampleService samples;
    @Mock private ImportEventRecorder importEvents;

    private VwEudaImportService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID carId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new VwEudaImportService(
                new VwEudaJsonParser(new ObjectMapper()),
                carRepository,
                publicApiImportService,
                samples,
                importEvents
        );
    }

    // ── Ownership ─────────────────────────────────────────────────────────────

    @Test
    void importData_carNotFound_throwsIllegalArgument() {
        when(carRepository.findById(carId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                service.importData(userId, carId, this::sampleJsonStream, "export.json"));
    }

    @Test
    void importData_wrongOwner_throwsSecurityException() {
        Car foreignCar = TestDataBuilder.createTestCarWithId(carId, UUID.randomUUID(), CarBrand.CarModel.ID_4);
        when(carRepository.findById(carId)).thenReturn(Optional.of(foreignCar));

        assertThrows(SecurityException.class, () ->
                service.importData(userId, carId, this::sampleJsonStream, "export.json"));
    }

    // ── kWh sanity check ──────────────────────────────────────────────────────

    @Test
    void importData_kwhExceedsBatteryCapacity_isCapped() throws Exception {
        // Use a very small capacity (15 kWh) so that session 2 DC (~46 kWh) triggers the cap
        Car car = carWithCapacity(BigDecimal.valueOf(15.0));
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));
        when(publicApiImportService.importSessions(any(), any(), any(DataSource.class)))
                .thenReturn(ImportApiResult.withoutIds(1, 0, 0));

        service.importData(userId, carId, this::realSampleStream, "export.json");

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
        VwEudaJsonParser mockParser = mock(VwEudaJsonParser.class);
        VwEudaSession sessionWithoutKwh = new VwEudaSession(
                OffsetDateTime.now(), OffsetDateTime.now().plusHours(1),
                60, 50, 80, null, "AC", 11.0, null, 1000, 10.0);
        when(mockParser.parse(any(InputStreamSource.class), anyBoolean())).thenReturn(new VwEudaParseResult("VIN", List.of(sessionWithoutKwh)));

        VwEudaImportService serviceWithMockParser = new VwEudaImportService(
                mockParser, carRepository, publicApiImportService, samples, importEvents);
        ImportApiResult result = serviceWithMockParser.importData(userId, carId, this::sampleJsonStream, "export.json");

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

        service.importData(userId, carId, this::mebZipStream, "export.zip");

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

        ImportApiResult result = service.importData(userId, carId, this::mebZipStream, "export.zip");

        verifyNoInteractions(publicApiImportService);
        assertEquals(0, result.imported());
    }

    // ── DataSource tagging ────────────────────────────────────────────────────

    @Test
    void importData_usesVwEudaDataSource() throws Exception {
        Car car = carWithCapacity(null);
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));
        when(publicApiImportService.importSessions(any(), any(), any(DataSource.class)))
                .thenReturn(ImportApiResult.withoutIds(3, 0, 0));

        service.importData(userId, carId, this::realSampleStream, "export.json");

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

        service.importData(userId, carId, this::realSampleStream, "export.json");

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
        service.importData(userId, carId, () -> new ByteArrayInputStream(zipBytes), "export.zip");

        verify(publicApiImportService).importSessions(any(UUID.class), any(), eq(DataSource.EU_DATA_ACT_IMPORT));
    }

    @Test
    void importData_zipWithNoJson_throwsIllegalArgument() {
        Car car = carWithCapacity(null);
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));

        byte[] zipBytes = wrapInZip("export.txt", "not json".getBytes());
        assertThrows(IllegalArgumentException.class, () ->
                service.importData(userId, carId, () -> new ByteArrayInputStream(zipBytes), "export.zip"));
    }

    @Test
    void importData_zipBomb_throwsIllegalArgumentWithActualLimit() {
        // Der Entpack-Guard muss auch beim gestreamten Lesen greifen - und die Meldung muss
        // das echte Limit nennen (stand frueher faelschlich auf "20 MB").
        Car car = carWithCapacity(null);
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));
        VwEudaImportService smallLimit = new VwEudaImportService(
                new VwEudaJsonParser(new ObjectMapper()), carRepository, publicApiImportService, samples,
                importEvents, 8 * 1024 * 1024L);

        byte[] zipBytes = zipWithOversizedJsonEntry();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                smallLimit.importData(userId, carId, () -> new ByteArrayInputStream(zipBytes), "export.zip"));
        assertTrue(ex.getMessage().contains("8 MB"), "Unerwartete Meldung: " + ex.getMessage());
    }

    // ── Upload-Kopien ─────────────────────────────────────────────────────────

    @Test
    void manualImport_isRecordedAsUploadCopy_withoutTheVin() throws Exception {
        when(samples.isEnabled()).thenReturn(true);
        when(carRepository.findById(carId)).thenReturn(Optional.of(carWithCapacity(new BigDecimal("77"))));
        when(publicApiImportService.importSessions(eq(userId), any(), eq(DataSource.EU_DATA_ACT_IMPORT)))
                .thenReturn(ImportApiResult.withoutIds(3, 0, 0));

        service.importData(userId, carId, this::realSampleStream, "WVWZZZ-ID7_20251213015510.json");

        ArgumentCaptor<SampleMeta> meta = ArgumentCaptor.forClass(SampleMeta.class);
        ArgumentCaptor<ImportSampleService.Anonymizer> copy = ArgumentCaptor.forClass(ImportSampleService.Anonymizer.class);
        verify(samples).record(meta.capture(), copy.capture());
        assertEquals(ImportSampleService.Channel.UPLOAD, meta.getValue().channel());
        assertEquals(ImportSampleService.Outcome.OK, meta.getValue().outcome());
        assertEquals(3, meta.getValue().sessionsDetected());
        byte[] zip = copy.getValue().anonymize().orElseThrow().zip();
        try (var z = new java.util.zip.ZipInputStream(new ByteArrayInputStream(zip))) {
            z.getNextEntry();
            assertFalse(new String(z.readAllBytes(), StandardCharsets.UTF_8).contains("WVWZZZ-ID7"));
        }
    }

    @Test
    void unreadablePreview_isRecorded_andContinuousDropsAreNot() throws Exception {
        when(samples.isEnabled()).thenReturn(true);
        when(carRepository.findById(carId)).thenReturn(Optional.of(carWithCapacity(new BigDecimal("77"))));

        assertThrows(VwEudaUnreadableException.class, () -> service.preview(userId, carId,
                () -> new ByteArrayInputStream("kein json".getBytes(StandardCharsets.UTF_8)), "x.json"));
        service.importData(userId, carId, this::sampleJsonStream, "drop.json", DataSource.EU_DATA_ACT_SYNC, true);

        ArgumentCaptor<SampleMeta> meta = ArgumentCaptor.forClass(SampleMeta.class);
        verify(samples, times(1)).record(meta.capture(), any());
        assertEquals(ImportSampleService.Outcome.UNREADABLE, meta.getValue().outcome());
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

    /**
     * ZIP, dessen JSON-Entry entpackt ueber dem Guard liegt (gepackt bleibt es winzig).
     * Der Fuellstoff steckt in einem Feld, das der Parser ueberspringt - so laeuft der Guard
     * gegen die Bytes und nicht der Test gegen den Heap.
     */
    private byte[] zipWithOversizedJsonEntry() {
        byte[] chunk = new byte[8192];
        for (int i = 0; i < chunk.length; i++) {
            chunk[i] = (i % 2 == 0) ? (byte) '0' : (byte) ',';
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("export.json"));
            zos.write("{\"pad\":[".getBytes(StandardCharsets.UTF_8));
            for (long written = 0; written < 70L * 1024 * 1024; written += chunk.length) {
                zos.write(chunk);
            }
            zos.closeEntry();
            zos.finish();
            return baos.toByteArray();
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

    // ── Telemetrie-Datensatz ohne Ladedaten ───────────────────────────────────

    private java.io.InputStream telemetryOnlyStream() {
        return getClass().getClassLoader().getResourceAsStream("eudataact/telemetry_only_15min_drop.json");
    }

    @Test
    void autoSync_telemetryOnlyDrop_importsNothingWithoutError() throws Exception {
        // Ein stehendes Auto liefert im 15-Minuten-Feed nur Telemetrie. Das darf den AutoSync
        // nicht abbrechen, sonst blockiert derselbe Datensatz jeden weiteren Poll.
        when(carRepository.findById(carId)).thenReturn(Optional.of(carWithCapacity(BigDecimal.valueOf(77.0))));

        ImportApiResult result = service.importData(userId, carId, this::telemetryOnlyStream,
                "20260919122855_TMBTEST0000000001.json", DataSource.EU_DATA_ACT_SYNC, true);

        assertEquals(0, result.imported());
        assertEquals(0, result.errors());
        verifyNoInteractions(publicApiImportService);
    }

    @Test
    void manualUpload_telemetryOnlyDrop_stillThrows() {
        when(carRepository.findById(carId)).thenReturn(Optional.of(carWithCapacity(BigDecimal.valueOf(77.0))));

        assertThrows(IllegalArgumentException.class, () ->
                service.importData(userId, carId, this::telemetryOnlyStream, "export.json"));
    }

    @Test
    void preview_telemetryOnlyDrop_stillThrows() {
        when(carRepository.findById(carId)).thenReturn(Optional.of(carWithCapacity(BigDecimal.valueOf(77.0))));

        assertThrows(IllegalArgumentException.class, () ->
                service.preview(userId, carId, this::telemetryOnlyStream, "export.json"));
    }

    @Test
    void autoSync_brokenJson_throwsUnreadable_notServerError() {
        // Auch im lenient-Modus muss eine kaputte Datei erkennbar scheitern - sonst laeuft der
        // AutoSync endlos gegen sie oder verbucht sie still als "keine Ladevorgaenge".
        when(carRepository.findById(carId)).thenReturn(Optional.of(carWithCapacity(BigDecimal.valueOf(77.0))));

        assertThrows(VwEudaUnreadableException.class, () -> service.importData(userId, carId,
                () -> new ByteArrayInputStream("nicht json".getBytes(StandardCharsets.UTF_8)),
                "kaputt.json", DataSource.EU_DATA_ACT_SYNC, true));
    }

    // ── Import-Protokoll ──────────────────────────────────────────────────────

    @Test
    void unreadablePreview_isLoggedAsParseError_withoutContent() {
        when(carRepository.findById(carId)).thenReturn(Optional.of(carWithCapacity(new BigDecimal("77"))));

        assertThrows(VwEudaUnreadableException.class, () -> service.preview(userId, carId,
                () -> new ByteArrayInputStream("kein json".getBytes(StandardCharsets.UTF_8)), "max-mustermann.json"));

        ArgumentCaptor<ImportEvent> event = ArgumentCaptor.forClass(ImportEvent.class);
        verify(importEvents).record(event.capture());
        assertEquals(ImportEventOutcome.PARSE_ERROR, event.getValue().getOutcome());
        assertEquals("EU_DATA_ACT_IMPORT", event.getValue().getDataSource());
        assertEquals("UPLOAD", event.getValue().getChannel());
        assertEquals(userId, event.getValue().getUserId());
        assertEquals(carId, event.getValue().getCarId());
        assertEquals("VwEudaUnreadableException: Datei ist kein lesbares JSON", event.getValue().getError());
    }

    @Test
    void autoSync_brokenDrop_isLoggedAsParseErrorOnSyncChannel() {
        when(carRepository.findById(carId)).thenReturn(Optional.of(carWithCapacity(BigDecimal.valueOf(77.0))));

        assertThrows(VwEudaUnreadableException.class, () -> service.importData(userId, carId,
                () -> new ByteArrayInputStream("nicht json".getBytes(StandardCharsets.UTF_8)),
                "kaputt.json", DataSource.EU_DATA_ACT_SYNC, true));

        ArgumentCaptor<ImportEvent> event = ArgumentCaptor.forClass(ImportEvent.class);
        verify(importEvents).record(event.capture());
        assertEquals(ImportEventOutcome.PARSE_ERROR, event.getValue().getOutcome());
        assertEquals("SYNC", event.getValue().getChannel());
    }

    @Test
    void readableImport_leavesTheLogToTheGateway() throws Exception {
        when(carRepository.findById(carId)).thenReturn(Optional.of(carWithCapacity(null)));
        when(publicApiImportService.importSessions(any(), any(PublicApiSessionRequest.class), any(DataSource.class)))
                .thenReturn(ImportApiResult.withoutIds(3, 0, 0));

        service.importData(userId, carId, this::realSampleStream, "export.json");

        verifyNoInteractions(importEvents);
    }
}
