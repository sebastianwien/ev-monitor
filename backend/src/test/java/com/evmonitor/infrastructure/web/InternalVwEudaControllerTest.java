package com.evmonitor.infrastructure.web;

import com.evmonitor.application.imports.vweuda.VwEudaImportService;
import com.evmonitor.application.imports.vweuda.VwEudaAutoSyncEntitlementService;
import com.evmonitor.application.imports.vweuda.VwEudaNotificationService;
import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.domain.DataSource;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamSource;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Der Connectors-Service liefert Portal-Datensaetze und Ereignisse ueber /api/internal.
 * Beides ist nur mit dem internen Token erreichbar.
 */
class InternalVwEudaControllerTest extends AbstractIntegrationTest {

    private static final String TOKEN_HEADER = "X-Internal-Token";
    private static final String VALID_TOKEN = "test-internal-token";

    private final UUID userId = UUID.randomUUID();
    private final UUID carId = UUID.randomUUID();

    private MockMultipartFile zip() {
        return new MockMultipartFile("file", "20260918093000_VIN.zip", "application/zip", "PKzip".getBytes());
    }

    @Test
    void import_forwardsToImportService_andReturnsCounts() throws Exception {
        doReturn(ImportApiResult.withoutIds(2, 1, 0)).when(vwEudaImportService).importData(eq(userId), eq(carId), any(InputStreamSource.class), eq("20260918093000_VIN.zip"), eq(DataSource.EU_DATA_ACT_SYNC), eq(true));

        mockMvc.perform(multipart("/api/internal/eu-data-act/import")
                        .file(zip())
                        .part(new org.springframework.mock.web.MockPart("userId", userId.toString().getBytes()))
                        .part(new org.springframework.mock.web.MockPart("carId", carId.toString().getBytes()))
                        .header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void import_withoutInternalToken_isRejected() throws Exception {
        mockMvc.perform(multipart("/api/internal/eu-data-act/import").file(zip())
                        .part(new org.springframework.mock.web.MockPart("userId", userId.toString().getBytes()))
                        .part(new org.springframework.mock.web.MockPart("carId", carId.toString().getBytes())))
                .andExpect(status().isForbidden());
        verify(vwEudaImportService, never()).importData(any(), any(), any(InputStreamSource.class), any(), any());
    }

    @Test
    void entitlement_returnsCoreDecision() throws Exception {
        com.evmonitor.domain.User user = userRepository.save(com.evmonitor.testutil.TestDataBuilder.createTestUserWithId(
                userId, "vweuda-ent-" + System.nanoTime() + "@example.com", "x"));
        doReturn(new VwEudaAutoSyncEntitlementService.Entitlement(true, true, java.time.LocalDate.of(2026, 10, 21)))
                .when(vwEudaAutoSyncEntitlementService).entitlementFor(any());

        mockMvc.perform(get("/api/internal/eu-data-act/entitlement/" + userId).header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entitled").value(true))
                .andExpect(jsonPath("$.viaTrial").value(true))
                .andExpect(jsonPath("$.trialEndsAt").value("2026-10-21"));
    }

    @Test
    void entitlement_unknownUser_is404_andWithoutTokenForbidden() throws Exception {
        mockMvc.perform(get("/api/internal/eu-data-act/entitlement/" + userId).header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/internal/eu-data-act/entitlement/" + userId))
                .andExpect(status().isForbidden());
    }

    @Test
    void notify_forwardsEventAndParams() throws Exception {
        mockMvc.perform(post("/api/internal/eu-data-act/notify")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType("application/json")
                        .content("{\"userId\":\"" + userId + "\",\"carId\":\"" + carId + "\","
                                + "\"event\":\"HISTORY_IMPORTED\",\"params\":{\"imported\":197,\"skipped\":6}}"))
                .andExpect(status().isNoContent());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(vwEudaNotificationService).notify(eq(userId), eq("HISTORY_IMPORTED"), params.capture());
        assertEquals(197, params.getValue().get("imported"));
    }

    @Test
    void notify_withoutInternalToken_isRejected() throws Exception {
        mockMvc.perform(post("/api/internal/eu-data-act/notify").contentType("application/json")
                        .content("{\"userId\":\"" + userId + "\",\"event\":\"X\"}"))
                .andExpect(status().isForbidden());
        verify(vwEudaNotificationService, never()).notify(any(), any(), any());
    }

    // ── Abgrenzung: welcher Fehler darf den Connector den Datensatz verwerfen lassen ──

    @Test
    void import_unreadableFile_returns422() throws Exception {
        // 422 = "verstanden, aber inhaltlich nicht verarbeitbar". Nur darauf hin darf der
        // Connector den Datensatz dauerhaft ueberspringen.
        doThrow(new com.evmonitor.application.imports.vweuda.VwEudaUnreadableException(
                        "Format wird nicht unterstuetzt")).when(vwEudaImportService).importData(any(), any(), any(InputStreamSource.class), anyString(), any(), anyBoolean());

        mockMvc.perform(multipart("/api/internal/eu-data-act/import")
                        .file(zip())
                        .part(new org.springframework.mock.web.MockPart("userId", userId.toString().getBytes()))
                        .part(new org.springframework.mock.web.MockPart("carId", carId.toString().getBytes()))
                        .header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void import_unknownCar_returns400_notSkippable() throws Exception {
        // Ein geloeschtes Fahrzeug ist ein Konfigurationsfehler, kein Datenproblem. Der
        // Datensatz darf deshalb NICHT still verworfen werden - 400 bleibt 400.
        doThrow(new IllegalArgumentException("Fahrzeug nicht gefunden")).when(vwEudaImportService).importData(any(), any(), any(InputStreamSource.class), anyString(), any(), anyBoolean());

        mockMvc.perform(multipart("/api/internal/eu-data-act/import")
                        .file(zip())
                        .part(new org.springframework.mock.web.MockPart("userId", userId.toString().getBytes()))
                        .part(new org.springframework.mock.web.MockPart("carId", carId.toString().getBytes()))
                        .header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_continuousDrop_isParsedLeniently() throws Exception {
        doReturn(ImportApiResult.withoutIds(0, 0, 0)).when(vwEudaImportService).importData(any(), any(), any(InputStreamSource.class), anyString(), any(), anyBoolean());

        mockMvc.perform(multipart("/api/internal/eu-data-act/import")
                        .file(zip())
                        .part(new org.springframework.mock.web.MockPart("userId", userId.toString().getBytes()))
                        .part(new org.springframework.mock.web.MockPart("carId", carId.toString().getBytes()))
                        .header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isOk());

        verify(vwEudaImportService).importData(eq(userId), eq(carId), any(InputStreamSource.class), anyString(),
                eq(DataSource.EU_DATA_ACT_SYNC), eq(true));
    }

    @Test
    void import_historyExport_isParsedStrictly() throws Exception {
        // Die Historie kommt genau einmal. Wuerde ein unbekanntes Format hier still als
        // "0 Ladevorgaenge" durchgehen, gaelte sie als importiert und waere fuer immer weg.
        doReturn(ImportApiResult.withoutIds(42, 0, 0)).when(vwEudaImportService).importData(any(), any(), any(InputStreamSource.class), anyString(), any(), anyBoolean());

        mockMvc.perform(multipart("/api/internal/eu-data-act/import")
                        .file(zip())
                        .part(new org.springframework.mock.web.MockPart("userId", userId.toString().getBytes()))
                        .part(new org.springframework.mock.web.MockPart("carId", carId.toString().getBytes()))
                        .part(new org.springframework.mock.web.MockPart("kind", "HISTORY".getBytes()))
                        .header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isOk());

        verify(vwEudaImportService).importData(eq(userId), eq(carId), any(InputStreamSource.class), anyString(),
                eq(DataSource.EU_DATA_ACT_SYNC), eq(false));
    }
}
