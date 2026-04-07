package com.evmonitor.application;

import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerBalanceTransactionCollectionCreateParams;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

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

    @Value("${stripe.referral-reward-cents:399}")
    private long referralRewardCents;

    private final UserRepository userRepository;

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
     */
    public void handleWebhookEvent(String payload, String sigHeader) throws SignatureVerificationException {
        Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        Optional<StripeObject> stripeObjectOpt = deserializer.getObject();

        if (stripeObjectOpt.isEmpty()) {
            return;
        }

        StripeObject obj = stripeObjectOpt.get();

        switch (event.getType()) {
            case "customer.subscription.created", "customer.subscription.updated" -> {
                if (obj instanceof Subscription sub) {
                    boolean isActive = "active".equals(sub.getStatus());
                    findUserByCustomerId(sub.getCustomer())
                            .ifPresent(u -> userRepository.setPremium(u.getId(), isActive));
                }
            }
            case "customer.subscription.deleted" -> {
                if (obj instanceof Subscription sub) {
                    findUserByCustomerId(sub.getCustomer())
                            .ifPresent(u -> userRepository.setPremium(u.getId(), false));
                }
            }
            case "invoice.payment_failed" -> {
                if (obj instanceof Invoice invoice) {
                    findUserByCustomerId(invoice.getCustomer())
                            .ifPresent(u -> userRepository.setPremium(u.getId(), false));
                }
            }
            case "invoice.payment_succeeded" -> {
                // Only trigger on invoices with actual payment (not the free referral month, amount=0).
                if (obj instanceof Invoice invoice && invoice.getAmountPaid() > 0) {
                    findUserByCustomerId(invoice.getCustomer()).ifPresent(u -> {
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
}
