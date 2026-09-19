package com.evmonitor.infrastructure.web;

import com.evmonitor.application.imports.eudataact.EUDataActImportService;
import com.evmonitor.application.imports.eudataact.EudaAutoSyncEntitlementService;
import com.evmonitor.application.imports.eudataact.EudaNotificationService;
import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.domain.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalEuDataActControllerTest {

    private static final String TOKEN_HEADER = "X-Internal-Token";
    private static final String VALID_TOKEN = "test-internal-token";

    @Autowired MockMvc mockMvc;
    @MockitoBean EUDataActImportService importService;
    @MockitoBean EudaNotificationService notifications;
    @MockitoBean EudaAutoSyncEntitlementService entitlement;
    @MockitoBean com.evmonitor.domain.UserRepository userRepository;

    private final UUID userId = UUID.randomUUID();
    private final UUID carId = UUID.randomUUID();

    private MockMultipartFile zip() {
        return new MockMultipartFile("file", "20260918093000_VIN.zip", "application/zip", "PKzip".getBytes());
    }

    @Test
    void import_forwardsToImportService_andReturnsCounts() throws Exception {
        when(importService.importData(eq(userId), eq(carId), any(InputStreamSource.class), eq("20260918093000_VIN.zip"), eq(DataSource.EU_DATA_ACT_SYNC), eq(true)))
                .thenReturn(ImportApiResult.withoutIds(2, 1, 0));

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
        verify(importService, never()).importData(any(), any(), any(InputStreamSource.class), any(), any());
    }

    @Test
    void entitlement_returnsCoreDecision() throws Exception {
        com.evmonitor.domain.User user = com.evmonitor.testutil.TestDataBuilder.createTestUserWithId(userId, "max@example.com", "x");
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(entitlement.entitlementFor(user)).thenReturn(
                new EudaAutoSyncEntitlementService.Entitlement(true, true, java.time.LocalDate.of(2026, 10, 21)));

        mockMvc.perform(get("/api/internal/eu-data-act/entitlement/" + userId).header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entitled").value(true))
                .andExpect(jsonPath("$.viaTrial").value(true))
                .andExpect(jsonPath("$.trialEndsAt").value("2026-10-21"));
    }

    @Test
    void entitlement_unknownUser_is404_andWithoutTokenForbidden() throws Exception {
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.empty());
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
        verify(notifications).notify(eq(userId), eq("HISTORY_IMPORTED"), params.capture());
        assertEquals(197, params.getValue().get("imported"));
    }

    @Test
    void notify_withoutInternalToken_isRejected() throws Exception {
        mockMvc.perform(post("/api/internal/eu-data-act/notify").contentType("application/json")
                        .content("{\"userId\":\"" + userId + "\",\"event\":\"X\"}"))
                .andExpect(status().isForbidden());
        verify(notifications, never()).notify(any(), any(), any());
    }

    // ── Abgrenzung: welcher Fehler darf den Connector den Datensatz verwerfen lassen ──

    @Test
    void import_unreadableFile_returns422() throws Exception {
        // 422 = "verstanden, aber inhaltlich nicht verarbeitbar". Nur darauf hin darf der
        // Connector den Datensatz dauerhaft ueberspringen.
        when(importService.importData(any(), any(), any(InputStreamSource.class), anyString(), any(), anyBoolean()))
                .thenThrow(new com.evmonitor.application.imports.eudataact.EUDataActUnreadableException(
                        "Format wird nicht unterstuetzt"));

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
        when(importService.importData(any(), any(), any(InputStreamSource.class), anyString(), any(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Fahrzeug nicht gefunden"));

        mockMvc.perform(multipart("/api/internal/eu-data-act/import")
                        .file(zip())
                        .part(new org.springframework.mock.web.MockPart("userId", userId.toString().getBytes()))
                        .part(new org.springframework.mock.web.MockPart("carId", carId.toString().getBytes()))
                        .header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_continuousDrop_isParsedLeniently() throws Exception {
        when(importService.importData(any(), any(), any(InputStreamSource.class), anyString(), any(), anyBoolean()))
                .thenReturn(ImportApiResult.withoutIds(0, 0, 0));

        mockMvc.perform(multipart("/api/internal/eu-data-act/import")
                        .file(zip())
                        .part(new org.springframework.mock.web.MockPart("userId", userId.toString().getBytes()))
                        .part(new org.springframework.mock.web.MockPart("carId", carId.toString().getBytes()))
                        .header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isOk());

        verify(importService).importData(eq(userId), eq(carId), any(InputStreamSource.class), anyString(),
                eq(DataSource.EU_DATA_ACT_SYNC), eq(true));
    }

    @Test
    void import_historyExport_isParsedStrictly() throws Exception {
        // Die Historie kommt genau einmal. Wuerde ein unbekanntes Format hier still als
        // "0 Ladevorgaenge" durchgehen, gaelte sie als importiert und waere fuer immer weg.
        when(importService.importData(any(), any(), any(InputStreamSource.class), anyString(), any(), anyBoolean()))
                .thenReturn(ImportApiResult.withoutIds(42, 0, 0));

        mockMvc.perform(multipart("/api/internal/eu-data-act/import")
                        .file(zip())
                        .part(new org.springframework.mock.web.MockPart("userId", userId.toString().getBytes()))
                        .part(new org.springframework.mock.web.MockPart("carId", carId.toString().getBytes()))
                        .part(new org.springframework.mock.web.MockPart("kind", "HISTORY".getBytes()))
                        .header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isOk());

        verify(importService).importData(eq(userId), eq(carId), any(InputStreamSource.class), anyString(),
                eq(DataSource.EU_DATA_ACT_SYNC), eq(false));
    }
}
