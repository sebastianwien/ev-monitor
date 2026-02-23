package com.evmonitor.infrastructure.web;

import com.evmonitor.application.EvLogRequest;
import com.evmonitor.application.EvLogResponse;
import com.evmonitor.application.EvLogService;
import com.evmonitor.domain.DrivingStyle;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EvLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EvLogService evLogService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser
    void logDrive_shouldReturnCreated() throws Exception {
        UUID carId = UUID.randomUUID();

        EvLogRequest request = new EvLogRequest(
                carId,
                new BigDecimal("50.0"),
                new BigDecimal("14.5"),
                new BigDecimal("22.5"),
                DrivingStyle.ECO);

        EvLogResponse mockedResponse = new EvLogResponse(
                UUID.randomUUID(),
                carId,
                request.distanceKm(),
                request.consumptionKwhPer100km(),
                request.outsideTempC(),
                request.drivingStyle(),
                LocalDateTime.now(),
                LocalDateTime.now());

        when(evLogService.logDrive(any(UUID.class), any(EvLogRequest.class))).thenReturn(mockedResponse);

        mockMvc.perform(post("/api/logs")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.distanceKm").value("50.0"))
                .andExpect(jsonPath("$.drivingStyle").value("ECO"))
                .andExpect(jsonPath("$.carId").exists());
    }
}
