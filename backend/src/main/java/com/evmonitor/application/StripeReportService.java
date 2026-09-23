package com.evmonitor.application;

import com.evmonitor.application.AdminStripeReport.CountryRow;
import com.evmonitor.application.AdminStripeReport.InvoiceRow;
import com.evmonitor.application.AdminStripeReport.MonthRow;
import com.evmonitor.application.AdminStripeReport.ProductRow;
import com.evmonitor.application.AdminStripeReport.SubscriptionRow;
import com.evmonitor.application.AdminStripeReport.Summary;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.external.StripeReportClient;
import com.evmonitor.infrastructure.external.StripeReportClient.StripeRawData;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Builds the admin Stripe report from raw Stripe JSON. Pure aggregation over
 * {@link StripeRawData}; the only side channel is a 10-minute in-memory cache
 * of the raw fetch so tab switches do not hammer Stripe.
 */
@Service
public class StripeReportService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int MAX_MONTHS = 60;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final StripeReportClient client;
    private final UserRepository userRepository;
    private final Clock clock;

    private Optional<StripeRawData> cached = Optional.empty();
    private Instant cachedAt = Instant.EPOCH;

    @Autowired
    public StripeReportService(StripeReportClient client, UserRepository userRepository) {
        this(client, userRepository, Clock.systemUTC());
    }

    StripeReportService(StripeReportClient client, UserRepository userRepository, Clock clock) {
        this.client = client;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * @param months  window for monthly series and revenue totals; 0 = all time. Clamped to 0..60.
     * @param refresh bypass the raw-data cache
     */
    public synchronized AdminStripeReport getReport(int months, boolean refresh) {
        int window = Math.max(0, Math.min(MAX_MONTHS, months));
        Instant now = clock.instant();
        if (refresh || cached.isEmpty() || cachedAt.plus(CACHE_TTL).isBefore(now)) {
            cached = client.fetchAll();
            cachedAt = now;
        }
        return cached.map(raw -> build(raw, window, now))
                .orElseGet(() -> AdminStripeReport.notConfigured(now.toString(), window));
    }

    // ── Aggregation ───────────────────────────────────────────────────────────

    AdminStripeReport build(StripeRawData raw, int months, Instant now) {
        Map<String, String> productNames = new HashMap<>();
        for (JsonNode p : raw.products()) {
            productNames.put(p.path("id").asText(), p.path("name").asText(p.path("id").asText()));
        }

        List<Sub> subs = raw.subscriptions().stream().map(n -> Sub.of(n, productNames)).toList();
        List<Inv> invoices = raw.invoices().stream().map(Inv::of).toList();

        YearMonth currentMonth = YearMonth.from(now.atZone(ZoneOffset.UTC));
        YearMonth firstMonth = resolveFirstMonth(months, currentMonth, subs, invoices, raw.balanceTransactions());
        Instant windowStart = firstMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // Paid totals per customer (all time) and revenue by country (window)
        Map<String, Double> paidByCustomer = new HashMap<>();
        Map<String, double[]> byCountry = new TreeMap<>();
        Map<String, String> countryCurrency = new HashMap<>();
        double totalPaidAllTime = 0;
        for (Inv inv : invoices) {
            if (!"paid".equals(inv.status)) continue;
            paidByCustomer.merge(inv.customerId, inv.amountPaid, Double::sum);
            totalPaidAllTime += inv.amountPaid;
            if (inv.created.isBefore(windowStart)) continue;
            double[] acc = byCountry.computeIfAbsent(inv.country, k -> new double[4]);
            acc[0] += 1;
            acc[1] += inv.subtotal;
            acc[2] += inv.tax;
            acc[3] += inv.total;
            countryCurrency.putIfAbsent(inv.country, inv.currency);
        }

        // Monthly buckets
        LinkedHashMap<String, double[]> money = new LinkedHashMap<>(); // gross, fees, net, refunds
        LinkedHashMap<String, int[]> counts = new LinkedHashMap<>();   // new, canceled, trialsCancelled
        for (YearMonth m = firstMonth; !m.isAfter(currentMonth); m = m.plusMonths(1)) {
            money.put(m.toString(), new double[4]);
            counts.put(m.toString(), new int[3]);
        }

        double gross = 0, fees = 0, net = 0, refunds = 0;
        for (JsonNode t : raw.balanceTransactions()) {
            Instant created = Instant.ofEpochSecond(t.path("created").asLong());
            if (created.isBefore(windowStart)) continue;
            String type = t.path("type").asText();
            double[] acc = money.get(monthKey(created));
            if (acc == null) continue;
            double amount = cents(t.path("amount"));
            switch (type) {
                case "charge", "payment" -> {
                    acc[0] += amount;
                    acc[1] += cents(t.path("fee"));
                    acc[2] += cents(t.path("net"));
                    gross += amount;
                    fees += cents(t.path("fee"));
                    net += cents(t.path("net"));
                }
                case "refund", "payment_refund" -> {
                    acc[3] += -amount;
                    refunds += -amount;
                }
                default -> { /* payouts, adjustments: not revenue */ }
            }
        }

        // Subscription metrics
        int active = 0, activeCancelling = 0, trialing = 0, canceled = 0;
        int trialsEnded = 0, trialsConverted = 0, trialsCancelled = 0, churnedPaid = 0, withDiscount = 0;
        double mrrEur = 0;
        Map<String, Double> mrrByCurrency = new TreeMap<>();
        Map<String, ProductAcc> byProduct = new LinkedHashMap<>();
        long ageSum = 0;
        int ageCount = 0;
        int activeAtWindowStart = 0;

        for (Sub s : subs) {
            switch (s.status) {
                case "active" -> {
                    active++;
                    if (s.cancelAtPeriodEnd) activeCancelling++;
                }
                case "trialing" -> trialing++;
                case "canceled" -> canceled++;
                default -> { }
            }
            boolean live = "active".equals(s.status) || "trialing".equals(s.status);
            if (live) {
                if ("EUR".equals(s.currency)) mrrEur += s.monthlyAmount;
                mrrByCurrency.merge(s.currency, s.monthlyAmount, Double::sum);
                ageSum += Duration.between(s.created, now).toDays();
                ageCount++;
                byProduct.computeIfAbsent(s.product + "|" + s.interval + "|" + s.currency,
                        k -> new ProductAcc(s.product, s.interval, s.currency)).add(s.monthlyAmount);
            }
            if (s.hasDiscount) withDiscount++;

            boolean trialOver = s.trialEnd != null && s.trialEnd.isBefore(now);
            if (trialOver) {
                trialsEnded++;
                boolean cancelledInTrial = s.ended != null && !s.ended.isAfter(s.trialEnd);
                if (cancelledInTrial) trialsCancelled++; else trialsConverted++;
            }
            if (s.ended != null) {
                boolean cancelledInTrial = s.trialEnd != null && !s.ended.isAfter(s.trialEnd);
                if (!cancelledInTrial && !s.ended.isBefore(windowStart)) churnedPaid++;
                int[] c = counts.get(monthKey(s.ended));
                if (c != null) {
                    c[1]++;
                    if (cancelledInTrial) c[2]++;
                }
            }
            if (!s.created.isBefore(windowStart)) {
                int[] c = counts.get(monthKey(s.created));
                if (c != null) c[0]++;
            }
            if (s.created.isBefore(windowStart) && (s.ended == null || !s.ended.isBefore(windowStart))) {
                activeAtWindowStart++;
            }
        }

        List<MonthRow> monthly = new ArrayList<>();
        for (String key : money.keySet()) {
            YearMonth ym = YearMonth.parse(key);
            Instant monthEnd = ym.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            double mrrAtMonthEnd = subs.stream()
                    .filter(s -> "EUR".equals(s.currency))
                    .filter(s -> s.created.isBefore(monthEnd) && (s.ended == null || !s.ended.isBefore(monthEnd)))
                    .mapToDouble(s -> s.monthlyAmount).sum();
            double[] m = money.get(key);
            int[] c = counts.get(key);
            monthly.add(new MonthRow(key, r2(m[0]), r2(m[1]), r2(m[2]), r2(m[3]), r2(mrrAtMonthEnd), c[0], c[1], c[2]));
        }

        List<SubscriptionRow> subRows = subs.stream()
                .sorted(Comparator.comparing((Sub s) -> s.created).reversed())
                .map(s -> new SubscriptionRow(
                        s.id, s.customerId, s.email,
                        userRepository.findByStripeCustomerId(s.customerId).map(u -> u.getUsername()).orElse(null),
                        s.status, s.cancelAtPeriodEnd, date(s.trialEnd), date(s.canceledAt),
                        s.product, s.interval, s.amount, s.currency,
                        r2(paidByCustomer.getOrDefault(s.customerId, 0.0)),
                        date(s.created), date(s.currentPeriodEnd), s.country, s.hasDiscount))
                .toList();

        List<InvoiceRow> invoiceRows = invoices.stream()
                .filter(i -> !"void".equals(i.status) && !"draft".equals(i.status))
                .filter(i -> !i.created.isBefore(windowStart))
                .sorted(Comparator.comparing((Inv i) -> i.created).reversed())
                .map(Inv::toRow)
                .toList();
        List<InvoiceRow> openRows = invoices.stream()
                .filter(i -> "open".equals(i.status) || "past_due".equals(i.status) || "uncollectible".equals(i.status))
                .sorted(Comparator.comparing((Inv i) -> i.created).reversed())
                .map(Inv::toRow)
                .toList();

        List<CountryRow> countryRows = new ArrayList<>();
        byCountry.forEach((country, acc) -> countryRows.add(new CountryRow(
                country, (int) acc[0], r2(acc[1]), r2(acc[2]), r2(acc[3]), countryCurrency.get(country))));

        Summary summary = new Summary(
                r2(mrrEur), r2(mrrEur * 12), roundMap(mrrByCurrency),
                active, activeCancelling, trialing, canceled,
                trialsEnded, trialsConverted, trialsCancelled,
                trialsEnded == 0 ? null : (double) trialsConverted / trialsEnded,
                churnedPaid,
                activeAtWindowStart == 0 ? null : (double) churnedPaid / activeAtWindowStart,
                ageCount == 0 ? null : (double) ageSum / ageCount,
                withDiscount,
                cents(raw.balance().path("available").path(0).path("amount")),
                cents(raw.balance().path("pending").path(0).path("amount")),
                r2(gross), r2(fees), r2(net), r2(refunds), r2(totalPaidAllTime));

        return new AdminStripeReport(true, now.toString(), months, summary, monthly,
                byProduct.values().stream().map(ProductAcc::toRow).toList(),
                countryRows, subRows, invoiceRows, openRows);
    }

    private static YearMonth resolveFirstMonth(int months, YearMonth current, List<Sub> subs, List<Inv> invoices,
                                               List<JsonNode> txns) {
        if (months > 0) return current.minusMonths(months - 1L);
        Instant earliest = Instant.MAX;
        for (Sub s : subs) if (s.created.isBefore(earliest)) earliest = s.created;
        for (Inv i : invoices) if (i.created.isBefore(earliest)) earliest = i.created;
        for (JsonNode t : txns) {
            Instant c = Instant.ofEpochSecond(t.path("created").asLong());
            if (c.isBefore(earliest)) earliest = c;
        }
        if (earliest.equals(Instant.MAX)) return current;
        return YearMonth.from(earliest.atZone(ZoneOffset.UTC));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String monthKey(Instant i) {
        return YearMonth.from(i.atZone(ZoneOffset.UTC)).toString();
    }

    private static String date(Instant i) {
        return i == null ? null : LocalDate.ofInstant(i, ZoneOffset.UTC).format(DATE);
    }

    private static Instant epoch(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? null : Instant.ofEpochSecond(n.asLong());
    }

    private static double cents(JsonNode n) {
        return n.asLong(0) / 100.0;
    }

    private static double r2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static Map<String, Double> roundMap(Map<String, Double> m) {
        Map<String, Double> out = new TreeMap<>();
        m.forEach((k, v) -> out.put(k, r2(v)));
        return out;
    }

    private static final class ProductAcc {
        final String product, interval, currency;
        int count;
        double mrr;

        ProductAcc(String product, String interval, String currency) {
            this.product = product;
            this.interval = interval;
            this.currency = currency;
        }

        void add(double monthly) {
            count++;
            mrr += monthly;
        }

        ProductRow toRow() {
            return new ProductRow(product, interval, count, r2(mrr), currency);
        }
    }

    private record Sub(String id, String customerId, String email, String country, String status,
                       Instant created, Instant canceledAt, Instant ended, Instant trialEnd, Instant currentPeriodEnd,
                       boolean cancelAtPeriodEnd, boolean hasDiscount, String product, String interval,
                       double amount, double monthlyAmount, String currency) {

        static Sub of(JsonNode n, Map<String, String> productNames) {
            JsonNode cust = n.path("customer");
            String customerId = cust.isObject() ? cust.path("id").asText() : cust.asText(null);
            String email = cust.isObject() ? cust.path("email").asText(null) : null;
            String country = cust.isObject() ? cust.path("address").path("country").asText(null) : null;

            JsonNode price = n.path("items").path("data").path(0).path("price");
            JsonNode prodNode = price.path("product");
            String prodId = prodNode.isObject() ? prodNode.path("id").asText() : prodNode.asText("?");
            String product = productNames.getOrDefault(prodId, prodId);
            String nickname = price.path("nickname").asText(null);
            if (nickname != null && !nickname.isBlank()) product = product + " " + nickname;
            String interval = price.path("recurring").path("interval").asText("");
            double amount = cents(price.path("unit_amount"));
            double monthly = switch (interval) {
                case "year" -> amount / 12;
                case "week" -> amount * 52 / 12;
                case "day" -> amount * 365 / 12;
                default -> amount;
            };
            Instant canceledAt = epoch(n.get("canceled_at"));
            Instant ended = epoch(n.get("ended_at"));
            if (ended == null && "canceled".equals(n.path("status").asText())) ended = canceledAt;
            JsonNode discount = n.get("discount");
            return new Sub(
                    n.path("id").asText(), customerId, email, country, n.path("status").asText(),
                    Instant.ofEpochSecond(n.path("created").asLong()), canceledAt, ended,
                    epoch(n.get("trial_end")), epoch(n.get("current_period_end")),
                    n.path("cancel_at_period_end").asBoolean(false),
                    discount != null && discount.isObject(),
                    product, interval, amount, monthly,
                    price.path("currency").asText("eur").toUpperCase());
        }
    }

    private record Inv(String id, String number, String customerId, String email, String country, String status,
                       Instant created, double subtotal, double tax, double total, double amountPaid,
                       String currency, String hostedUrl) {

        static Inv of(JsonNode n) {
            JsonNode cust = n.path("customer");
            String country = n.path("customer_address").path("country").asText(null);
            if (country == null) country = n.path("customer_shipping").path("address").path("country").asText("?");
            return new Inv(
                    n.path("id").asText(), n.path("number").asText(null),
                    cust.isObject() ? cust.path("id").asText() : cust.asText(null),
                    n.path("customer_email").asText(null), country, n.path("status").asText(),
                    Instant.ofEpochSecond(n.path("created").asLong()),
                    cents(n.path("subtotal")), cents(n.path("tax")), cents(n.path("total")), cents(n.path("amount_paid")),
                    n.path("currency").asText("eur").toUpperCase(),
                    n.path("hosted_invoice_url").asText(null));
        }

        InvoiceRow toRow() {
            return new InvoiceRow(id, number, date(created), email, country, subtotal, tax, total, amountPaid,
                    currency, status, hostedUrl);
        }
    }
}
