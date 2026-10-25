package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.persistence.ChargingNetworkEntity;
import com.evmonitor.infrastructure.persistence.JpaChargingNetworkRepository;
import com.evmonitor.infrastructure.persistence.JpaEmpRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin-Tab "Ladekarten": gepflegte Tarife anlegen, ändern und beenden. Nur für Admins,
 * jede Eingabe serverseitig geprüft. Ein neuer Anbieter landet im EMP-Katalog, ein Betreiber
 * muss im Katalog der Ladenetze stehen.
 */
class AdminChargingTariffControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/api/admin/charging-tariffs";

    @Autowired JpaChargingNetworkRepository networkRepository;
    @Autowired JpaEmpRepository empRepository;

    private User admin;
    private String emp;
    private String network;

    @BeforeEach
    void setUp() {
        admin = createAndSaveAdminUser("tariff-admin-" + System.nanoTime() + "@example.com");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        emp = "Karte-" + suffix;
        network = "Netz-" + suffix;
        ChargingNetworkEntity n = new ChargingNetworkEntity();
        n.setName(network);
        networkRepository.save(n);
    }

    @Test
    void nonAdmin_isForbidden() {
        User user = createAndSaveUser("tariff-user-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> res = restTemplate.exchange(URL, HttpMethod.GET, createAuthRequest(user.getId(), user.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void create_listsTheTariffAndAddsTheProviderToTheCatalog() {
        ResponseEntity<Map> created = send(HttpMethod.POST, URL, command());

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody()).containsEntry("empName", emp).containsEntry("cpoName", network)
                .containsEntry("chargingType", "DC").containsKey("lastVerifiedAt");
        assertThat(empRepository.existsById(emp)).isTrue();

        Map<?, ?> overview = send(HttpMethod.GET, URL, null).getBody();
        assertThat((List<Map<String, Object>>) overview.get("tariffs")).anySatisfy(t -> assertThat(t).containsEntry("id", created.getBody().get("id")));
        assertThat((List<String>) overview.get("emps")).contains(emp);
        assertThat((List<String>) overview.get("cpos")).contains(network);
    }

    @Test
    void invalidInput_400() {
        assertThat(send(HttpMethod.POST, URL, with("pricePerKwh", -0.1)).getStatusCode()).as("negative price").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, URL, with("pricePerKwh", 5)).getStatusCode()).as("absurd price").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, URL, with("sourceUrl", "javascript:alert(1)")).getStatusCode()).as("no web link").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, URL, with("validUntil", "2025-12-31")).getStatusCode()).as("ends before it starts").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, URL, with("cpoName", "Gibt es nicht")).getStatusCode()).as("unknown operator").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, URL, with("priceTier", "HIGH")).getStatusCode()).as("operator and tier").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, URL, with("chargingType", "UNKNOWN")).getStatusCode()).as("no charging type").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, URL, with("empName", " ")).getStatusCode()).as("no provider").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void update_changesThePriceAndRenewsTheDate() {
        String id = (String) send(HttpMethod.POST, URL, command()).getBody().get("id");

        ResponseEntity<Map> updated = send(HttpMethod.PUT, URL + "/" + id, with("pricePerKwh", 0.45));

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) updated.getBody().get("pricePerKwh")).doubleValue()).isEqualTo(0.45);
        assertThat(send(HttpMethod.PUT, URL + "/" + UUID.randomUUID(), command()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void end_setsTheLastDay_notBeforeTheFirst() {
        String id = (String) send(HttpMethod.POST, URL, command()).getBody().get("id");

        ResponseEntity<Map> ended = send(HttpMethod.POST, URL + "/" + id + "/end", Map.of("validUntil", "2026-10-25"));

        assertThat(ended.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ended.getBody()).containsEntry("validUntil", "2026-10-25");
        assertThat(send(HttpMethod.POST, URL + "/" + id + "/end", Map.of("validUntil", "2025-12-31")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, URL + "/" + UUID.randomUUID() + "/end", Map.of("validUntil", "2026-10-25")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private Map<String, Object> command() {
        Map<String, Object> c = new HashMap<>();
        c.put("empName", emp);
        c.put("tariffVariant", "Standard");
        c.put("cpoName", network);
        c.put("chargingType", "DC");
        c.put("pricePerKwh", 0.49);
        c.put("monthlyFeeEur", 5.99);
        c.put("sessionFeeEur", 0);
        c.put("validFrom", LocalDate.of(2026, 1, 1).toString());
        c.put("sourceUrl", "https://example.com/tarife");
        return c;
    }

    private Map<String, Object> with(String key, Object value) {
        Map<String, Object> c = command();
        c.put(key, value);
        return c;
    }

    private ResponseEntity<Map> send(HttpMethod method, String url, Object body) {
        return restTemplate.exchange(url, method, createAuthRequest(body, admin.getId(), admin.getEmail()), Map.class);
    }
}
