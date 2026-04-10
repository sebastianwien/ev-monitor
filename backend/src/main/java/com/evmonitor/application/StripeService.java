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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${stripe.price-id-monthly:}")
    private String priceIdMonthly;

    @Value("${stripe.price-id-yearly:}")
    private String priceIdYearly;

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

        String priceId = "yearly".equals(plan) ? priceIdYearly : priceIdMonthly;

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
                    userRepository.setPremium(u.getId(), isActive);
                    if (periodEnd != null) {
                        userRepository.setSubscriptionPeriodEnd(u.getId(), periodEnd);
                    }
                    if (isTrialing) {
                        userRepository.markTrialUsed(u.getId());
                    }
                });
                log.info("[STRIPE] subscription {} -> isPremium={} for customer={}", status, isActive, customerId);
            }
            case "customer.subscription.deleted" -> {
                String customerId = data.get("customer").getAsString();
                findUserByCustomerId(customerId).ifPresent(u -> {
                    userRepository.setPremium(u.getId(), false);
                    disconnectSmartcar(u.getId());
                });
                log.info("[STRIPE] subscription deleted -> isPremium=false for customer={}", customerId);
            }
            case "invoice.payment_failed" -> {
                // Do NOT revoke premium immediately — Stripe retries the payment.
                // Access is revoked naturally when the subscription transitions to
                // past_due (via customer.subscription.updated) or is deleted.
                String customerId = data.get("customer").getAsString();
                log.warn("[STRIPE] payment failed for customer={} — Stripe will retry", customerId);
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

    private void disconnectSmartcar(UUID userId) {
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
