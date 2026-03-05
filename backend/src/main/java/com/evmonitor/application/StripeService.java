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
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class StripeService {

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${stripe.price-id-monthly:}")
    private String priceIdMonthly;

    @Value("${stripe.price-id-yearly:}")
    private String priceIdYearly;

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

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build())
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .build();

        Session session = Session.create(params);
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
