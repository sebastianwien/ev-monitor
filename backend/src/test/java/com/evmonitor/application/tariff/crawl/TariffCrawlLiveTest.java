package com.evmonitor.application.tariff.crawl;

import com.evmonitor.application.tariff.crawl.TariffPageFetcher.Fetched;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gegen die echten Seiten der Anbieter, nicht im Standardlauf (Tag mistral-live).
 * Start: {@code ./gradlew mistralLiveTest --tests '*TariffCrawlLiveTest*'}; der zweite Test braucht
 * {@code MISTRAL_API_KEY} und kostet ein paar Cent.
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
    void mistralReadsTheSamePrices() {
        String key = System.getenv("MISTRAL_API_KEY");
        Assumptions.assumeTrue(key != null && !key.isBlank(), "MISTRAL_API_KEY fehlt");
        MistralProperties props = new MistralProperties();
        props.setApiKey(key);
        TariffExtractor extractor = new MistralTariffExtractClient(new RestTemplate(), props, new ObjectMapper());
        HttpTariffPageFetcher fetcher = new HttpTariffPageFetcher(new TariffCrawlProperties());
        List<String> catalog = List.of("IONITY", "EnBW", "Aral Pulse", "EWE Go", "Allego", "E.ON Drive", "EDEKA", "Fastned",
                "JET Strom", "Kaufland", "LichtBlick", "Lidl", "TankE", "Vattenfall InCharge", "Tesla Supercharger");
        SOURCES.forEach((url, quotes) -> {
            if (quotes.isEmpty()) return;
            Fetched f = fetcher.fetch(url);
            String country = url.contains("smatrics") || url.contains("oeamtc") ? "AT" : "DE";
            String emp = url.replaceFirst("https://(www\\.|incharge\\.)?", "").replaceFirst("[./].*", "");
            TariffExtractor.Extraction e = extractor.extract(new TariffExtractor.Request(emp, country, url, f.page().text(),
                    f.page().priceImages(), List.of(), catalog));
            long ok = e.tariffs().stream().filter(t -> EvidenceCheck.supports(f.page().text(), t.evidence(), t.pricePerKwh())).count();
            System.out.printf("%n%s: %d Tarife, %d belegt, %d Preisstufen%n", url, e.tariffs().size(), ok, e.tiers().size());
            e.tariffs().forEach(t -> System.out.printf("  %-14s %-2s %-11s %-12s kW>%-4s %s €/kWh  Grund %s  | %s%n", t.variant(), t.chargingType(),
                    t.scope(), t.operator() == null ? (t.tier() == null ? "" : t.tier()) : t.operator(), t.minPowerKw(), t.pricePerKwh(),
                    t.monthlyFeeEur(), t.evidence()));
            assertThat(ok).as(url).isPositive();
        });
    }
}
