package com.evmonitor.application;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.param.SubscriptionListParams;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Fragt Stripe nach den laufenden Abos eines Kunden. Eigene Klasse, weil das Stripe-SDK statisch ist
 * und sich im {@link StripeService} sonst nicht testen lässt.
 */
@Component
public class StripeSubscriptionLookup {

    /**
     * Preis-IDs aller aktiven oder trialenden Abos des Kunden, ohne das genannte Abo.
     *
     * @throws StripeLookupException Stripe nicht erreichbar; der Webhook scheitert dann und Stripe wiederholt ihn
     */
    /** Preis-IDs aller aktiven oder trialenden Abos des Kunden. */
    public List<String> activePriceIds(String customerId) {
        return activePriceIdsExcept(customerId, null);
    }

    public List<String> activePriceIdsExcept(String customerId, @Nullable String excludedSubscriptionId) {
        SubscriptionListParams params = SubscriptionListParams.builder()
                .setCustomer(customerId)
                .setLimit(20L)
                .build();
        try {
            return Subscription.list(params).getData().stream()
                    .filter(s -> !s.getId().equals(excludedSubscriptionId))
                    .filter(s -> "active".equals(s.getStatus()) || "trialing".equals(s.getStatus()))
                    .flatMap(s -> s.getItems().getData().stream())
                    .map(SubscriptionItem::getPrice)
                    .filter(Objects::nonNull)
                    .map(p -> p.getId())
                    .toList();
        } catch (StripeException e) {
            throw new StripeLookupException("Abos von Stripe nicht lesbar: " + e.getMessage(), e);
        }
    }

    public static class StripeLookupException extends RuntimeException {
        StripeLookupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
