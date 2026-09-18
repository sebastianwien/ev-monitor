package com.evmonitor.infrastructure.web;

import com.evmonitor.application.imports.eudataact.EUDataActImportService;
import com.evmonitor.application.imports.eudataact.EudaNotificationService;
import com.evmonitor.application.publicapi.ImportApiResult;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    private final UUID userId = UUID.randomUUID();
    private final UUID carId = UUID.randomUUID();

    private MockMultipartFile zip() {
        return new MockMultipartFile("file", "20260918093000_VIN.zip", "application/zip", "PKzip".getBytes());
    }

    @Test
    void import_forwardsToImportService_andReturnsCounts() throws Exception {
        when(importService.importData(eq(userId), eq(carId), any(InputStreamSource.class), eq("20260918093000_VIN.zip")))
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
        verify(importService, never()).importData(any(), any(), any(InputStreamSource.class), any());
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
}
