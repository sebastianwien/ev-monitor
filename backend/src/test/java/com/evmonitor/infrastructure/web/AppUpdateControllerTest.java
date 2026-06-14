package com.evmonitor.infrastructure.web;

import com.evmonitor.application.AppUpdateService;
import com.evmonitor.domain.AppBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests fuer AppUpdateController - das Capgo-Self-Hosted-Protokoll.
 *
 * POST /api/app/updates  -> liefert neuestes Bundle (version/url/checksum) oder leeres JSON.
 * GET  /api/app/bundle/{version}.zip -> streamt das Zip aus dem Bundle-Verzeichnis.
 *
 * Beide Endpoints sind oeffentlich (App ruft sie vor dem Login). Der Service ist
 * gemockt; das Bundle-Verzeichnis zeigt auf ein temporaeres Test-Verzeichnis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppUpdateControllerTest {

    @TempDir
    static Path bundleDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.bundle-dir", () -> bundleDir.toString());
        registry.add("app.bundle-base-url", () -> "https://test.local/api/app/bundle");
        registry.add("app.bundle-publish-token", () -> "test-secret");
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppUpdateService updateService;

    private AppBundle bundle(String version) {
        return AppBundle.builder()
                .version(version)
                .checksum("sha256-" + version)
                .filename(version + ".zip")
                .build();
    }

    @Test
    void checkUpdate_whenNewerBundleExists_returnsVersionUrlChecksum() throws Exception {
        when(updateService.findUpdateFor("1.0.4")).thenReturn(Optional.of(bundle("1.0.5")));

        mockMvc.perform(post("/api/app/updates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"ios\",\"version_name\":\"1.0.4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.0.5"))
                .andExpect(jsonPath("$.url").value("https://test.local/api/app/bundle/1.0.5.zip"))
                .andExpect(jsonPath("$.checksum").value("sha256-1.0.5"));
    }

    @Test
    void checkUpdate_whenUpToDate_returnsEmptyJsonWithoutUrl() throws Exception {
        when(updateService.findUpdateFor(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/app/updates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"ios\",\"version_name\":\"1.0.5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void downloadBundle_whenVersionKnown_streamsZipBytes() throws Exception {
        Files.write(bundleDir.resolve("1.0.5.zip"), new byte[]{1, 2, 3, 4});
        when(updateService.findByVersion("1.0.5")).thenReturn(Optional.of(bundle("1.0.5")));

        mockMvc.perform(get("/api/app/bundle/1.0.5.zip"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(content().bytes(new byte[]{1, 2, 3, 4}));
    }

    @Test
    void downloadBundle_whenVersionUnknown_returns404() throws Exception {
        when(updateService.findByVersion("9.9.9")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/app/bundle/9.9.9.zip"))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadBundle_whenVersionMalformed_returns400() throws Exception {
        mockMvc.perform(get("/api/app/bundle/not-a-version.zip"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishBundle_withValidToken_returns201() throws Exception {
        when(updateService.publish("1.0.42", "sha256-abc")).thenReturn(bundle("1.0.42"));

        mockMvc.perform(post("/api/app/bundles")
                        .header("X-App-Bundle-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.42\",\"checksum\":\"sha256-abc\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value("1.0.42"));
    }

    @Test
    void publishBundle_withWrongToken_returns403() throws Exception {
        mockMvc.perform(post("/api/app/bundles")
                        .header("X-App-Bundle-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.42\",\"checksum\":\"sha256-abc\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishBundle_withoutToken_returns403() throws Exception {
        mockMvc.perform(post("/api/app/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.42\",\"checksum\":\"sha256-abc\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishBundle_withInvalidVersion_returns400() throws Exception {
        mockMvc.perform(post("/api/app/bundles")
                        .header("X-App-Bundle-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"not-semver\",\"checksum\":\"sha256-abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishBundle_whenVersionExists_returns409() throws Exception {
        when(updateService.publish("1.0.42", "sha256-abc"))
                .thenThrow(new IllegalStateException("bundle already exists: 1.0.42"));

        mockMvc.perform(post("/api/app/bundles")
                        .header("X-App-Bundle-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.42\",\"checksum\":\"sha256-abc\"}"))
                .andExpect(status().isConflict());
    }
}
