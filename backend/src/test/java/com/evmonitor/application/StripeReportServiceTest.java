package com.evmonitor.application;

import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.external.StripeReportClient;
import com.evmonitor.infrastructure.external.StripeReportClient.StripeRawData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Aggregation is tested against hand-built Stripe JSON fixtures. Timestamps are epoch seconds;
 * "now" is fixed at 2026-09-23T12:00Z so month bucketing is deterministic.
 */
@ExtendWith(MockitoExtension.class)
class StripeReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");
    private static final long T_2026_06_10 = Instant.parse("2026-06-10T10:00:00Z").getEpochSecond();
    private static final long T_2026_07_05 = Instant.parse("2026-07-05T10:00:00Z").getEpochSecond();
    private static final long T_2026_08_01 = Instant.parse("2026-08-01T10:00:00Z").getEpochSecond();
    private static final long T_2026_08_20 = Instant.parse("2026-08-20T10:00:00Z").getEpochSecond();
    private static final long T_2026_09_15 = Instant.parse("2026-09-15T10:00:00Z").getEpochSecond();
    private static final long T_2026_09_28 = Instant.parse("2026-09-28T10:00:00Z").getEpochSecond();

    private final ObjectMapper om = new ObjectMapper();

    @Mock
    private StripeReportClient client;
    @Mock
    private UserRepository userRepository;

    private StripeReportService service;

    @BeforeEach
    void setUp() {
        service = new StripeReportService(client, userRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private String sub(String id, String custId, String email, String country, String status, long created,
                       Long canceledAt, Long endedAt, Long trialEnd, boolean cancelAtPeriodEnd,
                       String productId, int unitAmount, String interval, String currency, boolean discount) {
        return """
            {"id":"%s","status":"%s","created":%d,"canceled_at":%s,"ended_at":%s,"trial_end":%s,
             "cancel_at_period_end":%s,"current_period_end":%d,"discount":%s,
             "customer":{"id":"%s","email":"%s","address":{"country":"%s"}},
             "items":{"data":[{"price":{"product":"%s","unit_amount":%d,"currency":"%s",
               "recurring":{"interval":"%s"},"nickname":null}}]}}
            """.formatted(id, status, created, canceledAt, endedAt, trialEnd, cancelAtPeriodEnd, T_2026_09_28,
                discount ? "{\"coupon\":{\"id\":\"REF\"}}" : "null",
                custId, email, country, productId, unitAmount, currency, interval);
    }

    private String invoice(String id, String custId, String email, String country, String status,
                           long created, int subtotal, int tax, int total, int amountPaid, String currency) {
        return """
            {"id":"%s","number":"INV-%s","customer":"%s","customer_email":"%s",
             "customer_address":{"country":"%s"},"status":"%s","created":%d,
             "subtotal":%d,"tax":%d,"total":%d,"amount_paid":%d,"currency":"%s",
             "hosted_invoice_url":"https://invoice.stripe.com/%s"}
            """.formatted(id, id, custId, email, country, status, created, subtotal, tax, total, amountPaid, currency, id);
    }

    private String txn(String type, long created, int amount, int fee, int net) {
        return """
            {"id":"txn_%d_%s","type":"%s","created":%d,"amount":%d,"fee":%d,"net":%d,"currency":"eur"}
            """.formatted(created, type, type, created, amount, fee, net);
    }

    private StripeRawData raw(List<String> subs, List<String> invoices, List<String> txns) throws Exception {
        List<JsonNode> s = new ArrayList<>();
        for (String x : subs) s.add(om.readTree(x));
        List<JsonNode> i = new ArrayList<>();
        for (String x : invoices) i.add(om.readTree(x));
        List<JsonNode> t = new ArrayList<>();
        for (String x : txns) t.add(om.readTree(x));
        List<JsonNode> products = List.of(
                om.readTree("{\"id\":\"prod_autosync\",\"name\":\"EV Monitor AutoSync\"}"),
                om.readTree("{\"id\":\"prod_supporter\",\"name\":\"EV Monitor Supporter\"}"));
        JsonNode balance = om.readTree(
                "{\"available\":[{\"amount\":12345,\"currency\":\"eur\"}],\"pending\":[{\"amount\":390,\"currency\":\"eur\"}]}");
        return new StripeRawData(s, i, t, products, balance);
    }

    private StripeRawData standardFixture() throws Exception {
        List<String> subs = List.of(
                // active monthly EUR 3.90, created Jun, had trial that converted
                sub("sub_a", "cus_a", "a@x.de", "DE", "active", T_2026_06_10, null, null,
                        T_2026_06_10 + 7 * 86400, false, "prod_autosync", 390, "month", "eur", true),
                // active yearly EUR 39, cancels at period end
                sub("sub_b", "cus_b", "b@x.at", "AT", "active", T_2026_07_05, null, null, null,
                        true, "prod_autosync", 3900, "year", "eur", false),
                // trialing right now, trial ends after NOW
                sub("sub_c", "cus_c", "c@x.de", "DE", "trialing", T_2026_09_15, null, null,
                        T_2026_09_28, false, "prod_autosync", 390, "month", "eur", false),
                // canceled during trial in August -> trial NOT converted
                sub("sub_d", "cus_d", "d@x.de", "DE", "canceled", T_2026_08_01, T_2026_08_01 + 3 * 86400,
                        T_2026_08_01 + 3 * 86400, T_2026_08_01 + 7 * 86400, false, "prod_autosync", 390, "month", "eur", false),
                // canceled after having paid (converted trial, then churned in August)
                sub("sub_e", "cus_e", "e@x.de", "DE", "canceled", T_2026_06_10, T_2026_08_20, T_2026_08_20,
                        T_2026_06_10 + 7 * 86400, false, "prod_autosync", 390, "month", "eur", false),
                // active USD supporter, must not count into EUR MRR
                sub("sub_f", "cus_f", "f@x.com", "US", "active", T_2026_08_01, null, null, null,
                        false, "prod_supporter", 200, "month", "usd", false)
        );
        List<String> invoices = List.of(
                invoice("in_1", "cus_a", "a@x.de", "DE", "paid", T_2026_07_05, 328, 62, 390, 390, "eur"),
                invoice("in_2", "cus_a", "a@x.de", "DE", "paid", T_2026_08_20, 328, 62, 390, 390, "eur"),
                invoice("in_3", "cus_b", "b@x.at", "AT", "paid", T_2026_07_05, 3250, 650, 3900, 3900, "eur"),
                invoice("in_4", "cus_e", "e@x.de", "DE", "paid", T_2026_07_05, 328, 62, 390, 390, "eur"),
                invoice("in_5", "cus_b", "b@x.at", "AT", "open", T_2026_09_15, 3250, 650, 3900, 0, "eur"),
                invoice("in_6", "cus_d", "d@x.de", "DE", "void", T_2026_08_01, 0, 0, 0, 0, "eur")
        );
        List<String> txns = List.of(
                txn("charge", T_2026_07_05, 390, 36, 354),
                txn("charge", T_2026_07_05, 3900, 122, 3778),
                txn("charge", T_2026_08_20, 390, 36, 354),
                txn("refund", T_2026_08_20, -390, 0, -390),
                txn("payout", T_2026_08_20, -4000, 0, -4000)
        );
        return raw(subs, invoices, txns);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void notConfigured_returnsEmptyReportWithFlag() {
        when(client.fetchAll()).thenReturn(Optional.empty());

        AdminStripeReport report = service.getReport(12, true);

        assertThat(report.configured()).isFalse();
        assertThat(report.subscriptions()).isEmpty();
    }

    @Test
    void mrr_countsOnlyEurActiveAndTrialing_yearlyDividedBy12() throws Exception {
        when(client.fetchAll()).thenReturn(Optional.of(standardFixture()));

        AdminStripeReport.Summary s = service.getReport(12, true).summary();

        // sub_a 3.90 + sub_b 39/12 + sub_c 3.90 (trial) = 11.05
        assertThat(s.mrrEur()).isCloseTo(11.05, within(0.001));
        assertThat(s.arrEur()).isCloseTo(132.6, within(0.001));
        assertThat(s.mrrByCurrency()).containsEntry("USD", 2.0);
    }

    @Test
    void subscriptionCounts_includeCancellingFlag() throws Exception {
        when(client.fetchAll()).thenReturn(Optional.of(standardFixture()));

        AdminStripeReport.Summary s = service.getReport(12, true).summary();

        assertThat(s.active()).isEqualTo(3);
        assertThat(s.activeCancelling()).isEqualTo(1);
        assertThat(s.trialing()).isEqualTo(1);
        assertThat(s.canceled()).isEqualTo(2);
    }

    @Test
    void trialFunnel_separatesConvertedFromCancelledTrials() throws Exception {
        when(client.fetchAll()).thenReturn(Optional.of(standardFixture()));

        AdminStripeReport.Summary s = service.getReport(12, true).summary();

        // ended trials: sub_a (converted), sub_d (cancelled in trial), sub_e (converted, later churned)
        // sub_c is still trialing -> not ended
        assertThat(s.trialsEnded()).isEqualTo(3);
        assertThat(s.trialsConverted()).isEqualTo(2);
        assertThat(s.trialsCancelled()).isEqualTo(1);
        assertThat(s.trialConversionRate()).isCloseTo(2.0 / 3.0, within(0.001));
    }

    @Test
    void churn_countsPaidCancellationsOnly_notTrialAbandons() throws Exception {
        when(client.fetchAll()).thenReturn(Optional.of(standardFixture()));

        AdminStripeReport.Summary s = service.getReport(12, true).summary();

        // sub_e churned after paying; sub_d cancelled during trial is not churn
        assertThat(s.churnedPaid()).isEqualTo(1);
    }

    @Test
    void revenue_fromBalanceTransactions_ignoresPayouts() throws Exception {
        when(client.fetchAll()).thenReturn(Optional.of(standardFixture()));

        AdminStripeReport report = service.getReport(12, true);
        AdminStripeReport.Summary s = report.summary();

        assertThat(s.grossRevenue()).isCloseTo(46.80, within(0.001));
        assertThat(s.fees()).isCloseTo(1.94, within(0.001));
        assertThat(s.netRevenue()).isCloseTo(44.86, within(0.001));
        assertThat(s.refunds()).isCloseTo(3.90, within(0.001));
        assertThat(s.balanceAvailable()).isCloseTo(123.45, within(0.001));
        assertThat(s.balancePending()).isCloseTo(3.90, within(0.001));

        AdminStripeReport.MonthRow aug = report.monthly().stream()
                .filter(m -> m.month().equals("2026-08")).findFirst().orElseThrow();
        assertThat(aug.gross()).isCloseTo(3.90, within(0.001));
        assertThat(aug.refunds()).isCloseTo(3.90, within(0.001));
        assertThat(aug.newSubscriptions()).isEqualTo(2);      // sub_d, sub_f
        assertThat(aug.canceledSubscriptions()).isEqualTo(2); // sub_d, sub_e
        assertThat(aug.trialsCancelled()).isEqualTo(1);       // sub_d
    }

    @Test
    void monthlyMrr_reconstructedFromSubscriptionLifetimes() throws Exception {
        when(client.fetchAll()).thenReturn(Optional.of(standardFixture()));

        AdminStripeReport report = service.getReport(12, true);

        AdminStripeReport.MonthRow jul = report.monthly().stream()
                .filter(m -> m.month().equals("2026-07")).findFirst().orElseThrow();
        // end of July: sub_a 3.90 + sub_b 3.25 + sub_e 3.90 = 11.05
        assertThat(jul.mrrEur()).isCloseTo(11.05, within(0.001));
        AdminStripeReport.MonthRow sep = report.monthly().stream()
                .filter(m -> m.month().equals("2026-09")).findFirst().orElseThrow();
        assertThat(sep.mrrEur()).isCloseTo(11.05, within(0.001));
    }

    @Test
    void monthsWindow_limitsMonthlyRowsAndRevenue() throws Exception {
        when(client.fetchAll()).thenReturn(Optional.of(standardFixture()));

        AdminStripeReport report = service.getReport(2, true);

        assertThat(report.monthly()).extracting(AdminStripeReport.MonthRow::month)
                .containsExactly("2026-08", "2026-09");
        assertThat(report.summary().grossRevenue()).isCloseTo(3.90, within(0.001));
    }

    @Test
    void byCountry_aggregatesPaidInvoicesWithTax() throws Exception {
        when(client.fetchAll()).thenReturn(Optional.of(standardFixture()));

        List<AdminStripeReport.CountryRow> rows = service.getReport(12, true).byCountry();

        AdminStripeReport.CountryRow de = rows.stream().filter(r -> r.country().equals("DE")).findFirst().orElseThrow();
        assertThat(de.invoiceCount()).isEqualTo(3);
        assertThat(de.net()).isCloseTo(9.84, within(0.001));
        assertThat(de.tax()).isCloseTo(1.86, within(0.001));
        assertThat(de.gross()).isCloseTo(11.70, within(0.001));
        AdminStripeReport.CountryRow at = rows.stream().filter(r -> r.country().equals("AT")).findFirst().orElseThrow();
        assertThat(at.invoiceCount()).isEqualTo(1);
    }

    @Test
    void byProduct_groupsByProductAndInterval() throws Exception {
        when(client.fetchAll()).thenReturn(Optional.of(standardFixture()));

        List<AdminStripeReport.ProductRow> rows = service.getReport(12, true).byProduct();

        AdminStripeReport.ProductRow monthly = rows.stream()
                .filter(r -> r.product().equals("EV Monitor AutoSync") && r.interval().equals("month"))
                .findFirst().orElseThrow();
        assertThat(monthly.count()).isEqualTo(2); // sub_a, sub_c (active+trialing only)
        assertThat(monthly.mrrEur()).isCloseTo(7.80, within(0.001));
    }

    @Test
    void subscriptionRows_totalPaidFromPaidInvoices_andAppUserMatched() throws Exception {
        when(client.fetchAll()).thenReturn(Optional.of(standardFixture()));
        User user = mock(User.class);
        when(user.getUsername()).thenReturn("max");
        when(userRepository.findByStripeCustomerId(any())).thenAnswer(inv ->
                "cus_a".equals(inv.getArgument(0)) ? Optional.of(user) : Optional.empty());

        List<AdminStripeReport.SubscriptionRow> rows = service.getReport(12, true).subscriptions();

        AdminStripeReport.SubscriptionRow a = rows.stream().filter(r -> r.customerId().equals("cus_a")).findFirst().orElseThrow();
        assertThat(a.totalPaid()).isCloseTo(7.80, within(0.001));
        assertThat(a.appUsername()).isEqualTo("max");
        assertThat(a.product()).isEqualTo("EV Monitor AutoSync");
        assertThat(a.hasDiscount()).isTrue();
        assertThat(a.country()).isEqualTo("DE");
        AdminStripeReport.SubscriptionRow b = rows.stream().filter(r -> r.customerId().equals("cus_b")).findFirst().orElseThrow();
        assertThat(b.cancelAtPeriodEnd()).isTrue();
        assertThat(b.totalPaid()).isCloseTo(39.0, within(0.001)); // open invoice not counted
        // newest first
        assertThat(rows.get(0).customerId()).isEqualTo("cus_c");
    }

    @Test
    void openInvoices_listsOpenAndPastDueOnly() throws Exception {
        when(client.fetchAll()).thenReturn(Optional.of(standardFixture()));

        AdminStripeReport report = service.getReport(12, true);

        assertThat(report.openInvoices()).extracting(AdminStripeReport.InvoiceRow::id).containsExactly("in_5");
        assertThat(report.invoices()).extracting(AdminStripeReport.InvoiceRow::id)
                .doesNotContain("in_6"); // void invoices are noise for accounting
    }

    @Test
    void cache_servesSecondCallWithoutRefetch() throws Exception {
        when(client.fetchAll()).thenReturn(Optional.of(standardFixture()));

        service.getReport(12, true);
        service.getReport(12, false);

        org.mockito.Mockito.verify(client, org.mockito.Mockito.times(1)).fetchAll();
    }
}
