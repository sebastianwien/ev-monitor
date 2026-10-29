package com.evmonitor.application.tariff.crawl;

import com.evmonitor.application.tariff.crawl.TariffPageFetcher.Fetched;
import com.evmonitor.infrastructure.external.anthropic.AnthropicProperties;
import com.evmonitor.infrastructure.external.anthropic.AnthropicTariffExtractClient;
import com.evmonitor.infrastructure.external.mistral.MistralProperties;
import com.evmonitor.infrastructure.external.mistral.MistralTariffExtractClient;
import com.evmonitor.infrastructure.external.tariff.HttpTariffPageFetcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestTemplate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gegen die echten Seiten der Anbieter, nicht im Standardlauf (Tag mistral-live).
 * Start: {@code ./gradlew mistralLiveTest --tests '*TariffCrawlLiveTest*'}; der zweite Test braucht
 * {@code ANTHROPIC_API_KEY} (Claude) oder {@code MISTRAL_API_KEY} und kostet ein paar Cent. Anbieter und
 * Modell für einen Vergleich: {@code TARIFF_LIVE_PROVIDER=anthropic|mistral}, {@code TARIFF_LIVE_MODEL=...}.
 * Er zählt, wie viele der geprüften Preise das Modell mit gültigem Beleg findet, und verlangt alle.
 *
 * <p>Die erwarteten Zitate stammen aus der Prüfung vom 27.10.2026. Ändert ein Anbieter seine Preise,
 * schlägt der erste Test fehl: dann die Seite ansehen und die Erwartung anpassen.
 */
@Tag("mistral-live")
class TariffCrawlLiveTest {

    /** Quelle und Zitate, die auf der Seite als Beleg für den genannten Preis stehen müssen. */
    private static final Map<String, Map<String, String>> SOURCES = Map.of(
            "https://www.aral.de/de/global/retail/pulse/tarife-bezahlmethoden.html", Map.of("0,47 €/kWh", "0.47", "0,62 €/kWh", "0.62", "2,99 €", "2.99"),
            "https://www.adac.de/rund-ums-fahrzeug/e-angebote/ladekarte/", Map.of("Aral pulse Ladestationen: 0,55 €/kWh", "0.55", "andere Betreiber: 0,75 €/kWh", "0.75"),
            "https://www.ionity.eu/de/abonnements", Map.of("0.42 €/kWh", "0.42", "0.68 €/kWh", "0.68"),
            "https://www.maingau-energie.de/e-mobilitaet/autostrom", Map.of("AC : 0,52 €/kWh DC : 0,62 €/kWh", "0.62", "AC / DC : 0,72 €/kWh", "0.72"),
            "https://www.fastnedcharging.com/de/laden/tarife", Map.of("€ 0,69", "0.69", "(€ 0,49 pro kWh in Deutschland)", "0.49"),
            "https://incharge.vattenfall.de/fair-laden", Map.of("0,44 €/kWh oder 0,49 €/kWh", "0.49"),
            "https://www.smatrics.com/de/laden/tarife", Map.of("0,55€", "0.55"),
            "https://www.oeamtc.at/oeamtc-epower/", Map.of("AC-Ladepunkt: 0,44 €/kWh", "0.44"),
            "https://www.enbw.com/elektromobilitaet/produkte/ladetarife", Map.of());

    @Test
    void allSourcesAreReadable_andThePricesStandOnThePageAsQuotes() {
        HttpTariffPageFetcher fetcher = new HttpTariffPageFetcher(new TariffCrawlProperties());
        List<String> problems = new ArrayList<>();
        SOURCES.forEach((url, quotes) -> {
            Fetched f = fetcher.fetch(url);
            System.out.printf("%-75s %-10s %6d Zeichen, %d Preisbilder%n", url, f.outcome(),
                    f.page() == null ? 0 : f.page().text().length(), f.page() == null ? 0 : f.page().priceImages().size());
            if (f.outcome() != TariffPageFetcher.Outcome.OK) {
                problems.add(url + ": " + f.outcome() + " " + f.error());
                return;
            }
            quotes.forEach((quote, price) -> {
                if (!EvidenceCheck.supports(f.page().text(), quote, new BigDecimal(price))) problems.add(url + ": kein Beleg für " + quote);
            });
            if (url.contains("enbw")) {
                assertThat(f.page().priceImages()).as("EnBW-Tarifkacheln als Bild").anyMatch(i -> i.contains("Ladetarif L"));
            }
        });
        assertThat(problems).isEmpty();
    }

