package com.evmonitor.infrastructure.web;

import com.evmonitor.application.imports.eudataact.EUDataActImportService;
import com.evmonitor.application.imports.eudataact.EUDataActPreviewResult;
import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.security.UserPrincipal;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EUDataActImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EUDataActImportService importService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CAR_ID = UUID.randomUUID();

    private Authentication auth() {
        User user = TestDataBuilder.createTestUserWithId(USER_ID, "eu-data-act@ev-monitor.net", "hash");
        UserPrincipal principal = UserPrincipal.create(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private MockMultipartFile validJsonFile() {
        return new MockMultipartFile("file", "export.json", "application/json",
                "{\"vin\":\"WVWZZZ-ID7\",\"Data\":[]}".getBytes());
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    @Test
    void preview_unauthenticated_returns401() throws Exception {
        mockMvc.perform(multipart("/api/import/eu-data-act/preview").file(validJsonFile()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void import_unauthenticated_returns401() throws Exception {
        mockMvc.perform(multipart("/api/import/eu-data-act/import")
                        .file(validJsonFile())
                        .param("carId", CAR_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    // ── File validation ───────────────────────────────────────────────────────

    @Test
    void preview_wrongFileType_returns400() throws Exception {
        MockMultipartFile xlsx = new MockMultipartFile("file", "export.xlsx",
                "application/octet-stream", new byte[100]);

        mockMvc.perform(multipart("/api/import/eu-data-act/preview")
                        .file(xlsx)
                        .param("carId", CAR_ID.toString())
                        .with(authentication(auth())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preview_unzippedExport_isAccepted() throws Exception {
        // Regression: entpackte VW-Exporte sind zweistellig MB gross (real gesehen: 24 und 46 MB).
        // Solange wir .json akzeptieren, darf so ein Upload nicht am Groessenlimit scheitern.
        when(importService.preview(any(), any(), any(), any()))
                .thenReturn(new EUDataActPreviewResult("WVWZZZ-ID7", List.of()));

        MockMultipartFile file = new MockMultipartFile("file", "export.json",
                "application/json", new byte[6 * 1024 * 1024]);

        mockMvc.perform(multipart("/api/import/eu-data-act/preview")
                        .file(file)
                        .param("carId", CAR_ID.toString())
                        .with(authentication(auth())))
                .andExpect(status().isOk());
    }

    @Test
    void preview_fileAboveLimit_returns400() throws Exception {
        mockMvc.perform(multipart("/api/import/eu-data-act/preview")
                        .file(oversizedJsonFile())
                        .param("carId", CAR_ID.toString())
                        .with(authentication(auth())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("64 MB")));
    }

    /** Meldet eine Groesse jenseits des Limits, ohne sie tatsaechlich zu allozieren. */
    private MockMultipartFile oversizedJsonFile() {
        return new MockMultipartFile("file", "export.json", "application/json", new byte[]{'{'}) {
            @Override
            public long getSize() {
                return 70L * 1024 * 1024;
            }
        };
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    @Test
    void preview_returnsSessionsFromService() throws Exception {
        var session = new EUDataActPreviewResult.SessionPreview(
                java.time.OffsetDateTime.parse("2025-11-15T13:23:02Z"),
                java.time.OffsetDateTime.parse("2025-11-15T15:18:15Z"),
                115, 74, 100, "AC", 10.0, 17.5, 1951, 7.0);
        when(importService.preview(any(), any(), any(), any()))
                .thenReturn(new EUDataActPreviewResult("WVWZZZ-ID7", List.of(session)));

        mockMvc.perform(multipart("/api/import/eu-data-act/preview")
                        .file(validJsonFile())
                        .param("carId", CAR_ID.toString())
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vin").value("WVWZZZ-ID7"))
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].chargeType").value("AC"))
                .andExpect(jsonPath("$.sessions[0].calculatedKwh").value(17.5));
    }

    @Test
    void preview_serviceThrowsIllegalArgument_returns400() throws Exception {
        when(importService.preview(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Keine Ladevorgänge gefunden"));

        mockMvc.perform(multipart("/api/import/eu-data-act/preview")
                        .file(validJsonFile())
                        .param("carId", CAR_ID.toString())
                        .with(authentication(auth())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ── Import ────────────────────────────────────────────────────────────────

    @Test
    void import_returnsResultFromService() throws Exception {
        when(importService.importData(eq(USER_ID), eq(CAR_ID), any(), any()))
                .thenReturn(ImportApiResult.withoutIds(3, 1, 0));

        mockMvc.perform(multipart("/api/import/eu-data-act/import")
                        .file(validJsonFile())
                        .param("carId", CAR_ID.toString())
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(3))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void import_ownershipViolation_returns403() throws Exception {
        when(importService.importData(eq(USER_ID), eq(CAR_ID), any(), any()))
                .thenThrow(new SecurityException("Dieses Fahrzeug gehört dir nicht"));

        mockMvc.perform(multipart("/api/import/eu-data-act/import")
                        .file(validJsonFile())
                        .param("carId", CAR_ID.toString())
                        .with(authentication(auth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void import_missingCarId_returns400() throws Exception {
        mockMvc.perform(multipart("/api/import/eu-data-act/import")
                        .file(validJsonFile())
                        .with(authentication(auth())))
                .andExpect(status().isBadRequest());
    }
}
