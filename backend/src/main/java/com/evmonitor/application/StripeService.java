package com.evmonitor.application;

import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import com.stripe.param.CustomerBalanceTransactionCollectionCreateParams;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class StripeService {

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${stripe.price-id-monthly:}")
    private String priceIdMonthly;

    @Value("${stripe.price-id-yearly:}")
    private String priceIdYearly;

    @Value("${stripe.price-id-monthly-intl:}")
    private String priceIdMonthlyIntl;

    @Value("${stripe.price-id-yearly-intl:}")
    private String priceIdYearlyIntl;

    @Value("${stripe.price-id-monthly-usd:}")
    private String priceIdMonthlyUsd;

    @Value("${stripe.price-id-yearly-usd:}")
    private String priceIdYearlyUsd;

    @Value("${stripe.price-id-monthly-gbp:}")
    private String priceIdMonthlyGbp;

    @Value("${stripe.price-id-yearly-gbp:}")
    private String priceIdYearlyGbp;

    @Value("${stripe.price-id-monthly-nok:}")
    private String priceIdMonthlyNok;

    @Value("${stripe.price-id-yearly-nok:}")
    private String priceIdYearlyNok;

    @Value("${stripe.price-id-monthly-sek:}")
    private String priceIdMonthlySek;

    @Value("${stripe.price-id-yearly-sek:}")
    private String priceIdYearlySek;

    @Value("${stripe.price-id-monthly-dkk:}")
    private String priceIdMonthlyDkk;

    @Value("${stripe.price-id-yearly-dkk:}")
    private String priceIdYearlyDkk;

    @Value("${stripe.referral-coupon-id:}")
    private String referralCouponId;

    @Value("${stripe.referral-reward-cents:390}")
    private long referralRewardCents;

    @Value("${connectors.base-url:http://connectors-service:8081}")
    private String connectorsBaseUrl;

    @Value("${internal.token:}")
    private String internalToken;

    private final RestTemplate restTemplate = buildRestTemplate();
    private final UserRepository userRepository;

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }

    public StripeService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        if (secretKey != null && !secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
        }
    }

    /**
     * Creates a Stripe Checkout Session for the given user and plan.
     * Lazily creates a Stripe Customer if the user doesn't have one yet.
     *
     * @param user       the authenticated user
     * @param plan       "monthly" or "yearly"
     * @param successUrl URL to redirect to on success
     * @param cancelUrl  URL to redirect to on cancel
     * @return the Stripe Checkout URL
     */
    public String createCheckoutSession(User user, String plan, String successUrl, String cancelUrl) throws StripeException {
        boolean eligibleForTrial = !user.isTrialUsed();
        String customerId = ensureCustomer(user);

        String priceId = resolvePriceId(plan, user.getCountry());

        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build())
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl);

        if (eligibleForTrial) {
            builder.setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                    .setTrialPeriodDays(7L)
                    .build());
        }

        // Referral discount: only for monthly plan.
        // A "100% off, once" coupon on a yearly subscription would waive the full
        // annual price (~€45) instead of one month (~€3.99) — never apply to yearly.
        if ("monthly".equals(plan) && user.getReferredByUserId() != null && !referralCouponId.isBlank()) {
            builder.addDiscount(SessionCreateParams.Discount.builder()
                    .setCoupon(referralCouponId)
                    .build());
        }

        Session session = Session.create(builder.build());
        return session.getUrl();
    }

    /**
     * Verifies the Stripe webhook signature and dispatches the event.
     * Uses raw JSON parsing to stay compatible across all Stripe API versions —
     * the SDK's EventDataObjectDeserializer silently returns empty when the webhook
     * API version is newer than the SDK was built against.
     */
    public void handleWebhookEvent(String payload, String sigHeader) throws SignatureVerificationException {
        Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

        JsonObject data = JsonParser.parseString(payload)
                .getAsJsonObject()
                .getAsJsonObject("data")
                .getAsJsonObject("object");

        dispatch(event.getType(), data);
    }

    /**
     * Dispatches a validated Stripe event to the appropriate handler.
     * Package-private to allow unit testing without a real Stripe signature.
     */
    void dispatch(String eventType, JsonObject data) {
        switch (eventType) {
            case "customer.subscription.created", "customer.subscription.updated" -> {
                String customerId = data.get("customer").getAsString();
                String status = data.get("status").getAsString();
                boolean isTrialing = "trialing".equals(status);
                boolean isActive = "active".equals(status) || isTrialing;
                Instant periodEnd = data.has("current_period_end") && !data.get("current_period_end").isJsonNull()
                        ? Instant.ofEpochSecond(data.get("current_period_end").getAsLong())
                        : null;
                findUserByCustomerId(customerId).ifPresent(u -> {
                    boolean wasPremium = u.isPremium();
                    userRepository.setPremium(u.getId(), isActive);
                    if (periodEnd != null) {
                        userRepository.setSubscriptionPeriodEnd(u.getId(), periodEnd);
                    }
                    if (isTrialing) {
                        userRepository.markTrialUsed(u.getId());
                    }
                    // Status transitioned away from active/trialing (e.g. canceled, past_due,
                    // incomplete_expired, unpaid) → Smartcar billing must stop, Tesla telemetry off.
                    if (!isActive) {
                        disconnectSmartcar(u.getId());
                        disableTeslaTelemetry(u.getId());
                    } else if (!wasPremium) {
                        // Transition inactive→active: AutoSync purchase. Kick off Tesla telemetry
                        // auto-enable for users with a paired Tesla. No-op for non-Tesla users
                        // (connectors-service returns 404, treated as benign).
                        enableTeslaTelemetry(u.getId());
                    }
                });
                log.info("[STRIPE] subscription {} -> isPremium={} for customer={}", status, isActive, customerId);
            }
            case "customer.subscription.deleted" -> {
                String customerId = data.get("customer").getAsString();
                findUserByCustomerId(customerId).ifPresent(u -> {
                    userRepository.setPremium(u.getId(), false);
                    disconnectSmartcar(u.getId());
                    disableTeslaTelemetry(u.getId());
                });
                log.info("[STRIPE] subscription deleted -> isPremium=false for customer={}", customerId);
            }
            case "invoice.payment_failed" -> {
                // Revoke premium AND disconnect Smartcar immediately. We previously kept Smartcar
                // connected during Stripe's dunning window for "seamless recovery", but Stripe
                // retries up to 21 days — that's $1.66 of Smartcar billing per failed payment we
                // don't want to eat. If the user recovers later, they re-OAuth with one click.
                // The 6h reconciler is the backstop in case this fast-path itself fails.
                String customerId = data.get("customer").getAsString();
                findUserByCustomerId(customerId).ifPresentOrElse(
                        u -> {
                            userRepository.setPremium(u.getId(), false);
                            disconnectSmartcar(u.getId());
                            disableTeslaTelemetry(u.getId());
                            log.warn("[STRIPE] payment failed for customer={} userId={} - premium revoked, Smartcar disconnected, Tesla telemetry stopped", customerId, u.getId());
                        },
                        () -> log.warn("[STRIPE] payment failed for unknown customer={} — no action taken", customerId)
                );
            }
            case "invoice.payment_succeeded" -> {
                long amountPaid = data.get("amount_paid").getAsLong();
                // Only trigger on invoices with actual payment (not the free referral month, amount=0).
                if (amountPaid > 0) {
                    String customerId = data.get("customer").getAsString();
                    findUserByCustomerId(customerId).ifPresent(u -> {
                        if (u.getReferredByUserId() == null) return;

                        // Atomic DB claim BEFORE any Stripe call.
                        // Uses UPDATE WHERE referral_reward_given = false — only one concurrent
                        // webhook delivery can win this race. The loser gets false and exits.
                        // If the subsequent Stripe call fails, the reward is permanently lost for
                        // this user (no retry possible). This is intentional: under-rewarding is
                        // always preferable to double-crediting real money.
                        boolean claimed = userRepository.claimReferralReward(u.getId());
                        if (!claimed) {
                            log.debug("Referral reward already claimed for user {}, skipping", u.getId());
                            return;
                        }

                        userRepository.findById(u.getReferredByUserId()).ifPresent(referrer -> {
                            try {
                                String referrerCustomerId = ensureCustomer(referrer);
                                Customer referrerCustomer = Customer.retrieve(referrerCustomerId);
                                CustomerBalanceTransactionCollectionCreateParams params = CustomerBalanceTransactionCollectionCreateParams.builder()
                                        .setAmount(-referralRewardCents)
                                        .setCurrency("eur")
                                        .setDescription("Referral reward: friend subscribed")
                                        .build();
                                referrerCustomer.balanceTransactions().create(params);
                                log.info("Referral reward credited: referrer={} for referred user={}",
                                        referrer.getId(), u.getId());
                            } catch (StripeException e) {
                                // DB flag is already set to true — this reward is permanently lost.
                                // Manual recovery required: check logs and credit referrer manually.
                                log.error("REFERRAL_REWARD_FAILED: Stripe credit failed for referrer={} (referred={}). " +
                                        "DB flag already set. Manual credit required.", referrer.getId(), u.getId(), e);
                            }
                        });
                    });
                }
            }
            default -> {
                // Unhandled event type — ignore silently
            }
        }
    }

    /**
     * Creates a Stripe Billing Portal session for self-service subscription management.
     */
    public String createPortalSession(User user, String returnUrl) throws StripeException {
        String customerId = ensureCustomer(user);
        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(customerId)
                        .setReturnUrl(returnUrl)
                        .build();
        com.stripe.model.billingportal.Session session =
                com.stripe.model.billingportal.Session.create(params);
        return session.getUrl();
    }

    String resolvePriceId(String plan, String country) {
        boolean yearly = "yearly".equals(plan);
        if (country == null) country = "DE";
        return switch (country) {
            case "DE", "AT", "CH" -> yearly ? priceIdYearly      : priceIdMonthly;
            case "US"             -> yearly ? priceIdYearlyUsd    : priceIdMonthlyUsd;
            case "GB"             -> yearly ? priceIdYearlyGbp    : priceIdMonthlyGbp;
            case "NO"             -> yearly ? priceIdYearlyNok    : priceIdMonthlyNok;
            case "SE"             -> yearly ? priceIdYearlySek    : priceIdMonthlySek;
            case "DK"             -> yearly ? priceIdYearlyDkk    : priceIdMonthlyDkk;
            default               -> yearly ? priceIdYearlyIntl   : priceIdMonthlyIntl;
        };
    }

    private String ensureCustomer(User user) throws StripeException {
        if (user.getStripeCustomerId() != null && !user.getStripeCustomerId().isBlank()) {
            return user.getStripeCustomerId();
        }

        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .setName(user.getUsername())
                .putMetadata("userId", user.getId().toString())
                .build();

        Customer customer = Customer.create(params);
        userRepository.setStripeCustomerId(user.getId(), customer.getId());
        return customer.getId();
    }

    private Optional<User> findUserByCustomerId(String customerId) {
        return userRepository.findByStripeCustomerId(customerId);
    }

    /**
     * Auto-activate Tesla fleet telemetry on AutoSync purchase. Fires only on
     * inactive→active transitions; idempotent so duplicate webhooks are safe.
     * 404 from connectors-service means the user has no Tesla connection -
     * common for non-Tesla Premium users, treated as benign.
     */
    private void enableTeslaTelemetry(UUID userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", internalToken);
            restTemplate.exchange(
                    connectorsBaseUrl + "/api/internal/tesla/" + userId + "/enable-telemetry",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    Void.class);
            log.info("[STRIPE] Tesla telemetry enable triggered for userId={}", userId);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            log.debug("[STRIPE] Tesla telemetry enable skipped for userId={} - no Tesla connection", userId);
        } catch (Exception e) {
            log.warn("[STRIPE] Tesla telemetry enable failed for userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * Auto-deactivate Tesla fleet telemetry when entitlement is lost.
     * Re-reads entitlement before the call so privileged roles (ADMIN, BETA_TESTER,
     * TESLA_FOUNDER) keep telemetry running, and a parallel re-subscription that
     * flipped is_premium=true wins the race.
     */
    private void disableTeslaTelemetry(UUID userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("[STRIPE] Tesla telemetry disable skipped: user {} not found", userId);
            return;
        }
        User user = userOpt.get();
        String role = user.getRole();
        boolean privilegedRole = "ADMIN".equals(role) || "BETA_TESTER".equals(role) || "TESLA_FOUNDER".equals(role);
        if (user.isPremium() || privilegedRole) {
            log.info("[STRIPE] Tesla telemetry disable skipped for userId={} - still entitled (premium={}, role={})",
                    userId, user.isPremium(), role);
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", internalToken);
            restTemplate.exchange(
                    connectorsBaseUrl + "/api/internal/tesla/" + userId + "/disable-telemetry",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    Void.class);
            log.info("[STRIPE] Tesla telemetry disabled for userId={}", userId);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            log.debug("[STRIPE] Tesla telemetry disable skipped for userId={} - no Tesla connection", userId);
        } catch (Exception e) {
            log.warn("[STRIPE] Tesla telemetry disable failed for userId={}: {}", userId, e.getMessage());
        }
    }

    private void disconnectSmartcar(UUID userId) {
        // Re-read entitlement before firing the HTTP call. Two reasons:
        //   1) Privileged roles (ADMIN, BETA_TESTER) keep Smartcar even after Stripe failure.
        //   2) Race: a fresh subscription.created from a new subscription may have already
        //      restored is_premium=true between our setPremium(false) and now. If so, skip.
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("[STRIPE] Smartcar disconnect skipped: user {} not found", userId);
            return;
        }
        User user = userOpt.get();
        boolean privilegedRole = "ADMIN".equals(user.getRole()) || "BETA_TESTER".equals(user.getRole());
        if (user.isPremium() || privilegedRole) {
            log.info("[STRIPE] Smartcar disconnect skipped for userId={} - still entitled (premium={}, role={})",
                    userId, user.isPremium(), user.getRole());
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", internalToken);
            restTemplate.exchange(
                    connectorsBaseUrl + "/api/internal/smartcar/disconnect/" + userId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class);
            log.info("[STRIPE] Smartcar disconnected for userId={}", userId);
        } catch (Exception e) {
            log.warn("[STRIPE] Smartcar disconnect failed for userId={}: {}", userId, e.getMessage());
        }
    }
}
