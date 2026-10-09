package com.evmonitor.application.promo;

import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.persistence.JpaPromoCodeRepository;
import com.evmonitor.infrastructure.persistence.JpaPromoRedemptionRepository;
import com.evmonitor.infrastructure.persistence.PromoCodeEntity;
import com.evmonitor.infrastructure.persistence.PromoRedemptionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Promo codes that lengthen the Stripe trial. A code is valid for a user when it is active and not
 * expired, its quota is not used up, the user's sign-up source matches and the user never had a
 * trial. Redemption is recorded when Stripe starts the trial, not at checkout.
 */
@Service
@RequiredArgsConstructor
public class PromoCodeService {

    private final JpaPromoCodeRepository codeRepository;
    private final JpaPromoRedemptionRepository redemptionRepository;

    public PromoCheck check(User user, String rawCode) {
        PromoCodeEntity code = codeRepository.findById(normalize(rawCode))
                .filter(PromoCodeService::isLive)
                .orElse(null);
        if (code == null) return PromoCheck.rejected(PromoCheck.Reason.UNKNOWN);
        if (code.getRequiredUtmSource() != null && !code.getRequiredUtmSource().equalsIgnoreCase(user.getUtmSource())) {
            return PromoCheck.rejected(PromoCheck.Reason.NOT_ELIGIBLE);
        }
        if (user.isTrialUsed()) return PromoCheck.rejected(PromoCheck.Reason.TRIAL_USED);
        if (redemptionRepository.existsById(user.getId())) return PromoCheck.rejected(PromoCheck.Reason.ALREADY_REDEEMED);
        if (redemptionRepository.countByCode(code.getCode()) >= code.getMaxRedemptions()) {
            return PromoCheck.rejected(PromoCheck.Reason.EXHAUSTED);
        }
        return PromoCheck.valid(new PromoOffer(code.getCode(), code.getTrialDays()));
    }

    /** The code made for the user's sign-up source, if the user can redeem it (drives the teaser banner). */
    public Optional<PromoOffer> offerFor(User user) {
        if (user.getUtmSource() == null) return Optional.empty();
        return codeRepository.findFirstByRequiredUtmSourceIgnoreCaseAndActiveTrue(user.getUtmSource())
                .map(code -> check(user, code.getCode()))
                .filter(PromoCheck::valid)
                .map(PromoCheck::offer);
    }

    /**
     * Idempotent: Stripe sends created and updated for the same trial, often at the same time.
     * The loser of that race hits the primary key and is fine - the redemption is recorded.
     */
    public void recordRedemption(UUID userId, String rawCode) {
        if (redemptionRepository.existsById(userId)) return;
        try {
            redemptionRepository.save(new PromoRedemptionEntity(userId, normalize(rawCode), LocalDateTime.now()));
        } catch (DataIntegrityViolationException e) {
            // parallel webhook was faster
        }
    }

    private static boolean isLive(PromoCodeEntity code) {
        return code.isActive() && (code.getValidUntil() == null || code.getValidUntil().isAfter(LocalDateTime.now()));
    }

    private static String normalize(String rawCode) {
        return rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
    }
}
