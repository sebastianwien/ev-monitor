package com.evmonitor.application;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.SubscriptionTier;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
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

    @Mock
    private AdminAlertService adminAlertService;

    private StripeService stripeService;

    private static final String CUSTOMER_ID = "cus_testABC123";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REFERRER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        stripeService = new StripeService(userRepository, adminAlertService);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User buildUser(UUID id, UUID referredByUserId) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .id(id)
                .email("test@example.com").username("testuser").passwordHash("hash")
                .authProvider(AuthProvider.LOCAL).role("USER")
                .emailVerified(true).emailNotificationsEnabled(true)
                .referralCode("REFERRALCODE1").referredByUserId(referredByUserId)
                .stripeCustomerId(CUSTOMER_ID)
                .createdAt(now).updatedAt(now)
                .build();
    }

    private User buildUserWithStripeId(UUID id, String stripeCustomerId) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .id(id)
                .email("test@example.com").username("testuser").passwordHash("hash")
                .authProvider(AuthProvider.LOCAL).role("USER")
                .emailVerified(true).emailNotificationsEnabled(true)
                .referralCode("REFERRALCODE1")
                .stripeCustomerId(stripeCustomerId)
                .createdAt(now).updatedAt(now)
                .build();
    }

    private JsonObject subscriptionPayload(String customerId, String status, Long periodEndEpoch) {
        return subscriptionPayload(customerId, status, periodEndEpoch, null);
    }

    private JsonObject subscriptionPayload(String customerId, String status, Long periodEndEpoch, String priceId) {
        String periodEndJson = periodEndEpoch != null
                ? String.valueOf(periodEndEpoch)
                : "null";
        String itemsJson = priceId != null
                ? ",\"items\":{\"data\":[{\"price\":{\"id\":\"" + priceId + "\"}}]}"
                : "";
        String json = "{\"customer\":\"" + customerId + "\","
                + "\"status\":\"" + status + "\","
                + "\"current_period_end\":" + periodEndJson
                + itemsJson
                + "}";
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private JsonObject invoicePayload(String customerId, long amountPaid) {
        return JsonParser.parseString("""
                {
                  "customer": "%s",
                  "amount_paid": %d
                }
                """.formatted(customerId, amountPaid)).getAsJsonObject();
    }

    /** Installs a mock RestTemplate so disconnectSmartcar() HTTP calls can be observed. */
    private RestTemplate installMockRestTemplate() {
        RestTemplate mockRest = mock(RestTemplate.class);
        lenient().when(mockRest.exchange(anyString(), any(HttpMethod.class), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.noContent().build());
        ReflectionTestUtils.setField(stripeService, "restTemplate", mockRest);
        ReflectionTestUtils.setField(stripeService, "connectorsBaseUrl", "http://test-connectors:8081");
        ReflectionTestUtils.setField(stripeService, "internalToken", "test-internal-token");
        return mockRest;
    }

    private void verifyDisconnectCalledFor(RestTemplate mockRest, UUID userId) {
        verify(mockRest).exchange(
                contains("/api/internal/smartcar/disconnect/" + userId),
                eq(HttpMethod.DELETE), any(), eq(Void.class));
    }

    private void verifyTeslaEnableCalledFor(RestTemplate mockRest, UUID userId) {
        verify(mockRest).exchange(
                contains("/api/internal/tesla/" + userId + "/enable-telemetry"),
                eq(HttpMethod.POST), any(), eq(Void.class));
    }

    private void verifyTeslaDisableCalledFor(RestTemplate mockRest, UUID userId) {
        verify(mockRest).exchange(
                contains("/api/internal/tesla/" + userId + "/disable-telemetry"),
                eq(HttpMethod.POST), any(), eq(Void.class));
    }

    private User userWithRole(UUID id, String role, boolean premium) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .id(id).email(role.toLowerCase() + "@example.com").username(role.toLowerCase()).passwordHash("hash")
                .authProvider(AuthProvider.LOCAL).role(role)
                .emailVerified(true).emailNotificationsEnabled(true)
                .referralCode("REFERRALCODE1").stripeCustomerId(CUSTOMER_ID)
                .premium(premium)
                .createdAt(now).updatedAt(now)
                .build();
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

            verify(userRepository).setSubscriptionTier(USER_ID, SubscriptionTier.AUTOSYNC);
            ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
            verify(userRepository).setSubscriptionPeriodEnd(eq(USER_ID), captor.capture());
            assertThat(captor.getValue()).isEqualTo(Instant.ofEpochSecond(periodEndEpoch));
        }

        @Test
        void inactiveToActive_stampsAutosyncStartedAt() {
            User user = buildUser(USER_ID, null); // premium=false → tier NONE
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.created",
                    subscriptionPayload(CUSTOMER_ID, "active", 1_800_000_000L));

            verify(userRepository).setAutoSyncStartedAtIfNull(eq(USER_ID), any(Instant.class));
        }

        @Test
        void supporterPurchase_doesNotStampAutosyncStartedAt() {
            // SUPPORTER is the orthogonal analytics-only tier - no AutoSync, so no survey anchor.
            ReflectionTestUtils.setField(stripeService, "priceIdSupporterMonthly", "price_SUPPORTER_M");
            User user = buildUser(USER_ID, null); // tier NONE
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.created",
                    subscriptionPayload(CUSTOMER_ID, "active", 1_800_000_000L, "price_SUPPORTER_M"));

            verify(userRepository).setSubscriptionTier(USER_ID, SubscriptionTier.SUPPORTER);
            verify(userRepository, never()).setAutoSyncStartedAtIfNull(any(), any());
        }

        @Test
        void alreadyPremium_doesNotStampAutosyncStartedAt() {
            // No inactive→active transition (oldTier already AUTOSYNC) → purchase moment
            // must not be re-stamped. The IF-NULL guard is a backstop, but we also don't call.
            User user = userWithRole(USER_ID, "USER", true); // premium=true → tier AUTOSYNC
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "active", 1_800_000_000L));

            verify(userRepository, never()).setAutoSyncStartedAtIfNull(any(), any());
        }

        @Test
        void statusTrialing_setsPremiumTrue() {
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "trialing", 1_800_000_000L));

            verify(userRepository).setSubscriptionTier(USER_ID, SubscriptionTier.AUTOSYNC);
        }

        @Test
        void statusPastDue_setsPremiumFalse() {
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "past_due", 1_800_000_000L));

            verify(userRepository).setSubscriptionTier(USER_ID, SubscriptionTier.NONE);
        }

        @Test
        void statusCanceled_setsPremiumFalse() {
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "canceled", 1_800_000_000L));

            verify(userRepository).setSubscriptionTier(USER_ID, SubscriptionTier.NONE);
        }

        @Test
        void noPeriodEnd_doesNotCallSetSubscriptionPeriodEnd() {
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.created",
                    subscriptionPayload(CUSTOMER_ID, "active", null));

            verify(userRepository).setSubscriptionTier(USER_ID, SubscriptionTier.AUTOSYNC);
            verify(userRepository, never()).setSubscriptionPeriodEnd(any(), any());
        }

        @Test
        void unknownCustomerId_noDbCallsMade() {
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

            stripeService.dispatch("customer.subscription.created",
                    subscriptionPayload(CUSTOMER_ID, "active", 1_800_000_000L));

            verify(userRepository, never()).setSubscriptionTier(any(), any());
            verify(userRepository, never()).setSubscriptionPeriodEnd(any(), any());
        }

        @Test
        void statusCanceled_triggersSmartcarDisconnect() {
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user)); // role=USER, premium=false

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "canceled", 1_800_000_000L));

            verifyDisconnectCalledFor(mockRest, USER_ID);
        }

        @Test
        void statusActive_doesNotTriggerSmartcarDisconnect() {
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "active", 1_800_000_000L));

            // Active status must not disconnect Smartcar. (It DOES trigger Tesla
            // telemetry enable for inactive→active transition - covered separately.)
            verify(mockRest, never()).exchange(
                    contains("/api/internal/smartcar/disconnect/"),
                    any(HttpMethod.class), any(), eq(Void.class));
        }

        @Test
        void statusPastDue_triggersSmartcarDisconnect() {
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "past_due", 1_800_000_000L));

            verifyDisconnectCalledFor(mockRest, USER_ID);
        }

        @Test
        void adminUser_canceled_skipsSmartcarDisconnect() {
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            User admin = User.builder()
                    .id(USER_ID).email("admin@example.com").username("admin").passwordHash("hash")
                    .authProvider(AuthProvider.LOCAL).role("ADMIN")
                    .emailVerified(true).emailNotificationsEnabled(true)
                    .referralCode("REFERRALCODE1").stripeCustomerId(CUSTOMER_ID)
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                    .build();
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(admin));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "canceled", 1_800_000_000L));

            verify(mockRest, never()).exchange(anyString(), any(HttpMethod.class), any(), eq(Void.class));
        }

        @Test
        void betaTesterUser_canceled_skipsSmartcarDisconnect() {
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            User beta = User.builder()
                    .id(USER_ID).email("beta@example.com").username("beta").passwordHash("hash")
                    .authProvider(AuthProvider.LOCAL).role("BETA_TESTER")
                    .emailVerified(true).emailNotificationsEnabled(true)
                    .referralCode("REFERRALCODE1").stripeCustomerId(CUSTOMER_ID)
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                    .build();
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(beta));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "canceled", 1_800_000_000L));

            verify(mockRest, never()).exchange(anyString(), any(HttpMethod.class), any(), eq(Void.class));
        }

        @Test
        void liveTierPriceId_setsTierToAutosyncLive() {
            // Mockito hoistery: configure the field before dispatch sees it.
            ReflectionTestUtils.setField(stripeService, "priceIdLiveMonthly", "price_LIVE_M");
            ReflectionTestUtils.setField(stripeService, "priceIdLiveYearly", "price_LIVE_Y");
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.created",
                    subscriptionPayload(CUSTOMER_ID, "active", 1_800_000_000L, "price_LIVE_M"));

            verify(userRepository).setSubscriptionTier(USER_ID, SubscriptionTier.AUTOSYNC_LIVE);
        }

        @Test
        void unknownPriceId_defaultsToAutosync() {
            ReflectionTestUtils.setField(stripeService, "priceIdLiveMonthly", "price_LIVE_M");
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.created",
                    subscriptionPayload(CUSTOMER_ID, "active", 1_800_000_000L, "price_LEGACY_UNKNOWN"));

            verify(userRepository).setSubscriptionTier(USER_ID, SubscriptionTier.AUTOSYNC);
        }

        @Test
        void tierDowngrade_liveToAutosync_rePushesProfile() {
            // Existing Live-tier user, webhook arrives with AutoSync-tier price-id (downgrade
            // took effect at period end). Telemetry must be re-pushed to flip FULL → CHARGING_ONLY.
            RestTemplate mockRest = installMockRestTemplate();
            ReflectionTestUtils.setField(stripeService, "priceIdLiveMonthly", "price_LIVE_M");
            User liveUser = User.builder()
                    .id(USER_ID).email("u@example.com").username("u").passwordHash("hash")
                    .authProvider(AuthProvider.LOCAL).role("USER")
                    .emailVerified(true).emailNotificationsEnabled(true)
                    .referralCode("REFERRALCODE1").stripeCustomerId(CUSTOMER_ID)
                    .subscriptionTier(SubscriptionTier.AUTOSYNC_LIVE)
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                    .build();
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(liveUser));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "active", 1_800_000_000L, "price_AUTOSYNC_M"));

            verify(userRepository).setSubscriptionTier(USER_ID, SubscriptionTier.AUTOSYNC);
            verifyTeslaEnableCalledFor(mockRest, USER_ID);
        }

        @Test
        void raceCondition_userPremiumByConcurrentSub_skipsSmartcarDisconnect() {
            // Stale subscription.deleted webhook arrives after the user has already started a
            // fresh subscription that flipped is_premium back to true. Re-read catches this.
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            User refreshed = User.builder()
                    .id(USER_ID).email("test@example.com").username("testuser").passwordHash("hash")
                    .authProvider(AuthProvider.LOCAL).role("USER")
                    .emailVerified(true).emailNotificationsEnabled(true)
                    .referralCode("REFERRALCODE1").stripeCustomerId(CUSTOMER_ID)
                    .premium(true)  // ← race: a parallel subscription.created made user premium again
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                    .build();
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(refreshed));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "canceled", 1_800_000_000L));

            verify(mockRest, never()).exchange(anyString(), any(HttpMethod.class), any(), eq(Void.class));
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

            verify(userRepository).setSubscriptionTier(USER_ID, SubscriptionTier.NONE);
        }

        @Test
        void unknownCustomerId_noDbCallsMade() {
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

            JsonObject data = JsonParser.parseString("""
                    {"customer": "%s"}
                    """.formatted(CUSTOMER_ID)).getAsJsonObject();

            stripeService.dispatch("customer.subscription.deleted", data);

            verify(userRepository, never()).setSubscriptionTier(any(), any());
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
        void setsPremiumFalse_immediately() {
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            JsonObject data = JsonParser.parseString("""
                    {"customer": "%s"}
                    """.formatted(CUSTOMER_ID)).getAsJsonObject();

            stripeService.dispatch("invoice.payment_failed", data);

            verify(userRepository).setSubscriptionTier(USER_ID, SubscriptionTier.NONE);
        }

        @Test
        void triggersSmartcarDisconnect_immediately() {
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            JsonObject data = JsonParser.parseString("""
                    {"customer": "%s"}
                    """.formatted(CUSTOMER_ID)).getAsJsonObject();

            stripeService.dispatch("invoice.payment_failed", data);

            verifyDisconnectCalledFor(mockRest, USER_ID);
        }

        @Test
        void unknownCustomerId_noDbCallsMade() {
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

            JsonObject data = JsonParser.parseString("""
                    {"customer": "%s"}
                    """.formatted(CUSTOMER_ID)).getAsJsonObject();

            stripeService.dispatch("invoice.payment_failed", data);

            verify(userRepository, never()).setSubscriptionTier(any(), any());
        }
    }

    // =========================================================================
    // Tesla telemetry auto-activation / deactivation
    // =========================================================================

    @Nested
    class TeslaTelemetryAutoActivation {

        // ── enable on inactive→active transition ────────────────────────────

        @Test
        void subscriptionCreated_active_userPreviouslyNotPremium_triggersTeslaEnable() {
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null); // premium=false default
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.created",
                    subscriptionPayload(CUSTOMER_ID, "active", 1_800_000_000L));

            verifyTeslaEnableCalledFor(mockRest, USER_ID);
        }

        @Test
        void subscriptionCreated_trialing_userPreviouslyNotPremium_triggersTeslaEnable() {
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.created",
                    subscriptionPayload(CUSTOMER_ID, "trialing", 1_800_000_000L));

            verifyTeslaEnableCalledFor(mockRest, USER_ID);
        }

        @Test
        void subscriptionUpdated_active_userAlreadyPremium_skipsTeslaEnable() {
            // No transition → no auto-enable. Idempotent against repeated webhook delivery
            // and against subscription.updated events that don't change status.
            RestTemplate mockRest = installMockRestTemplate();
            User user = userWithRole(USER_ID, "USER", true); // premium=true already
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "active", 1_800_000_000L));

            verify(mockRest, never()).exchange(
                    contains("/api/internal/tesla/" + USER_ID + "/enable-telemetry"),
                    any(HttpMethod.class), any(), eq(Void.class));
        }

        @Test
        void subscriptionUpdated_inactive_doesNotTriggerEnable() {
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "canceled", 1_800_000_000L));

            verify(mockRest, never()).exchange(
                    contains("/api/internal/tesla/" + USER_ID + "/enable-telemetry"),
                    any(HttpMethod.class), any(), eq(Void.class));
        }

        // ── disable on cancel / payment_failed / subscription.deleted ────────

        @Test
        void subscriptionCanceled_triggersTeslaDisable() {
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user)); // role=USER, premium=false

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "canceled", 1_800_000_000L));

            verifyTeslaDisableCalledFor(mockRest, USER_ID);
        }

        @Test
        void subscriptionDeleted_triggersTeslaDisable() {
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            JsonObject data = JsonParser.parseString("""
                    {"customer": "%s"}
                    """.formatted(CUSTOMER_ID)).getAsJsonObject();

            stripeService.dispatch("customer.subscription.deleted", data);

            verifyTeslaDisableCalledFor(mockRest, USER_ID);
        }

        @Test
        void paymentFailed_triggersTeslaDisable() {
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            JsonObject data = JsonParser.parseString("""
                    {"customer": "%s"}
                    """.formatted(CUSTOMER_ID)).getAsJsonObject();

            stripeService.dispatch("invoice.payment_failed", data);

            verifyTeslaDisableCalledFor(mockRest, USER_ID);
        }

        // ── role-based skip on disable ──────────────────────────────────────

        @Test
        void canceled_adminUser_skipsTeslaDisable() {
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            User admin = userWithRole(USER_ID, "ADMIN", false);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(admin));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "canceled", 1_800_000_000L));

            verify(mockRest, never()).exchange(
                    contains("/api/internal/tesla/" + USER_ID + "/disable-telemetry"),
                    any(HttpMethod.class), any(), eq(Void.class));
        }

        @Test
        void canceled_betaTester_skipsTeslaDisable() {
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            User beta = userWithRole(USER_ID, "BETA_TESTER", false);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(beta));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "canceled", 1_800_000_000L));

            verify(mockRest, never()).exchange(
                    contains("/api/internal/tesla/" + USER_ID + "/disable-telemetry"),
                    any(HttpMethod.class), any(), eq(Void.class));
        }

        @Test
        void canceled_teslaFounder_skipsTeslaDisable() {
            // FOUNDER keeps telemetry via role even after Premium ends.
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            User founder = userWithRole(USER_ID, "TESLA_FOUNDER", false);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(founder));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "canceled", 1_800_000_000L));

            verify(mockRest, never()).exchange(
                    contains("/api/internal/tesla/" + USER_ID + "/disable-telemetry"),
                    any(HttpMethod.class), any(), eq(Void.class));
        }

        @Test
        void canceled_userStillPremiumByRace_skipsTeslaDisable() {
            // Stale subscription.deleted webhook arrives after a fresh subscription
            // restored is_premium=true. Re-read catches this.
            RestTemplate mockRest = installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            User refreshed = userWithRole(USER_ID, "USER", true); // premium=true now
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(refreshed));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "canceled", 1_800_000_000L));

            verify(mockRest, never()).exchange(
                    contains("/api/internal/tesla/" + USER_ID + "/disable-telemetry"),
                    any(HttpMethod.class), any(), eq(Void.class));
        }
    }

    // =========================================================================
    // Purchase celebration - internal "yay, a new subscription!" founder mail
    // =========================================================================

    @Nested
    class PurchaseCelebration {

        @Test
        void newCustomer_active_triggersCelebration_paid() {
            User user = buildUser(USER_ID, null); // tier NONE
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.created",
                    subscriptionPayload(CUSTOMER_ID, "active", 1_800_000_000L));

            verify(adminAlertService).sendPurchaseCelebration(SubscriptionTier.AUTOSYNC, false);
        }

        @Test
        void newCustomer_trialing_triggersCelebration_withTrialFlag() {
            User user = buildUser(USER_ID, null); // tier NONE
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.created",
                    subscriptionPayload(CUSTOMER_ID, "trialing", 1_800_000_000L));

            verify(adminAlertService).sendPurchaseCelebration(SubscriptionTier.AUTOSYNC, true);
        }

        @Test
        void newCustomer_supporter_triggersCelebration() {
            ReflectionTestUtils.setField(stripeService, "priceIdSupporterMonthly", "price_SUPPORTER_M");
            User user = buildUser(USER_ID, null); // tier NONE
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.created",
                    subscriptionPayload(CUSTOMER_ID, "active", 1_800_000_000L, "price_SUPPORTER_M"));

            verify(adminAlertService).sendPurchaseCelebration(SubscriptionTier.SUPPORTER, false);
        }

        @Test
        void renewal_alreadyPremium_doesNotCelebrate() {
            // subscription.updated for an already-active user: renewals must not re-fire.
            User user = userWithRole(USER_ID, "USER", true); // tier AUTOSYNC already
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "active", 1_800_000_000L));

            verify(adminAlertService, never()).sendPurchaseCelebration(any(), anyBoolean());
        }

        @Test
        void upgrade_autosyncToLive_doesNotCelebrate() {
            // Tier flip between paid tiers is not a new customer - no celebration.
            installMockRestTemplate();
            ReflectionTestUtils.setField(stripeService, "priceIdLiveMonthly", "price_LIVE_M");
            User liveUser = User.builder()
                    .id(USER_ID).email("u@example.com").username("u").passwordHash("hash")
                    .authProvider(AuthProvider.LOCAL).role("USER")
                    .emailVerified(true).emailNotificationsEnabled(true)
                    .referralCode("REFERRALCODE1").stripeCustomerId(CUSTOMER_ID)
                    .subscriptionTier(SubscriptionTier.AUTOSYNC)
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                    .build();
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(liveUser));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "active", 1_800_000_000L, "price_LIVE_M"));

            verify(adminAlertService, never()).sendPurchaseCelebration(any(), anyBoolean());
        }

        @Test
        void cancellation_doesNotCelebrate() {
            installMockRestTemplate();
            User user = buildUser(USER_ID, null);
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            stripeService.dispatch("customer.subscription.updated",
                    subscriptionPayload(CUSTOMER_ID, "canceled", 1_800_000_000L));

            verify(adminAlertService, never()).sendPurchaseCelebration(any(), anyBoolean());
        }

        @Test
        void unknownCustomer_doesNotCelebrate() {
            when(userRepository.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

            stripeService.dispatch("customer.subscription.created",
                    subscriptionPayload(CUSTOMER_ID, "active", 1_800_000_000L));

            verify(adminAlertService, never()).sendPurchaseCelebration(any(), anyBoolean());
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

    // =========================================================================
    // appendTargetTier helper - drives UpgradeSuccessView polling to the right tier
    // =========================================================================

    @Nested
    class AppendTargetTier {

        @Test
        void appendsAsQueryParam_whenUrlHasNoQuery() {
            assertThat(StripeService.appendTargetTier(
                    "https://ev-monitor.net/upgrade/success", SubscriptionTier.AUTOSYNC_LIVE))
                    .isEqualTo("https://ev-monitor.net/upgrade/success?target_tier=AUTOSYNC_LIVE");
        }

        @Test
        void appendsAsAdditionalParam_whenUrlAlreadyHasQuery() {
            assertThat(StripeService.appendTargetTier(
                    "https://ev-monitor.net/upgrade/success?from=upgrade", SubscriptionTier.AUTOSYNC))
                    .isEqualTo("https://ev-monitor.net/upgrade/success?from=upgrade&target_tier=AUTOSYNC");
        }

        @Test
        void returnsUrlUnchanged_whenNullOrBlank() {
            assertThat(StripeService.appendTargetTier(null, SubscriptionTier.AUTOSYNC_LIVE)).isNull();
            assertThat(StripeService.appendTargetTier("", SubscriptionTier.AUTOSYNC_LIVE)).isEmpty();
            assertThat(StripeService.appendTargetTier("   ", SubscriptionTier.AUTOSYNC_LIVE)).isEqualTo("   ");
        }

        @Test
        void returnsUrlUnchanged_whenTierNull() {
            assertThat(StripeService.appendTargetTier(
                    "https://ev-monitor.net/upgrade/success", null))
                    .isEqualTo("https://ev-monitor.net/upgrade/success");
        }
    }
}
