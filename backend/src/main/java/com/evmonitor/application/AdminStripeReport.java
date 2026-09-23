package com.evmonitor.application;

import java.util.List;
import java.util.Map;

/**
 * Admin-only Stripe report: accounting view (revenue, fees, tax by country, invoices)
 * plus subscription business metrics (MRR, trial funnel, churn). All money values are
 * major units (EUR, not cents). Built entirely from Stripe data; nothing is persisted.
 */
public record AdminStripeReport(
        boolean configured,
        String generatedAt,
        int months,
        Summary summary,
        List<MonthRow> monthly,
        List<ProductRow> byProduct,
        List<CountryRow> byCountry,
        List<SubscriptionRow> subscriptions,
        List<InvoiceRow> invoices,
        List<InvoiceRow> openInvoices
) {

    public static AdminStripeReport notConfigured(String generatedAt, int months) {
        return new AdminStripeReport(false, generatedAt, months, Summary.empty(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public record Summary(
            double mrrEur,
            double arrEur,
            Map<String, Double> mrrByCurrency,
            int active,
            int activeCancelling,
            int trialing,
            int canceled,
            int trialsEnded,
            int trialsConverted,
            int trialsCancelled,
            Double trialConversionRate,
            int churnedPaid,
            Double churnRate,
            Double avgSubscriptionAgeDays,
            int withReferralDiscount,
            double balanceAvailable,
            double balancePending,
            double grossRevenue,
            double fees,
            double netRevenue,
            double refunds,
            double totalPaidAllTime
    ) {
        static Summary empty() {
            return new Summary(0, 0, Map.of(), 0, 0, 0, 0, 0, 0, 0, null, 0, null, null, 0,
                    0, 0, 0, 0, 0, 0, 0);
        }
    }

    /** One calendar month, key "yyyy-MM". */
    public record MonthRow(
            String month,
            double gross,
            double fees,
            double net,
            double refunds,
            double mrrEur,
            int newSubscriptions,
            int canceledSubscriptions,
            int trialsCancelled
    ) {}

    public record ProductRow(String product, String interval, int count, double mrrEur, String currency) {}

    public record CountryRow(String country, int invoiceCount, double net, double tax, double gross, String currency) {}

    public record SubscriptionRow(
            String id,
            String customerId,
            String email,
            String appUsername,
            String status,
            boolean cancelAtPeriodEnd,
            String trialEnd,
            String canceledAt,
            String product,
            String interval,
            double amount,
            String currency,
            double totalPaid,
            String createdAt,
            String currentPeriodEnd,
            String country,
            boolean hasDiscount
    ) {}

    public record InvoiceRow(
            String id,
            String number,
            String date,
            String email,
            String country,
            double subtotal,
            double tax,
            double total,
            double amountPaid,
            String currency,
            String status,
            String hostedUrl
    ) {}
}
