package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Beide Säulen-Endpunkte nur angemeldet: sie fragen ein fremdes Register ab.
 */
class ChargingStationControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void ohneAnmeldungKeineSaeulen() {
        assertThat(restTemplate.getForEntity("/api/charging-stations?cells=u1qcvx", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity("/api/charging-stations/tariffs?cpo=EnBW&type=DC", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** Im Test ist das Register abgeschaltet: die Zelle kommt als "fehlt", die Antwort bleibt 200. */
    @SuppressWarnings("unchecked")
    @Test
    void angemeldetLiefertZellenUndFehlende() {
        User user = createAndSaveUser("stations-map@test.local");

        var response = restTemplate.exchange("/api/charging-stations?cells=u1qcvx", HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((java.util.List<String>) response.getBody().get("missing")).containsExactly("u1qcvx");
    }
}
