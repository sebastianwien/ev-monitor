package com.evmonitor.application;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StripeService.dispatch() — the validated event handler.
 *
 * We bypass Webhook.constructEvent() (a Stripe SDK static method) by testing
 * the package-private dispatch() method directly. This is intentional: the
 * signature verification is Stripe's responsibility, not ours. Our business
 * logic starts after the Event is trusted.
 *
 * All Stripe API calls (Customer.retrieve, balanceTransactions().create, etc.)
 * are static methods on Stripe SDK objects and cannot be mocked without PowerMock.
 * The referral reward Stripe credit path is therefore tested only up to the point
 * where claimReferralReward() returns true — the subsequent Stripe call is
 * left untested at the unit level (covered by integration/manual testing).
 */
@ExtendWith(MockitoExtension.class)
class StripeServiceTest {

    @Mock
    private UserRepository userRepository;

    private StripeService stripeService;

    private static final String CUSTOMER_ID = "cus_testABC123";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REFERRER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        stripeService = new StripeService(userRepository);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User buildUser(UUID id, UUID referredByUserId) {
        return new User(
                id, "test@example.com", "testuser", "hash",
                AuthProvider.LOCAL, "USER",
                true, false, true, false, false,
                "REFERRALCODE1", referredByUserId,
                CUSTOMER_ID,
                null, null, null, null, null, null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private User buildUserWithStripeId(UUID id, String stripeCustomerId) {
        return new User(
                id, "test@example.com", "testuser", "hash",
                AuthProvider.LOCAL, "USER",
                true, false, true, false, false,
                "REFERRALCODE1", null,
                stripeCustomerId,
                null, null, null, null, null, null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private JsonObject subscriptionPayload(String customerId, String status, Long periodEndEpoch) {
        String periodEndJson = periodEndEpoch != null
                ? String.valueOf(periodEndEpoch)
                : "null";
        return JsonParser.parseString("""
                {
                  "customer": "%s",
                  "status": "%s",
                  "current_period_end": %s
                }
                """.formatted(customerId, status, periodEndJson)).getAsJsonObject();
    }

    private JsonObject invoicePayload(String customerId, long amountPaid) {
        return JsonParser.parseString("""
                {
                  "customer": "%s",
                  "amount_paid": %d
                }
                """.formatted(customerId, amountPaid)).getAsJsonObject();
    }

    // =========================================================================
    // customer.subscription.created / customer.subscription.updated
    // =========================================================================

    @Nested
    class SubscriptionCreatedOrUpdated {

        @Test
        void statusActive_setsPremiumTrue_andPeriodEnd() {
            long periodEndEpoch = 1_800_000_000L;
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.created",
                    subscriptionPayload(CUSTOMER_ID, "active", periodEndEpoch));

            verify(userRepository).setPremium(USER_ID, true);
            ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
            verify(userRepository).setSubscriptionPeriodEnd(eq(USER_ID), captor.capture());
            assertThat(captor.getValue()).isEqualTo(Instant.ofEpochSecond(periodEndEpoch));
        }

        @Test
        void statusTrialing_setsPremiumTrue() {
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "trialing", 1_800_000_000L));

            verify(userRepository).setPremium(USER_ID, true);
        }

        @Test
        void statusPastDue_setsPremiumFalse() {
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "past_due", 1_800_000_000L));

            verify(userRepository).setPremium(USER_ID, false);
        }

