package com.evmonitor.infrastructure.web;

import com.evmonitor.application.tessie.TessieImportResult;
import com.evmonitor.application.tessie.TessieImportService;
import com.evmonitor.application.tessie.TessieVehicleDTO;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.security.UserPrincipal;
import com.evmonitor.testutil.TestDataBuilder;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TessieImportControllerTest extends AbstractIntegrationTest {

    private static final String VALID_VIN = "5YJ3E7EAXKF000001";
    private static final String VALID_TOKEN = "tessie-token-abc";
    private final UUID carId = UUID.randomUUID();

    private final UUID userId = UUID.randomUUID();

    private Authentication auth() {
        User user = TestDataBuilder.createTestUserWithId(userId, "test@example.com", "hash");
        UserPrincipal principal = UserPrincipal.create(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private Car ownedCar() {
        return carRepository.save(TestDataBuilder.createTestCarWithId(carId, userId, CarBrand.CarModel.MODEL_3));
    }

    private Car foreignCar() {
        return carRepository.save(TestDataBuilder.createTestCarWithId(carId, UUID.randomUUID(), CarBrand.CarModel.MODEL_3));
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
        doThrow(HttpClientErrorException.Unauthorized.class).when(tessieImportService).fetchVehicles(any());

        mockMvc.perform(post("/api/import/tessie/vehicles")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"bad-token\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getVehicles_validToken_returnsVehicleList() throws Exception {
        doReturn(
                List.of(new TessieVehicleDTO(VALID_VIN, "Mein Tesla", true))).when(tessieImportService).fetchVehicles(VALID_TOKEN);

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
                        .content("{\"vin\":\"" + VALID_VIN + "\",\"carId\":\"" + carId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void importVin_invalidVin_returns400() throws Exception {
        mockMvc.perform(post("/api/import/tessie/import")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + VALID_TOKEN + "\",\"vin\":\"INVALID\",\"carId\":\"" + carId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void importVin_vinWithPathTraversalChars_returns400() throws Exception {
        mockMvc.perform(post("/api/import/tessie/import")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + VALID_TOKEN + "\",\"vin\":\"../../etc/passwd\",\"carId\":\"" + carId + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importVin_missingCarId_returns400() throws Exception {
        mockMvc.perform(post("/api/import/tessie/import")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + VALID_TOKEN + "\",\"vin\":\"" + VALID_VIN + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void importVin_carNotFound_returns404() throws Exception {
        mockMvc.perform(post("/api/import/tessie/import")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + VALID_TOKEN + "\",\"vin\":\"" + VALID_VIN + "\",\"carId\":\"" + carId + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void importVin_foreignCar_returns404_withoutLeakingExistence() throws Exception {
        foreignCar();

        mockMvc.perform(post("/api/import/tessie/import")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + VALID_TOKEN + "\",\"vin\":\"" + VALID_VIN + "\",\"carId\":\"" + carId + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void importVin_success_returnsImportResult() throws Exception {
        ownedCar();
        doReturn(new TessieImportResult(120, 45, 3, 42, 110)).when(tessieImportService).importForVin(any(), eq(VALID_TOKEN), eq(VALID_VIN), eq(carId));

        mockMvc.perform(post("/api/import/tessie/import")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + VALID_TOKEN + "\",\"vin\":\"" + VALID_VIN + "\",\"carId\":\"" + carId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drivesImported").value(120))
                .andExpect(jsonPath("$.chargesImported").value(45))
                .andExpect(jsonPath("$.skipped").value(3))
                .andExpect(jsonPath("$.evLogsCreated").value(42))
                .andExpect(jsonPath("$.evTripsCreated").value(110));
    }
}