    @Test
    void modelFindsTheVerifiedPrices() {
        TariffExtractor extractor = liveExtractor();
        Assumptions.assumeTrue(extractor != null, "ANTHROPIC_API_KEY oder MISTRAL_API_KEY fehlt");
        HttpTariffPageFetcher fetcher = new HttpTariffPageFetcher(new TariffCrawlProperties());
        List<String> catalog = List.of("IONITY", "EnBW", "Aral Pulse", "EWE Go", "Allego", "E.ON Drive", "EDEKA", "Fastned",
                "JET Strom", "Kaufland", "LichtBlick", "Lidl", "TankE", "Vattenfall InCharge", "Tesla Supercharger");
        List<String> missing = new ArrayList<>();
        int[] totals = new int[4]; // erwartet, gefunden, Token rein, Token raus
        SOURCES.forEach((url, quotes) -> {
            if (quotes.isEmpty()) return;
            Fetched f = fetcher.fetch(url);
            String country = url.contains("smatrics") || url.contains("oeamtc") ? "AT" : "DE";
            String emp = url.replaceFirst("https://(www\\.|incharge\\.)?", "").replaceFirst("[./].*", "");
            TariffExtractor.Extraction e = extractor.extract(new TariffExtractor.Request(emp, country, url, f.page().text(),
                    f.page().priceImages(), List.of(), catalog));
            List<ExtractedTariff> supported = e.tariffs().stream()
                    .filter(t -> EvidenceCheck.supports(f.page().text(), t.evidence(), t.pricePerKwh())).toList();
            Set<BigDecimal> found = supported.stream()
                    .flatMap(t -> Stream.of(t.pricePerKwh(), t.monthlyFeeEur(), t.sessionFeeEur()))
                    .filter(Objects::nonNull).map(BigDecimal::stripTrailingZeros).collect(Collectors.toSet());
            List<String> notFound = quotes.values().stream().distinct()
                    .filter(p -> !found.contains(new BigDecimal(p).stripTrailingZeros())).toList();
            int expected = (int) quotes.values().stream().distinct().count();
            totals[0] += expected;
            totals[1] += expected - notFound.size();
            if (e.usage() != null) {
                totals[2] += e.usage().promptTokens() == null ? 0 : e.usage().promptTokens();
                totals[3] += e.usage().completionTokens() == null ? 0 : e.usage().completionTokens();
            }
            notFound.forEach(p -> missing.add(url + ": " + p));
            System.out.printf("%n%s: %d Tarife, %d belegt, %d Preisstufen, Treffer %d von %d%n", url, e.tariffs().size(),
                    supported.size(), e.tiers().size(), expected - notFound.size(), expected);
            e.tariffs().forEach(t -> System.out.printf("  %-14s %-2s %-11s %-12s kW>%-4s %s €/kWh  Grund %s  | %s%n", t.variant(), t.chargingType(),
                    t.scope(), t.operator() == null ? (t.tier() == null ? "" : t.tier()) : t.operator(), t.minPowerKw(), t.pricePerKwh(),
                    t.monthlyFeeEur(), t.evidence()));
        });
        System.out.printf("%nGESAMT %s: Treffer %d von %d, %d Token rein, %d raus%n",
                System.getenv().getOrDefault("TARIFF_LIVE_MODEL", "Standardmodell"), totals[1], totals[0], totals[2], totals[3]);
        if (extractor instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception ignored) {
                // nur Testende
            }
        }
        assertThat(missing).as("geprüfte Preise, die das Modell nicht belegt gefunden hat").isEmpty();
    }

    /** Claude, wenn ein Anthropic-Key da ist (oder TARIFF_LIVE_PROVIDER=anthropic), sonst Mistral; null ohne Key. */
    private static TariffExtractor liveExtractor() {
        String anthropicKey = System.getenv("ANTHROPIC_API_KEY");
        String mistralKey = System.getenv("MISTRAL_API_KEY");
        String model = System.getenv("TARIFF_LIVE_MODEL");
        String provider = System.getenv().getOrDefault("TARIFF_LIVE_PROVIDER",
                anthropicKey != null && !anthropicKey.isBlank() ? "anthropic" : "mistral");
        if (provider.equals("anthropic")) {
            if (anthropicKey == null || anthropicKey.isBlank()) return null;
            AnthropicProperties props = new AnthropicProperties();
            props.setApiKey(anthropicKey);
            if (model != null) props.setTariffModel(model);
            return new AnthropicTariffExtractClient(props, new ObjectMapper());
        }
        if (mistralKey == null || mistralKey.isBlank()) return null;
        MistralProperties props = new MistralProperties();
        props.setApiKey(mistralKey);
        if (model != null) props.setTariffModel(model);
        return new MistralTariffExtractClient(new RestTemplate(), props, new ObjectMapper());
    }
}
