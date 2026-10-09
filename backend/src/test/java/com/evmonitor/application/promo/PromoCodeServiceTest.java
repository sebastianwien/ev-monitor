package com.evmonitor.application.promo;

import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.persistence.JpaPromoCodeRepository;
import com.evmonitor.infrastructure.persistence.JpaPromoRedemptionRepository;
import com.evmonitor.infrastructure.persistence.PromoCodeEntity;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromoCodeServiceTest {

    private final JpaPromoCodeRepository codes = mock(JpaPromoCodeRepository.class);
    private final JpaPromoRedemptionRepository redemptions = mock(JpaPromoRedemptionRepository.class);
    private final PromoCodeService service = new PromoCodeService(codes, redemptions);

    private static PromoCodeEntity firstev() {
        return new PromoCodeEntity("FIRSTEV", 28, 100, "firstev", null, true, LocalDateTime.now());
    }

    private static User user(String utmSource, boolean trialUsed) {
        return User.createVerifiedLocalUser("a@example.com", "a_user", "hash").toBuilder()
                .utmSource(utmSource).trialUsed(trialUsed).build();
    }

    @Test
    void firstevReaderGets28Days() {
        when(codes.findById("FIRSTEV")).thenReturn(Optional.of(firstev()));
        when(redemptions.countByCode("FIRSTEV")).thenReturn(12L);

        PromoCheck check = service.check(user("firstev", false), " firstev ");

        assertThat(check.valid()).isTrue();
        assertThat(check.offer().trialDays()).isEqualTo(28);
        assertThat(check.offer().code()).isEqualTo("FIRSTEV");
    }

    @Test
    void otherSourceIsNotEligible() {
        when(codes.findById("FIRSTEV")).thenReturn(Optional.of(firstev()));

        assertThat(service.check(user("reddit", false), "FIRSTEV").reason()).isEqualTo(PromoCheck.Reason.NOT_ELIGIBLE);
        assertThat(service.check(user(null, false), "FIRSTEV").reason()).isEqualTo(PromoCheck.Reason.NOT_ELIGIBLE);
    }

    @Test
    void usedTrialIsRejected() {
        when(codes.findById("FIRSTEV")).thenReturn(Optional.of(firstev()));

        assertThat(service.check(user("firstev", true), "FIRSTEV").reason()).isEqualTo(PromoCheck.Reason.TRIAL_USED);
    }

    @Test
    void exhaustedQuotaIsRejected() {
        when(codes.findById("FIRSTEV")).thenReturn(Optional.of(firstev()));
        when(redemptions.countByCode("FIRSTEV")).thenReturn(100L);

        assertThat(service.check(user("firstev", false), "FIRSTEV").reason()).isEqualTo(PromoCheck.Reason.EXHAUSTED);
    }

    @Test
    void unknownInactiveOrExpiredCodesAreRejected() {
        when(codes.findById("NOPE")).thenReturn(Optional.empty());
        assertThat(service.check(user("firstev", false), "nope").reason()).isEqualTo(PromoCheck.Reason.UNKNOWN);

        PromoCodeEntity expired = new PromoCodeEntity("OLD", 28, 100, null, LocalDateTime.now().minusDays(1), true, LocalDateTime.now());
        when(codes.findById("OLD")).thenReturn(Optional.of(expired));
        assertThat(service.check(user("firstev", false), "OLD").reason()).isEqualTo(PromoCheck.Reason.UNKNOWN);

        PromoCodeEntity inactive = new PromoCodeEntity("OFF", 28, 100, null, null, false, LocalDateTime.now());
        when(codes.findById("OFF")).thenReturn(Optional.of(inactive));
        assertThat(service.check(user("firstev", false), "OFF").reason()).isEqualTo(PromoCheck.Reason.UNKNOWN);
    }

    @Test
    void redemptionIsRecordedOncePerUser() {
        UUID userId = UUID.randomUUID();
        when(redemptions.existsById(userId)).thenReturn(false, true);

        service.recordRedemption(userId, "firstev");
        service.recordRedemption(userId, "FIRSTEV");

        verify(redemptions).save(any());
    }

    @Test
    void offerForPicksTheCodeMatchingTheUsersSource() {
        when(codes.findFirstByRequiredUtmSourceIgnoreCaseAndActiveTrue("firstev")).thenReturn(Optional.of(firstev()));
        when(codes.findById("FIRSTEV")).thenReturn(Optional.of(firstev()));

        assertThat(service.offerFor(user("firstev", false))).map(PromoOffer::code).contains("FIRSTEV");
        assertThat(service.offerFor(user(null, false))).isEmpty();
        verify(codes, never()).findFirstByRequiredUtmSourceIgnoreCaseAndActiveTrue(null);
    }

    @Test
    void parallelWebhookThatLostTheInsertIsNotAnError() {
        UUID userId = UUID.randomUUID();
        when(redemptions.existsById(userId)).thenReturn(false);
        when(redemptions.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatCode(() -> service.recordRedemption(userId, "FIRSTEV")).doesNotThrowAnyException();
    }
}