        @Test
        void statusCanceled_setsPremiumFalse() {
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "canceled", 1_800_000_000L));

            verify(userRepository).setPremium(USER_ID, false);
        }

        @Test
        void noPeriodEnd_doesNotCallSetSubscriptionPeriodEnd() {
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.created",
                    subscriptionPayload(CUSTOMER_ID, "active", null));

            verify(userRepository).setPremium(USER_ID, true);
            verify(userRepository, never()).setSubscriptionPeriodEnd(any(), any());
        }

        @Test
        void unknownCustomerId_noDbCallsMade() {
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

            stripeService.dispatch("customer.subscription.created",
                    subscriptionPayload(CUSTOMER_ID, "active", 1_800_000_000L));

            verify(userRepository, never()).setPremium(any(), anyBoolean());
            verify(userRepository, never()).setSubscriptionPeriodEnd(any(), any());
        }
    }

    // =========================================================================
    // customer.subscription.deleted
    // =========================================================================

    @Nested
    class SubscriptionDeleted {

        @Test
        void setsPremiumFalse() {
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            JsonObject data = JsonParser.parseString("""
                    {"customer": "%s"}
                    """.formatted(CUSTOMER_ID)).getAsJsonObject();

            stripeService.dispatch("customer.subscription.deleted", data);

            verify(userRepository).setPremium(USER_ID, false);
        }

        @Test
        void unknownCustomerId_noDbCallsMade() {
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

            JsonObject data = JsonParser.parseString("""
                    {"customer": "%s"}
                    """.formatted(CUSTOMER_ID)).getAsJsonObject();

            stripeService.dispatch("customer.subscription.deleted", data);

            verify(userRepository, never()).setPremium(any(), anyBoolean());
        }
    }

    // =========================================================================
    // invoice.payment_succeeded
    // =========================================================================

    @Nested
    class InvoicePaymentSucceeded {

        @Test
        void amountPaidIsZero_noReferralCheck() {
            stripeService.dispatch("invoice.payment_succeeded",
                    invoicePayload(CUSTOMER_ID, 0L));

            // amount=0 path exits immediately — no user lookup at all
            verify(userRepository, never()).findByStripeCustomerId(any());
            verify(userRepository, never()).claimReferralReward(any());
        }

        @Test
        void amountPaidPositive_userHasNoReferrer_noClaimAttempt() {
            User user = buildUser(USER_ID, null); // referredByUserId = null
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("invoice.payment_succeeded",
                    invoicePayload(CUSTOMER_ID, 390L));

            verify(userRepository, never()).claimReferralReward(any());
        }

        @Test
        void amountPaidPositive_claimReferralReturnsFalse_noFurtherAction() {
            User user = buildUser(USER_ID, REFERRER_ID); // has referrer
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.claimReferralReward(USER_ID)).thenReturn(false);

            stripeService.dispatch("invoice.payment_succeeded",
                    invoicePayload(CUSTOMER_ID, 390L));

            verify(userRepository).claimReferralReward(USER_ID);
            // Stripe credit call never reached — claimReferralReward was false
            verify(userRepository, never()).findById(any());
        }

        @Test
        void amountPaidPositive_claimReferralReturnsTrue_looksUpReferrer() {
            User user = buildUser(USER_ID, REFERRER_ID);
            User referrer = buildUserWithStripeId(REFERRER_ID, "cus_referrerXYZ");

            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.claimReferralReward(USER_ID)).thenReturn(true);
            when(userRepository.findById(REFERRER_ID)).thenReturn(Optional.of(referrer));

            // The subsequent Customer.retrieve() is a Stripe static call — it will
            // throw a NullPointerException or StripeException in a unit test because
            // the Stripe HTTP client is not initialized. The service catches all
            // StripeExceptions and logs them, so the test must not expect an exception.
            // We only verify the DB operations up to this point.
            try {
                stripeService.dispatch("invoice.payment_succeeded",
                        invoicePayload(CUSTOMER_ID, 390L));
            } catch (Exception e) {
                // Any exception from the Stripe SDK layer is acceptable here —
                // we only care that the DB path executed correctly.
            }

            verify(userRepository).claimReferralReward(USER_ID);
            verify(userRepository).findById(REFERRER_ID);
        }

        @Test
        void unknownCustomerId_noDbCallsBeyondLookup() {
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

            stripeService.dispatch("invoice.payment_succeeded",
                    invoicePayload(CUSTOMER_ID, 390L));

            verify(userRepository).findByStripeCustomerId(CUSTOMER_ID);
            verify(userRepository, never()).claimReferralReward(any());
        }
    }

    // =========================================================================
    // invoice.payment_failed
    // =========================================================================

    @Nested
    class InvoicePaymentFailed {

        @Test
        void onlyLogsWarning_noDbCallsMade() {
            JsonObject data = JsonParser.parseString("""
                    {"customer": "%s"}
                    """.formatted(CUSTOMER_ID)).getAsJsonObject();

            stripeService.dispatch("invoice.payment_failed", data);

            // payment_failed must never touch the DB — Stripe handles retries
            verify(userRepository, never()).setPremium(any(), anyBoolean());
            verify(userRepository, never()).findByStripeCustomerId(any());
        }
    }

    // =========================================================================
    // Unknown / unhandled event types
    // =========================================================================

    @Nested
    class UnknownEventType {

        @Test
        void unknownType_doesNotThrow_noDbCalls() {
            JsonObject data = new JsonObject();

            // Must not throw — unknown events are silently ignored
            stripeService.dispatch("some.completely.unknown.event", data);

            verifyNoInteractions(userRepository);
        }

        @Test
        void chargeSucceeded_unknownType_silentlyIgnored() {
            JsonObject data = new JsonObject();

            stripeService.dispatch("charge.succeeded", data);

            verifyNoInteractions(userRepository);
        }
    }
}
