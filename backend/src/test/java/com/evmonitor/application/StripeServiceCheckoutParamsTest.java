package com.evmonitor.application;

import com.evmonitor.application.promo.PromoCheck;
import com.evmonitor.application.promo.PromoCodeService;
import com.evmonitor.application.promo.PromoOffer;
import com.evmonitor.application.promo.TrialSubscriptionHandler;
import com.evmonitor.domain.SubscriptionTier;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.exception.ValidationException;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StripeServiceCheckoutParamsTest {

    private final PromoCodeService promoCodeService = mock(PromoCodeService.class);
    private final StripeService stripeService = new StripeService(mock(UserRepository.class), mock(AdminAlertService.class),
            mock(StripeSubscriptionLookup.class), mock(TrialSubscriptionHandler.class), promoCodeService);

    private static final StripeService.CheckoutParams MONTHLY = new StripeService.CheckoutParams(
            "monthly", SubscriptionTier.AUTOSYNC, "https://ev-monitor.net/upgrade/success", "https://ev-monitor.net/upgrade/cancel", null);

    private static User user(boolean trialUsed) {
        return User.createVerifiedLocalUser("a@example.com", "a_user", "hash").toBuilder().trialUsed(trialUsed).build();
    }

    @BeforeEach
    void noReferralCoupon() {
        ReflectionTestUtils.setField(stripeService, "referralCouponId", "");
    }

    @Test
    void promoGivesItsTrialLengthAndTagsTheSubscription() {
        SessionCreateParams params = stripeService.buildCheckoutParams(user(false), "cus_1", "price_1", MONTHLY, new PromoOffer("FIRSTEV", 28));

        assertThat(params.getSubscriptionData().getTrialPeriodDays()).isEqualTo(28L);
        assertThat(params.getSubscriptionData().getMetadata()).containsEntry(TrialSubscriptionHandler.PROMO_CODE_METADATA, "FIRSTEV");
    }

    @Test
    void withoutPromoTheDefaultTrialStays() {
        SessionCreateParams params = stripeService.buildCheckoutParams(user(false), "cus_1", "price_1", MONTHLY, null);

        assertThat(params.getSubscriptionData().getTrialPeriodDays()).isEqualTo(7L);
        assertThat(params.getSubscriptionData().getMetadata()).isNullOrEmpty();
    }

    @Test
    void usedTrialGetsNoTrialEvenWithPromo() {
        SessionCreateParams params = stripeService.buildCheckoutParams(user(true), "cus_1", "price_1", MONTHLY, new PromoOffer("FIRSTEV", 28));

        assertThat(params.getSubscriptionData()).isNull();
    }

    @Test
    void codeThatDoesNotApplyStopsTheCheckout() {
        User user = user(false);
        when(promoCodeService.check(user, "FIRSTEV")).thenReturn(new PromoCheck(null, PromoCheck.Reason.NOT_ELIGIBLE));

        assertThatThrownBy(() -> stripeService.createCheckoutSession(user, new StripeService.CheckoutParams(
                "monthly", SubscriptionTier.AUTOSYNC, "s", "c", "FIRSTEV")))
                .isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("PROMO_NOT_ELIGIBLE");
    }
}
