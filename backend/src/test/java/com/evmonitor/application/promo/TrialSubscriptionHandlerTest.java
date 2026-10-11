package com.evmonitor.application.promo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TrialSubscriptionHandlerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final long DAY = 86_400L;
    private static final long START = 1_800_000_000L;

    private final PromoCodeService promoCodeService = mock(PromoCodeService.class);
    private final TrialReminderService reminders = mock(TrialReminderService.class);
    private final TrialSubscriptionHandler handler = new TrialSubscriptionHandler(promoCodeService, reminders);

    private static JsonObject subscription(String status, long trialDays, String promoCode) {
        String metadata = promoCode == null ? "{}" : "{\"promo_code\":\"" + promoCode + "\"}";
        return JsonParser.parseString("{\"status\":\"" + status + "\",\"trial_start\":" + START
                + ",\"trial_end\":" + (START + trialDays * DAY) + ",\"metadata\":" + metadata + "}").getAsJsonObject();
    }

    @Test
    void promoTrialIsRedeemedAndRemindedSevenDaysBeforeTheEnd() {
        handler.onSubscriptionChange(USER_ID, subscription("trialing", 28, "FIRSTEV"));

        verify(promoCodeService).recordRedemption(USER_ID, "FIRSTEV");
        LocalDateTime expectedDue = LocalDateTime.ofInstant(Instant.ofEpochSecond(START + 21 * DAY), ZoneId.systemDefault());
        verify(reminders).schedule(USER_ID, expectedDue);
    }

    @Test
    void sevenDayTrialGetsNoReminder() {
        handler.onSubscriptionChange(USER_ID, subscription("trialing", 7, null));

        verify(promoCodeService, never()).recordRedemption(any(), any());
        verify(reminders, never()).schedule(any(), any());
    }

    @Test
    void leavingTheTrialClearsTheReminder() {
        handler.onSubscriptionChange(USER_ID, subscription("active", 28, "FIRSTEV"));

        verify(reminders).clear(USER_ID);
        verify(promoCodeService, never()).recordRedemption(any(), any());
    }

    @Test
    void missingTrialFieldsAreIgnored() {
        JsonObject bare = JsonParser.parseString("{\"status\":\"trialing\",\"trial_start\":null,\"trial_end\":null}").getAsJsonObject();

        handler.onSubscriptionChange(USER_ID, bare);

        verify(reminders, never()).schedule(eq(USER_ID), any());
    }

    @Test
    void subscriptionWithoutTrialLeavesTheReminderOfAnotherOneAlone() {
        // Zweites Abo (weiteres Auto) ohne Trial, während das erste noch im Trial läuft
        JsonObject secondCar = JsonParser.parseString("{\"status\":\"active\",\"trial_start\":null,\"trial_end\":null}").getAsJsonObject();

        handler.onSubscriptionChange(USER_ID, secondCar);

        verify(reminders, never()).clear(any());
    }
}
