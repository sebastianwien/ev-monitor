package com.evmonitor.application.promo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Reacts to Stripe subscription events for trials: books the promo code from the subscription
 * metadata once the trial runs, and schedules or clears the reminder mail. Trials of 7 days and
 * less get no reminder - it would arrive on the first day.
 */
@Component
@RequiredArgsConstructor
public class TrialSubscriptionHandler {

    /** Metadata key the checkout writes the promo code under. */
    public static final String PROMO_CODE_METADATA = "promo_code";
    private static final Duration MIN_TRIAL_FOR_REMINDER = Duration.ofDays(TrialReminderService.DAYS_BEFORE_END + 1);

    private final PromoCodeService promoCodeService;
    private final TrialReminderService trialReminderService;

    public void onSubscriptionChange(UUID userId, JsonObject subscription) {
        Instant trialEnd = epoch(subscription, "trial_end");
        if (!"trialing".equals(string(subscription, "status"))) {
            // Only a subscription that had a trial ends one; a second subscription without trial
            // (another car) must not wipe the reminder of the running trial.
            if (trialEnd != null) trialReminderService.clear(userId);
            return;
        }
        JsonElement metadata = subscription.get("metadata");
        if (metadata != null && metadata.isJsonObject()) {
            String promoCode = string(metadata.getAsJsonObject(), PROMO_CODE_METADATA);
            if (promoCode != null) promoCodeService.recordRedemption(userId, promoCode);
        }
        Instant trialStart = epoch(subscription, "trial_start");
        if (trialStart == null || trialEnd == null
                || Duration.between(trialStart, trialEnd).compareTo(MIN_TRIAL_FOR_REMINDER) < 0) return;
        Instant due = trialEnd.minus(Duration.ofDays(TrialReminderService.DAYS_BEFORE_END));
        trialReminderService.schedule(userId, LocalDateTime.ofInstant(due, ZoneId.systemDefault()));
    }

    private static String string(JsonObject node, String key) {
        JsonElement value = node.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static Instant epoch(JsonObject node, String key) {
        JsonElement value = node.get(key);
        return value == null || value.isJsonNull() ? null : Instant.ofEpochSecond(value.getAsLong());
    }
}
