package com.evmonitor.infrastructure.web;

import com.evmonitor.application.tessie.TessieImportResult;
import com.evmonitor.application.tessie.TessieImportService;
import com.evmonitor.application.tessie.TessieVehicleDTO;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.security.UserPrincipal;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TessieImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TessieImportService importService;

    private static final String VALID_VIN = "5YJ3E7EAXKF000001";
    private static final String VALID_TOKEN = "tessie-token-abc";

    private Authentication auth() {
        User user = TestDataBuilder.createTestUserWithId(UUID.randomUUID(), "test@example.com", "hash");
        UserPrincipal principal = UserPrincipal.create(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    // --- /vehicles ---

    @Test
    void getVehicles_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/import/tessie/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getVehicles_missingToken_returns400() throws Exception {
        mockMvc.perform(post("/api/import/tessie/vehicles")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getVehicles_invalidToken_returns422() throws Exception {
        when(importService.fetchVehicles(any()))
                .thenThrow(HttpClientErrorException.Unauthorized.class);

        mockMvc.perform(post("/api/import/tessie/vehicles")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"bad-token\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getVehicles_validToken_returnsVehicleList() throws Exception {
        when(importService.fetchVehicles(VALID_TOKEN)).thenReturn(
                List.of(new TessieVehicleDTO(VALID_VIN, "Mein Tesla", true)));

        mockMvc.perform(post("/api/import/tessie/vehicles")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + VALID_TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vin").value(VALID_VIN))
                .andExpect(jsonPath("$[0].displayName").value("Mein Tesla"));
    }

    // --- /import ---

    @Test
    void importVin_missingToken_returns400() throws Exception {
        mockMvc.perform(post("/api/import/tessie/import")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vin\":\"" + VALID_VIN + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void importVin_invalidVin_returns400() throws Exception {
        mockMvc.perform(post("/api/import/tessie/import")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + VALID_TOKEN + "\",\"vin\":\"INVALID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void importVin_vinWithPathTraversalChars_returns400() throws Exception {
        mockMvc.perform(post("/api/import/tessie/import")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + VALID_TOKEN + "\",\"vin\":\"../../etc/passwd\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importVin_success_returnsImportResult() throws Exception {
        when(importService.importForVin(any(), eq(VALID_TOKEN), eq(VALID_VIN)))
                .thenReturn(new TessieImportResult(120, 45, 3));

        mockMvc.perform(post("/api/import/tessie/import")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + VALID_TOKEN + "\",\"vin\":\"" + VALID_VIN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drivesImported").value(120))
                .andExpect(jsonPath("$.chargesImported").value(45))
                .andExpect(jsonPath("$.skipped").value(3));
    }
}
