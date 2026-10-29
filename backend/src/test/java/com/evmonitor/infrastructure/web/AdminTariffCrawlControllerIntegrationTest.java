package com.evmonitor.infrastructure.web;

import com.evmonitor.application.tariff.crawl.TariffCrawlScheduler;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.persistence.EmpEntity;
import com.evmonitor.infrastructure.persistence.JpaEmpRepository;
import com.evmonitor.infrastructure.persistence.JpaTariffProposalRepository;
import com.evmonitor.infrastructure.persistence.JpaTariffSourceRepository;
import com.evmonitor.infrastructure.persistence.TariffProposalEntity;
import com.evmonitor.infrastructure.persistence.TariffSourceEntity;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Admin tab "Ladekarten", crawler part: sources with their last result, open proposals in plain
 * sentences with the current value next to it, apply or reject, add a source, start a run.
 * Admins only.
 */
class AdminTariffCrawlControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/api/admin/charging-tariffs/crawl";

    @MockitoBean TariffCrawlScheduler scheduler;

    @Autowired JpaEmpRepository emps;
    @Autowired JpaTariffSourceRepository sources;
    @Autowired JpaTariffProposalRepository proposals;
    @Autowired ChargingProviderTariffRepository tariffs;

    private User admin;
    private String emp;
    private TariffSourceEntity source;
    private ChargingProviderTariff tariff;

    @BeforeEach
    void setUp() {
        admin = createAndSaveAdminUser("crawl-admin-" + System.nanoTime() + "@example.com");
        emp = "Karte-" + UUID.randomUUID().toString().substring(0, 8);
        EmpEntity e = new EmpEntity();
        e.setName(emp);
        emps.save(e);
        source = new TariffSourceEntity();
        source.setId(UUID.randomUUID());
        source.setEmpName(emp);
        source.setUrl("https://" + emp.toLowerCase() + ".example/tarife");
        source.setLastStatus("CHANGED");
        source = sources.save(source);
        tariff = tariffs.save(ChargingProviderTariff.builder().id(UUID.randomUUID()).empName(emp).tariffVariant("Go")
                .chargingType(ChargingType.DC).pricePerKwh(new BigDecimal("0.39")).sessionFeeEur(BigDecimal.ZERO)
                .monthlyFeeEur(BigDecimal.ZERO).validFrom(LocalDate.of(2025, 1, 1)).sourceUrl(source.getUrl())
                .lastVerifiedAt(LocalDateTime.of(2026, 3, 25, 16, 11)).build());
    }

    @Test
    void nonAdmin_isForbidden() {
        User user = createAndSaveUser("crawl-user-" + System.nanoTime() + "@example.com");

        assertThat(restTemplate.exchange(URL, HttpMethod.GET, createAuthRequest(user.getId(), user.getEmail()), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange(URL + "/run", HttpMethod.POST, createAuthRequest(user.getId(), user.getEmail()), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange(URL + "/driver-check", HttpMethod.GET, createAuthRequest(user.getId(), user.getEmail()), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void overview_showsSourcesAndOpenProposals_withTheCurrentValue() {
        TariffProposalEntity p = proposal(TariffProposalEntity.Kind.CHANGE, Map.of("variant", "Go", "chargingType", "DC", "pricePerKwh", 0.68));

        Map<String, Object> body = send(HttpMethod.GET, URL, null).getBody();

        assertThat((List<Map<String, Object>>) body.get("sources")).anySatisfy(s -> assertThat(s)
                .containsEntry("id", source.getId().toString()).containsEntry("lastStatus", "CHANGED").containsEntry("mode", "EXTRACT"));
        assertThat((List<Map<String, Object>>) body.get("proposals")).anySatisfy(row -> {
            assertThat(row).containsEntry("id", p.getId().toString()).containsEntry("kind", "CHANGE");
            assertThat((String) row.get("description")).isEqualTo(emp + ": Go DC neu 0,68 €/kWh. Sprung über 40 %");
            assertThat(((Number) ((Map<?, ?>) row.get("current")).get("pricePerKwh")).doubleValue()).isEqualTo(0.39);
        });
        assertThat(body).containsKey("extractorAvailable");
    }

    @Test
    void apply_changesTheTariff_reject_leavesIt() {
        TariffProposalEntity change = proposal(TariffProposalEntity.Kind.CHANGE, Map.of("variant", "Go", "chargingType", "DC", "pricePerKwh", 0.68));

        assertThat(send(HttpMethod.POST, URL + "/proposals/" + change.getId() + "/apply", null).getBody()).containsEntry("applied", true);
        assertThat(tariffs.findById(tariff.getId()).orElseThrow().getValidUntil()).isNotNull();
        assertThat(send(HttpMethod.POST, URL + "/proposals/" + change.getId() + "/apply", null).getStatusCode())
                .as("already decided").isEqualTo(HttpStatus.BAD_REQUEST);

        TariffProposalEntity other = proposal(TariffProposalEntity.Kind.MISSING, Map.of("variant", "Go", "chargingType", "DC"));
        assertThat(send(HttpMethod.POST, URL + "/proposals/" + other.getId() + "/reject", null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(proposals.findById(other.getId()).orElseThrow().getStatus()).isEqualTo(TariffProposalEntity.Status.REJECTED);
        assertThat(send(HttpMethod.POST, URL + "/proposals/" + UUID.randomUUID() + "/reject", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void addSource_onlyKnownProviderAndHttpsLink() {
        Map<String, Object> ok = new HashMap<>(Map.of("empName", emp, "url", "https://" + emp.toLowerCase() + ".example/neu", "mode", "WATCH", "enabled", true));

        ResponseEntity<Map> created = send(HttpMethod.POST, URL + "/sources", ok);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody()).containsEntry("mode", "WATCH").containsEntry("enabled", true);
        assertThat(send(HttpMethod.POST, URL + "/sources", ok).getStatusCode()).as("same URL twice").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, URL + "/sources", with(ok, "url", "http://x.example/a")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, URL + "/sources", with(ok, "url", "javascript:alert(1)")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, URL + "/sources", with(ok, "empName", "Gibt es nicht")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, URL + "/sources", with(ok, "mode", "SCRAPE")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateSource_canSwitchItOff() {
        Map<String, Object> off = Map.of("empName", emp, "url", source.getUrl(), "mode", "EXTRACT", "enabled", false);

        assertThat(send(HttpMethod.PUT, URL + "/sources/" + source.getId(), off).getBody()).containsEntry("enabled", false);
        assertThat(send(HttpMethod.PUT, URL + "/sources/" + UUID.randomUUID(), off).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void driverCheck_answersForAdmins() {
        assertThat(restTemplate.exchange(URL + "/driver-check", HttpMethod.GET, createAuthRequest(admin.getId(), admin.getEmail()), List.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void run_startsInTheBackground_orSaysThatOneIsRunning() {
        when(scheduler.tryStartManual()).thenReturn(true, false);

        assertThat(send(HttpMethod.POST, URL + "/run", null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(send(HttpMethod.POST, URL + "/run", null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private TariffProposalEntity proposal(TariffProposalEntity.Kind kind, Map<String, Object> proposed) {
        TariffProposalEntity p = new TariffProposalEntity();
        p.setId(UUID.randomUUID());
        p.setSourceId(source.getId());
        p.setEmpName(emp);
        p.setKind(kind);
        p.setTariffId(tariff.getId());
        p.setProposed(new HashMap<>(proposed));
        p.setFingerprint(UUID.randomUUID().toString().replace("-", ""));
        p.setHoldReason(kind == TariffProposalEntity.Kind.CHANGE ? "Sprung über 40 %" : "Steht nicht mehr auf der Seite");
        p.setStatus(TariffProposalEntity.Status.PENDING);
        p.setFirstSeenAt(LocalDateTime.now());
        p.setLastSeenAt(LocalDateTime.now());
        return proposals.save(p);
    }

    private static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        Map<String, Object> m = new HashMap<>(base);
        m.put(key, value);
        return m;
    }

    private ResponseEntity<Map> send(HttpMethod method, String url, Object body) {
        return restTemplate.exchange(url, method, createAuthRequest(body, admin.getId(), admin.getEmail()), Map.class);
    }
}
