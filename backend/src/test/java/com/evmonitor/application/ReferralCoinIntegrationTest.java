package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.evmonitor.infrastructure.email.EmailService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Integration tests for referral coin awards.
 *
 * Coins are awarded in AuthService.verifyEmail() — referrer gets REFERRAL_INVITED (100),
 * referred user gets REFERRAL_WELCOME (25). Tests call the service directly with a real
 * DB to verify both awards and the idempotency cap (max 20 referrals per referrer).
 */
class ReferralCoinIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private CoinLogService coinLogService;
    @Autowired private EmailVerificationTokenRepository tokenRepository;

    @MockitoBean private EmailService emailService;

    @Test
    void referrer_receives100Coins_whenReferredUserVerifiesEmail() {
        doNothing().when(emailService).sendVerificationEmail(anyString(), anyString());

        User referrer = createAndSaveVerifiedUser();
        User referred  = saveUnverifiedUserWithReferrer(referrer.getId());
        String token   = saveVerificationToken(referred.getId());

        authService.verifyEmail(token);

        List<CoinLog> referrerCoins = coinLogRepository.findAllByUserId(referrer.getId());
        assertThat(referrerCoins).hasSize(1);
        assertThat(referrerCoins.get(0).getAmount()).isEqualTo(100);
        assertThat(referrerCoins.get(0).getActionDescription())
                .isEqualTo(CoinLogService.CoinEvent.REFERRAL_INVITED.getDescription());
    }

    @Test
    void referredUser_receives25Coins_whenVerifyingEmail() {
        doNothing().when(emailService).sendVerificationEmail(anyString(), anyString());

        User referrer = createAndSaveVerifiedUser();
        User referred  = saveUnverifiedUserWithReferrer(referrer.getId());
        String token   = saveVerificationToken(referred.getId());

        authService.verifyEmail(token);

        List<CoinLog> referredCoins = coinLogRepository.findAllByUserId(referred.getId());
        assertThat(referredCoins).hasSize(1);
        assertThat(referredCoins.get(0).getAmount()).isEqualTo(25);
        assertThat(referredCoins.get(0).getActionDescription())
                .isEqualTo(CoinLogService.CoinEvent.REFERRAL_WELCOME.getDescription());
    }

    @Test
    void noCoins_whenUserVerifiesEmail_withoutReferrer() {
        doNothing().when(emailService).sendVerificationEmail(anyString(), anyString());

        User user  = saveUnverifiedUserWithReferrer(null);
        String token = saveVerificationToken(user.getId());

        authService.verifyEmail(token);

        assertThat(coinLogRepository.findAllByUserId(user.getId())).isEmpty();
    }

    @Test
    void referrer_doesNotReceiveCoins_beyondMaxReferrals() {
        doNothing().when(emailService).sendVerificationEmail(anyString(), anyString());

        User referrer = createAndSaveVerifiedUser();

        // Verify 20 referred users — fills up the cap (MAX_REFERRALS = 20)
        for (int i = 0; i < 20; i++) {
            User referred = saveUnverifiedUserWithReferrer(referrer.getId());
            String token  = saveVerificationToken(referred.getId());
            authService.verifyEmail(token);
        }

        long coinsAtCap = coinLogRepository.findAllByUserId(referrer.getId()).stream()
                .filter(c -> CoinLogService.CoinEvent.REFERRAL_INVITED.getDescription().equals(c.getActionDescription()))
                .count();
        assertThat(coinsAtCap).isEqualTo(20);

        // 21st referral should NOT award the referrer
        User twentyFirstReferred = saveUnverifiedUserWithReferrer(referrer.getId());
        String token = saveVerificationToken(twentyFirstReferred.getId());
        authService.verifyEmail(token);

        long coinsAfterCap = coinLogRepository.findAllByUserId(referrer.getId()).stream()
                .filter(c -> CoinLogService.CoinEvent.REFERRAL_INVITED.getDescription().equals(c.getActionDescription()))
                .count();
        assertThat(coinsAfterCap).isEqualTo(20);
    }

    @Test
    void referralWelcome_awardedOnlyOnce_evenIfCalledTwice() {
        User user = createAndSaveVerifiedUser();

        coinLogService.awardCoinsForEvent(user.getId(), CoinLogService.CoinEvent.REFERRAL_WELCOME, null);
        coinLogService.awardCoinsForEvent(user.getId(), CoinLogService.CoinEvent.REFERRAL_WELCOME, null);

        long welcomeEntries = coinLogRepository.findAllByUserId(user.getId()).stream()
                .filter(c -> CoinLogService.CoinEvent.REFERRAL_WELCOME.getDescription().equals(c.getActionDescription()))
                .count();
        assertThat(welcomeEntries).isEqualTo(1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User createAndSaveVerifiedUser() {
        return userRepository.save(
                User.createVerifiedLocalUser(
                        "referrer-" + System.nanoTime() + "@ev-monitor.net",
                        "referrer-" + System.nanoTime(),
                        "hash"));
    }

    private User saveUnverifiedUserWithReferrer(java.util.UUID referrerId) {
        User user = referrerId != null
                ? User.createNewLocalUserWithReferrer(
                        "referred-" + System.nanoTime() + "@ev-monitor.net",
                        "referred-" + System.nanoTime(),
                        "hash",
                        referrerId)
                : User.createNewLocalUser(
                        "noref-" + System.nanoTime() + "@ev-monitor.net",
                        "noref-" + System.nanoTime(),
                        "hash");
        return userRepository.save(user);
    }

    private String saveVerificationToken(java.util.UUID userId) {
        EmailVerificationToken token = EmailVerificationToken.createFor(userId);
        return tokenRepository.save(token).getToken();
    }
}
