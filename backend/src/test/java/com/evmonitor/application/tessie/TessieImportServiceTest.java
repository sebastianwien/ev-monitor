package com.evmonitor.application.tessie;

import com.evmonitor.infrastructure.external.TessieClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TessieImportServiceTest {

    @Mock private TessieClient client;
    @Mock private TessieRawImportJdbcWriter writer;

    private TessieImportService service;
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID userId = UUID.randomUUID();
    private final String token = "test-token";
    private final String vin = "5YJ3E7EAXKF000001";

    @BeforeEach
    void setUp() {
        service = new TessieImportService(client, writer);
    }

    @Test
    void importForVin_countsImportedDrivesAndCharges() throws Exception {
        JsonNode drive = mapper.readTree("{\"id\":1,\"started_at\":1628960959,\"energy_used\":30.5}");
        JsonNode charge = mapper.readTree("{\"id\":2,\"started_at\":1628906796,\"energy_added\":20.0}");

        when(client.getDrives(token, vin)).thenReturn(List.of(drive));
        when(client.getCharges(token, vin)).thenReturn(List.of(charge));
        when(writer.batchInsertIfNew(anyList())).thenReturn(new int[]{1});

        TessieImportResult result = service.importForVin(userId, token, vin);

        assertEquals(1, result.drivesImported());
        assertEquals(1, result.chargesImported());
        assertEquals(0, result.skipped());
    }

    @Test
    void importForVin_countsSkippedDuplicates() throws Exception {
        JsonNode drive = mapper.readTree("{\"id\":1,\"started_at\":1628960959}");

        when(client.getDrives(token, vin)).thenReturn(List.of(drive));
        when(client.getCharges(token, vin)).thenReturn(List.of());
        when(writer.batchInsertIfNew(anyList())).thenReturn(new int[]{0});

        TessieImportResult result = service.importForVin(userId, token, vin);

        assertEquals(0, result.drivesImported());
        assertEquals(0, result.chargesImported());
        assertEquals(1, result.skipped());
    }

    @Test
    void importForVin_skipsEntriesWithoutId() throws Exception {
        JsonNode driveNoId = mapper.readTree("{\"started_at\":1628960959,\"energy_used\":30.5}");

        when(client.getDrives(token, vin)).thenReturn(List.of(driveNoId));
        when(client.getCharges(token, vin)).thenReturn(List.of());
        when(writer.batchInsertIfNew(anyList())).thenReturn(new int[]{});

        TessieImportResult result = service.importForVin(userId, token, vin);

        assertEquals(0, result.drivesImported());
        assertEquals(0, result.skipped());
    }

    @Test
    @SuppressWarnings("unchecked")
    void importForVin_savesCorrectEntityFields() throws Exception {
        JsonNode drive = mapper.readTree("{\"id\":42,\"started_at\":1628960959,\"energy_used\":73.28}");

        when(client.getDrives(token, vin)).thenReturn(List.of(drive));
        when(client.getCharges(token, vin)).thenReturn(List.of());
        when(writer.batchInsertIfNew(anyList())).thenReturn(new int[]{1});

        service.importForVin(userId, token, vin);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(writer, times(2)).batchInsertIfNew(captor.capture());

        var drivesBatch = (List<com.evmonitor.domain.TessieRawImport>) captor.getAllValues().get(0);
        assertEquals(1, drivesBatch.size());
        var saved = drivesBatch.get(0);
        assertEquals(userId, saved.getUserId());
        assertEquals(vin, saved.getVin());
        assertEquals("drive", saved.getType());
        assertEquals(42L, saved.getTessieId());
        assertFalse(saved.isProcessed());
        assertTrue(saved.getRaw().contains("73.28"));
    }

    @Test
    void fetchVehicles_delegatesToClient() {
        List<TessieVehicleDTO> expected = List.of(new TessieVehicleDTO(vin, "Mein Tesla", true));
        when(client.getVehicles(token)).thenReturn(expected);

        List<TessieVehicleDTO> result = service.fetchVehicles(token);

        assertSame(expected, result);
    }
}
