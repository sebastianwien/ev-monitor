package com.evmonitor.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for AppVersionController.
 *
 * Der Endpoint liefert die Versions-Metadaten der nativen App (Capacitor):
 * den aktuellen Web-Build-Hash (steuert spaeter Live-Updates) und die minimal
 * erforderliche Native-Version (steuert den "Bitte im Store aktualisieren"-Hinweis).
 * Er muss oeffentlich erreichbar sein, weil die App die Version schon vor dem
 * Login prueft.
 */
@SpringBootTest(properties = {
        "app.web-build-hash=abc1234",
        "app.min-native-version=42"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppVersionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getVersion_returnsConfiguredMetadata_withoutAuth() throws Exception {
        mockMvc.perform(get("/api/app/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webBuildHash").value("abc1234"))
                .andExpect(jsonPath("$.minNativeVersion").value(42));
    }
}
