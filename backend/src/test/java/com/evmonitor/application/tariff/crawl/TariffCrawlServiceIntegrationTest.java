package com.evmonitor.application.tariff.crawl;

import com.evmonitor.application.publicapi.CpoNameNormalizer;
import com.evmonitor.application.tariff.crawl.ExtractedTariff.Scope;
import com.evmonitor.application.tariff.crawl.TariffPageFetcher.Fetched;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.infrastructure.persistence.ChargingNetworkEntity;
import com.evmonitor.infrastructure.persistence.CpoEmpTierMappingEntity;
import com.evmonitor.infrastructure.persistence.EmpEntity;
import com.evmonitor.infrastructure.persistence.JpaChargingNetworkRepository;
import com.evmonitor.infrastructure.persistence.JpaCpoEmpTierMappingRepository;
import com.evmonitor.infrastructure.persistence.JpaEmpRepository;
import com.evmonitor.infrastructure.persistence.JpaTariffProposalRepository;
import com.evmonitor.infrastructure.persistence.JpaTariffSourceRepository;
import com.evmonitor.infrastructure.persistence.TariffProposalEntity;
import com.evmonitor.infrastructure.persistence.TariffProposalEntity.Kind;
import com.evmonitor.infrastructure.persistence.TariffProposalEntity.Status;
import com.evmonitor.infrastructure.persistence.TariffSourceEntity;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One crawler run against a fake page and a fake language model, with the real database.
 * Unchanged prices renew the date; a new price is applied only after a second run sees it again;
 * doubtful changes, new and missing tariffs wait for the admin; a broken page changes nothing.
 */
class TariffCrawlServiceIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 27, 5, 40);
    private static final LocalDateTime T1 = T0.plusDays(1);
    private static final LocalDateTime OLD_CHECK = LocalDateTime.of(2026, 3, 25, 16, 11);

    @MockitoBean TariffPageFetcher fetcher;
    @MockitoBean TariffExtractor extractor;

    @Autowired TariffCrawlService service;
    @Autowired TariffProposalBook book;
    @Autowired TariffCrawlProperties props;
    @Autowired ChargingProviderTariffRepository tariffs;
    @Autowired JpaTariffSourceRepository sources;
    @Autowired JpaTariffProposalRepository proposals;
    @Autowired JpaEmpRepository emps;
    @Autowired JpaChargingNetworkRepository networks;
    @Autowired JpaCpoEmpTierMappingRepository tierMapping;
    @Autowired CpoNameNormalizer normalizer;

    private String emp;
    private String url;
    private String network;
    private TariffSourceEntity source;
    private final List<ExtractedTariff> onPage = new ArrayList<>();
    private String pageText;
    private int originalConfirmHours;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        emp = "Karte-" + suffix;
        url = "https://karte-" + suffix + ".example/tarife";
        network = "Netz-" + suffix;
        EmpEntity e = new EmpEntity();
        e.setName(emp);
        emps.save(e);
        ChargingNetworkEntity n = new ChargingNetworkEntity();
        n.setName(network);
        networks.save(n);
        normalizer.init();
        source = new TariffSourceEntity();
        source.setId(UUID.randomUUID());
        source.setEmpName(emp);
        source.setUrl(url);
        source = sources.save(source);
        originalConfirmHours = props.getConfirmAfterHours();
        props.setConfirmAfterHours(20);
        pageText = "Tarif L\nDC: 0,39 €/kWh, Grundgebühr 11,99 €";
        when(extractor.isAvailable()).thenReturn(true);
        when(fetcher.fetch(anyString())).thenAnswer(i -> Fetched.ok(200, new PageText(pageText, List.of())));
        when(extractor.extract(any())).thenAnswer(i -> new TariffExtractor.Extraction(List.copyOf(onPage), List.of(), null));
    }

    @AfterEach
    void restore() {
        props.setConfirmAfterHours(originalConfirmHours);
        sources.delete(source);
    }

    @Test
    void unchangedPrices_renewTheDate_noProposal() {
        ChargingProviderTariff l = save("L", "0.39", "11.99");
        onPage.add(offer("L", "0.39", "11.99", "DC: 0,39 €/kWh"));

        service.run(true, T0);

        assertThat(tariffs.findById(l.getId()).orElseThrow().getLastVerifiedAt()).isEqualTo(T0);
        assertThat(pending()).isEmpty();
        assertThat(sources.findById(source.getId()).orElseThrow().getLastStatus()).isEqualTo("OK");
    }

    @Test
    void newPrice_isAppliedOnlyWhenASecondRunSeesItAgain() {
        ChargingProviderTariff l = save("L", "0.39", "11.99");
        pageText = "Tarif L\nDC: 0,42 €/kWh, Grundgebühr 11,99 €";
        onPage.add(offer("L", "0.42", "11.99", "DC: 0,42 €/kWh"));

        service.run(true, T0);
        assertThat(pending()).singleElement().extracting(TariffProposalEntity::getKind).isEqualTo(Kind.CHANGE);
        assertThat(tariffs.findById(l.getId()).orElseThrow().getValidUntil()).as("first run changes nothing").isNull();

        service.run(true, T1);

        assertThat(tariffs.findById(l.getId()).orElseThrow().getValidUntil()).isEqualTo(T1.toLocalDate().minusDays(1));
        assertThat(current()).singleElement().satisfies(t -> {
            assertThat(t.getPricePerKwh()).isEqualByComparingTo("0.42");
            assertThat(t.getValidFrom()).isEqualTo(T1.toLocalDate());
            assertThat(t.getLastVerifiedAt()).isEqualTo(T1);
        });
        assertThat(proposals.findBySourceIdAndStatus(source.getId(), Status.APPLIED)).singleElement()
                .extracting(TariffProposalEntity::getDecidedBy).isEqualTo("AUTO");
    }

    @Test
    void secondRunBeforeTheWaitingTime_doesNotApply() {
        save("L", "0.39", "11.99");
        pageText = "DC: 0,42 €/kWh";
        onPage.add(offer("L", "0.42", "11.99", "DC: 0,42 €/kWh"));

        service.run(false, T0);
        service.run(false, T0.plusHours(2));

        assertThat(pending()).singleElement().extracting(TariffProposalEntity::getSeenCount).isEqualTo(2);
    }

    @Test
    void doubtfulChange_waitsForTheAdmin_evenAfterTwoRuns_thenAdminApplies() {
        ChargingProviderTariff l = save("L", "0.39", "11.99");
        pageText = "DC: 0,69 €/kWh";
        onPage.add(offer("L", "0.69", "11.99", "DC: 0,69 €/kWh"));

        service.run(true, T0);
        TariffCrawlService.RunSummary second = service.run(true, T1);

        TariffProposalEntity p = pending().get(0);
        assertThat(p.getHoldReason()).contains("Sprung");
        assertThat(tariffs.findById(l.getId()).orElseThrow().getValidUntil()).isNull();
        assertThat(second.needAdmin()).as("mailed once, on the first run").isEmpty();

        assertThat(book.applyByAdmin(p.getId(), T1)).isTrue();
        assertThat(current()).singleElement().extracting(ChargingProviderTariff::getPricePerKwh)
                .satisfies(price -> assertThat(price).isEqualByComparingTo("0.69"));
    }

    @Test
    void samePage_noOpenProposal_skipsTheLanguageModel() {
        ChargingProviderTariff l = save("L", "0.39", "11.99");
        onPage.add(offer("L", "0.39", "11.99", "DC: 0,39 €/kWh"));
        service.run(false, T0);
        org.mockito.Mockito.clearInvocations(extractor);

        service.run(false, T1);

        verify(extractor, never()).extract(any());
        assertThat(sources.findById(source.getId()).orElseThrow().getLastStatus()).isEqualTo("UNCHANGED");
        assertThat(tariffs.findById(l.getId()).orElseThrow().getLastVerifiedAt()).isEqualTo(T1);
    }

    @Test
    void brokenPage_countsAFailure_andChangesNothing() {
        ChargingProviderTariff l = save("L", "0.39", "11.99");
        when(fetcher.fetch(anyString())).thenReturn(Fetched.failed(TariffPageFetcher.Outcome.HTTP_ERROR, 404, "HTTP 404"));

        service.run(false, T0);
        TariffCrawlService.RunSummary second = service.run(false, T1);

        TariffSourceEntity s = sources.findById(source.getId()).orElseThrow();
        assertThat(s.getConsecutiveFailures()).isEqualTo(2);
        assertThat(s.getLastHttpStatus()).isEqualTo(404);
        assertThat(second.newlyFailing()).extracting(TariffSourceEntity::getId).containsExactly(source.getId());
        assertThat(tariffs.findById(l.getId()).orElseThrow().getLastVerifiedAt()).isEqualTo(OLD_CHECK);
    }

    @Test
    void inventedPrice_withoutQuoteOnThePage_isDropped() {
        ChargingProviderTariff l = save("L", "0.39", "11.99");
        onPage.add(offer("L", "0.29", "11.99", "DC: 0,29 €/kWh"));

        service.run(false, T0);

        assertThat(sources.findById(source.getId()).orElseThrow().getLastStatus()).isEqualTo("UNREADABLE");
        assertThat(pending()).isEmpty();
        assertThat(tariffs.findById(l.getId()).orElseThrow().getLastVerifiedAt()).isEqualTo(OLD_CHECK);
    }

    @Test
    void newAndMissingTariffs_andOperatorPrices_waitForTheAdmin() {
        save("L", "0.39", "11.99");
        save("S", "0.56", "0");
        pageText = "Tarif L DC: 0,39 €/kWh\nAn " + network + "-Säulen 0,59 €/kWh";
        onPage.add(offer("L", "0.39", "11.99", "Tarif L DC: 0,39 €/kWh"));
        onPage.add(new ExtractedTariff("L", "DC", new BigDecimal("0.59"), null, null, null, null, null,
                Scope.OPERATOR, network, network, null, null, false, "0,59 €/kWh"));

        TariffCrawlService.RunSummary run = service.run(false, T0);

        assertThat(pending()).extracting(TariffProposalEntity::getKind).containsExactlyInAnyOrder(Kind.NEW, Kind.MISSING);
        assertThat(run.needAdmin()).hasSize(2);
        TariffProposalEntity added = pending().stream().filter(p -> p.getKind() == Kind.NEW).findFirst().orElseThrow();
        book.applyByAdmin(added.getId(), T0);
        assertThat(current()).anySatisfy(t -> {
            assertThat(t.getCpoName()).isEqualTo(network);
            assertThat(t.getSourceUrl()).isEqualTo(url);
        });
    }

    @Test
    void operatorNotOnThePage_orNotInTheCatalog_isDropped() {
        save("L", "0.39", "11.99");
        onPage.add(offer("L", "0.39", "11.99", "DC: 0,39 €/kWh"));
        onPage.add(new ExtractedTariff("L", "DC", new BigDecimal("0.39"), null, null, null, null, null,
                Scope.OPERATOR, "Erfunden GmbH", "Erfunden", null, null, false, "DC: 0,39 €/kWh"));

        service.run(false, T0);

        assertThat(pending()).isEmpty();
    }

    @Test
    void changedTierList_isAMappingProposal() {
        save("L", "0.39", "11.99");
        CpoEmpTierMappingEntity m = new CpoEmpTierMappingEntity();
        m.setEmpName(emp);
        m.setCpoName(network);
        m.setPriceTier("LOW");
        tierMapping.save(m);
        pageText = "DC: 0,39 €/kWh\nHochpreis: " + network + " und weitere";
        onPage.add(offer("L", "0.39", "11.99", "DC: 0,39 €/kWh"));
        when(extractor.extract(any())).thenAnswer(i -> new TariffExtractor.Extraction(List.copyOf(onPage),
                List.of(new TariffExtractor.TierMembers("HIGH", List.of(new TariffExtractor.OperatorName(network, network)))), null));

        service.run(false, T0);

        TariffProposalEntity p = pending().get(0);
        assertThat(p.getKind()).isEqualTo(Kind.MAPPING);
        book.applyByAdmin(p.getId(), T0);
        assertThat(tierMapping.findTierByEmpAndCpo(emp, network)).contains("HIGH");
    }

    @Test
    void watchSource_firstSightingIsTrusted_aChangeWaitsForTheAdmin() {
        ChargingProviderTariff l = save("L", "0.39", "11.99");
        source.setMode(TariffSourceEntity.Mode.WATCH);
        sources.save(source);

        service.run(false, T0);
        assertThat(tariffs.findById(l.getId()).orElseThrow().getLastVerifiedAt()).isEqualTo(T0);

        pageText = "Neue Tarifkacheln";
        service.run(false, T1);
        TariffProposalEntity p = pending().get(0);
        assertThat(p.getKind()).isEqualTo(Kind.WATCH_CHANGED);
        assertThat(tariffs.findById(l.getId()).orElseThrow().getLastVerifiedAt()).isEqualTo(T0);

        book.applyByAdmin(p.getId(), T1);
        assertThat(tariffs.findById(l.getId()).orElseThrow().getLastVerifiedAt()).isEqualTo(T1);
        verify(extractor, never()).extract(any());
    }

    @Test
    void withoutLanguageModel_extractSourcesAreSkipped_notCountedAsFailure() {
        save("L", "0.39", "11.99");
        when(extractor.isAvailable()).thenReturn(false);

        service.run(false, T0);

        TariffSourceEntity s = sources.findById(source.getId()).orElseThrow();
        assertThat(s.getLastStatus()).isEqualTo("EXTRACT_ERROR");
        assertThat(s.getConsecutiveFailures()).isZero();
    }

    @Test
    void languageModelError_isNoPageFailure_isReportedOnce_andRetriedNextDay() {
        save("L", "0.39", "11.99");
        when(extractor.extract(any())).thenThrow(new TariffExtractionException("Mistral: REJECTED"));

        TariffCrawlService.RunSummary run = service.run(true, T0);

        TariffSourceEntity s = sources.findById(source.getId()).orElseThrow();
        assertThat(s.getLastStatus()).isEqualTo("EXTRACT_ERROR");
        assertThat(s.getLastHttpStatus()).isEqualTo(200);
        assertThat(s.getConsecutiveFailures()).isZero();
        assertThat(run.extractionErrors()).containsExactly("Mistral: REJECTED");
        assertThat(service.isDue(s, T0.plusHours(2))).isFalse();
        assertThat(service.isDue(s, T1)).as("failed sources are retried the next day").isTrue();
    }

    @Test
    void unreachablePage_isRetriedNextDay() {
        save("L", "0.39", "11.99");
        when(fetcher.fetch(anyString())).thenReturn(Fetched.failed(TariffPageFetcher.Outcome.UNREACHABLE, 0, "Timeout"));

        service.run(true, T0);

        assertThat(service.isDue(sources.findById(source.getId()).orElseThrow(), T1)).isTrue();
    }

    private List<TariffProposalEntity> pending() {
        return proposals.findBySourceIdAndStatus(source.getId(), Status.PENDING);
    }

    private List<ChargingProviderTariff> current() {
        return tariffs.findAll().stream().filter(t -> emp.equals(t.getEmpName()) && t.getValidUntil() == null).toList();
    }

    private ChargingProviderTariff save(String variant, String price, String fee) {
        return tariffs.save(ChargingProviderTariff.builder().id(UUID.randomUUID()).empName(emp).tariffVariant(variant)
                .chargingType(ChargingType.DC).pricePerKwh(new BigDecimal(price)).sessionFeeEur(BigDecimal.ZERO)
                .monthlyFeeEur(new BigDecimal(fee)).validFrom(LocalDate.of(2025, 12, 1)).sourceUrl(url)
                .lastVerifiedAt(OLD_CHECK).build());
    }

    private static ExtractedTariff offer(String variant, String price, String fee, String evidence) {
        return new ExtractedTariff(variant, "DC", new BigDecimal(price), new BigDecimal(fee), null, null, null, null,
                Scope.OWN_NETWORK, null, null, null, null, false, evidence);
    }
}
