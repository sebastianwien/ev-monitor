package com.evmonitor.infrastructure.web;

import com.evmonitor.application.AppUpdateService;
import com.evmonitor.domain.AppBundle;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.any;
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
class AppUpdateControllerTest extends AbstractIntegrationTest {

    @Value("${app.bundle-dir}")
    private Path bundleDir;

    @BeforeEach
    void createBundleDir() throws IOException {
        Files.createDirectories(bundleDir);
    }

    private AppBundle bundle(String version) {
        return AppBundle.builder()
                .version(version)
                .checksum("sha256-" + version)
                .filename(version + ".zip")
                .build();
    }

    @Test
    void checkUpdate_whenNewerBundleExists_returnsVersionUrlChecksum() throws Exception {
        doReturn(Optional.of(bundle("1.0.5"))).when(appUpdateService).findUpdateFor("1.0.4");

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
        doReturn(Optional.empty()).when(appUpdateService).findUpdateFor(any());

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
        doReturn(Optional.of(bundle("1.0.5"))).when(appUpdateService).findByVersion("1.0.5");

        mockMvc.perform(get("/api/app/bundle/1.0.5.zip"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(content().bytes(new byte[]{1, 2, 3, 4}));
    }

    @Test
    void downloadBundle_whenVersionUnknown_returns404() throws Exception {
        doReturn(Optional.empty()).when(appUpdateService).findByVersion("9.9.9");

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
        doReturn(bundle("1.0.42")).when(appUpdateService).publish("1.0.42", "sha256-abc");

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
        doThrow(new IllegalStateException("bundle already exists: 1.0.42")).when(appUpdateService).publish("1.0.42", "sha256-abc");

        mockMvc.perform(post("/api/app/bundles")
                        .header("X-App-Bundle-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.42\",\"checksum\":\"sha256-abc\"}"))
                .andExpect(status().isConflict());
    }
}
