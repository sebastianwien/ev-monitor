package com.evmonitor.application.tariff.crawl;

import com.evmonitor.application.publicapi.CpoNameNormalizer;
import com.evmonitor.application.tariff.crawl.ExtractedTariff.Scope;
import com.evmonitor.application.tariff.crawl.TariffExtractor.Extraction;
import com.evmonitor.application.tariff.crawl.TariffExtractor.OperatorName;
import com.evmonitor.application.tariff.crawl.TariffExtractor.TierMembers;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.infrastructure.persistence.JpaEmpRepository;
import com.evmonitor.infrastructure.persistence.TariffSourceEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Liest eine Quelle: Seite laden, Tarife vom Sprachmodell extrahieren lassen und jedes Ergebnis
 * prüfen. Ein Preis zählt nur mit Zitat, das auf der Seite steht und den Preis enthält; ein
 * Betreiber nur, wenn sein Seitenname auf der Seite steht und sein Katalogname im Katalog.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TariffSourceReader {

    private static final Set<String> TIERS = Set.of("LOW", "STANDARD", "HIGH");
    private static final Set<String> TYPES = Set.of("AC", "DC");

    private final TariffPageFetcher fetcher;
    private final TariffExtractor extractor;
    private final CpoNameNormalizer operators;
    private final JpaEmpRepository emps;
    private final TariffCrawlProperties props;

    /** Geprüftes Ergebnis einer Seite; {@code dropped} zählt Angebote ohne gültigen Beleg. */
    public record Reading(List<ExtractedTariff> tariffs, List<TierMembers> tiers, int dropped) {}

    public TariffPageFetcher.Fetched fetch(String url) {
        return fetcher.fetch(url);
    }

    public boolean canExtract() {
        return extractor.isAvailable();
    }

    public Reading read(TariffSourceEntity source, PageText page, List<ChargingProviderTariff> known) {
        String text = page.text().length() > props.getMaxTextChars() ? page.text().substring(0, props.getMaxTextChars()) : page.text();
        String country = emps.findById(source.getEmpName()).map(e -> e.getCountryCode()).filter(Objects::nonNull).orElse("DE");
        Extraction extraction = extractor.extract(new TariffExtractor.Request(source.getEmpName(), country, source.getUrl(), text,
                page.priceImages(), known.stream().map(TariffSourceReader::nameOnly).distinct().toList(), operators.getKnownCpos()));
        List<ExtractedTariff> ok = new ArrayList<>();
        int dropped = 0;
        for (ExtractedTariff t : extraction.tariffs()) {
            ExtractedTariff checked = check(t, page.text());
            if (checked == null) dropped++;
            else ok.add(checked);
        }
        if (dropped > 0) log.info("Tarif-Crawler {}: {} von {} Angeboten ohne gültigen Beleg verworfen",
                source.getEmpName(), dropped, extraction.tariffs().size());
        return new Reading(ok, checkTiers(extraction.tiers(), page.text()), dropped);
    }

    private ExtractedTariff check(ExtractedTariff t, String pageText) {
        if (t.pricePerKwh() == null || !TYPES.contains(t.chargingType())) return null;
        if (!EvidenceCheck.supports(pageText, t.evidence(), t.pricePerKwh())) return null;
        if (t.minPowerKw() != null && (t.minPowerKw() < 1 || !"DC".equals(t.chargingType()))) return null;
        if (negative(t.monthlyFeeEur()) || negative(t.sessionFeeEur()) || negative(t.blockingFeePerMin())) return null;
        Scope scope = t.scope() == null ? Scope.OWN_NETWORK : t.scope();
        String operator = null;
        if (scope == Scope.OPERATOR) {
            operator = operators.canonicalOperator(t.operator());
            if (operator == null || !EvidenceCheck.mentions(pageText, t.operatorOnPage())) return null;
        }
        if (scope == Scope.TIER && !TIERS.contains(t.tier())) return null;
        String variant = t.variant() == null || t.variant().isBlank() ? "Standard" : t.variant().trim();
        return new ExtractedTariff(variant, t.chargingType(), t.pricePerKwh(), t.monthlyFeeEur(), t.sessionFeeEur(),
                t.blockingFeePerMin(), t.blockingFeeAfterMin(), t.blockingFeeCapEur(), scope, operator, t.operatorOnPage(),
                scope == Scope.TIER ? t.tier() : null, t.minPowerKw(), t.dynamic(), t.evidence());
    }

    private List<TierMembers> checkTiers(List<TierMembers> tiers, String pageText) {
        if (tiers == null) return List.of();
        return tiers.stream()
                .filter(m -> TIERS.contains(m.tier()) && m.operators() != null)
                .map(m -> new TierMembers(m.tier(), m.operators().stream()
                        .filter(o -> EvidenceCheck.mentions(pageText, o.onPage()))
                        .map(o -> new OperatorName(o.onPage(), operators.canonicalOperator(o.catalog())))
                        .filter(o -> o.catalog() != null)
                        .toList()))
                .filter(m -> !m.operators().isEmpty())
                .toList();
    }

    /**
     * Ein gepflegter Tarif nur mit Namen und Geltungsbereich, ohne Preise: das Modell soll die
     * Namen übernehmen, aber Preise nur von der Seite lesen und nie den alten Wert wiederholen.
     */
    static ExtractedTariff nameOnly(ChargingProviderTariff t) {
        Scope scope = t.getCpoName() != null ? Scope.OPERATOR : t.getPriceTier() != null ? Scope.TIER
                : t.isAppliesElsewhere() ? Scope.ELSEWHERE : Scope.OWN_NETWORK;
        return new ExtractedTariff(t.getTariffVariant(), t.getChargingType().name(), null, null, null, null, null, null,
                scope, t.getCpoName(), null, t.getPriceTier(), t.getMinPowerKw(), false, null);
    }

    private static boolean negative(BigDecimal v) {
        return v != null && v.signum() < 0;
    }
}
