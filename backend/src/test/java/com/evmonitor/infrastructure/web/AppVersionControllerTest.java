package com.evmonitor.infrastructure.web;

import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for AppVersionController.
 *
 * Der Endpoint liefert die minimal erforderliche Native-Version (steuert den
 * "Bitte im Store aktualisieren"-Hinweis). Web-Bundle-Updates laufen ueber Capgo,
 * daher kein webBuildHash mehr. Oeffentlich erreichbar, weil die App die Version
 * schon vor dem Login prueft.
 */
class AppVersionControllerTest extends AbstractIntegrationTest {

    @Test
    void getVersion_returnsMinNativeVersion_withoutAuth() throws Exception {
        mockMvc.perform(get("/api/app/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minNativeVersion").value(42));
    }
}
