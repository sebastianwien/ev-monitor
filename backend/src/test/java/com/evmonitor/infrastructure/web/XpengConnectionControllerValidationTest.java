package com.evmonitor.infrastructure.web;

import com.evmonitor.application.imports.xpeng.XpengConnectionService;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.persistence.xpeng.XpengConnection;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression: Die Bean-Validation-Annotationen auf den Request-Records greifen nur,
 * wenn der Endpoint den Body mit {@code @Valid} annotiert. Ohne das lief z.B. eine
 * VIN mit Leerzeichen ungeprueft durch und wurde als DA-Anfrage an XPeng geschickt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class XpengConnectionControllerValidationTest {

    private static final String URL = "/api/imports/xpeng/connections";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private XpengConnectionService service;

    private Authentication auth() {
        User user = TestDataBuilder.createTestUserWithId(
                UUID.randomUUID(), "xpeng-validation@ev-monitor.net", "hash");
        UserPrincipal principal = UserPrincipal.create(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    /** Stubt den Service so, dass ein akzeptierter Request auch 200 liefern kann. */
    private void stubGrantSuccess() {
        when(service.grantConsent(any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(XpengConnection.builder()
                        .id(UUID.randomUUID())
                        .carId(UUID.randomUUID())
                        .vin("L1NNSGHA6SB201375")
                        .consentGrantedAt(LocalDateTime.now())
                        .consentVersion(XpengConnection.CURRENT_CONSENT_VERSION)
                        .build());
    }

    private String grantBody(String vin) {
        return """
                {"carId":"%s","vin":"%s","consentAccepted":true,"autoSync":true}
                """.formatted(UUID.randomUUID(), vin);
    }

    @Test
    void grant_rejectsVinWithSpace() throws Exception {
        // Der reale Fall: Modellname vor der VIN, zufaellig exakt 17 Zeichen.
        mockMvc.perform(post(URL).with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody("G6 L1NNSGHA6SB201")))
                .andExpect(status().isBadRequest());
        verify(service, never()).grantConsent(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void grant_rejectsTooShortVin() throws Exception {
        mockMvc.perform(post(URL).with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody("L1NNSGHA6SB201")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void grant_rejectsVinWithForbiddenLetter() throws Exception {
        // I, O und Q sind in VINs nicht zulaessig.
        mockMvc.perform(post(URL).with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody("L1NNSGHA6SB2O137")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateEmail_rejectsMalformedAddress() throws Exception {
        mockMvc.perform(patch(URL + "/" + UUID.randomUUID() + "/email")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xpengEmail\":\"nicht-mal-eine-adresse\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).updateXpengEmail(any(), any(), any());
    }

    @Test
    void activateAutoSync_rejectsMalformedEmail() throws Exception {
        mockMvc.perform(patch(URL + "/" + UUID.randomUUID() + "/autosync")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentAccepted\":true,\"xpengEmail\":\"kaputt@\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).activateAutoSync(any(), any(), any(), any(), any());
    }

    // ── Was weiterhin akzeptiert werden muss ─────────────────────────────────

    @Test
    void grant_acceptsLowercaseVin() throws Exception {
        stubGrantSuccess();
        // Der Controller normalisiert per trim+toUpperCase - die Validierung darf
        // Kleinschreibung deshalb nicht vorher abweisen.
        mockMvc.perform(post(URL).with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody("l1nnsgha6sb20137")))
                .andExpect(status().isBadRequest()); // 16 Zeichen - separat geprueft
        mockMvc.perform(post(URL).with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody("l1nnsgha6sb201375")))
                .andExpect(status().isOk());
    }

    @Test
    void grant_acceptsVinWithSurroundingWhitespace() throws Exception {
        stubGrantSuccess();
        mockMvc.perform(post(URL).with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody("  L1NNSGHA6SB201375  ")))
                .andExpect(status().isOk());
    }
}
