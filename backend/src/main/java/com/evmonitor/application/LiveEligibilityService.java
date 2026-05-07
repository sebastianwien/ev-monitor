package com.evmonitor.application;

import com.evmonitor.domain.TeslaConnectionRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Single source of truth for "may this user buy or upgrade to AutoSync Live?".
 * Live is currently a Tesla-only product, so the gate is "user has a Tesla
 * connection on record". Pairing status is intentionally NOT required here -
 * if the connection exists but the virtual key is unpaired, the user can still
 * pay and the post-purchase Settings flow will surface the repair-CTA.
 *
 * <p>Used by the checkout, upgrade and downgrade endpoints to reject Live-tier
 * actions from non-Tesla users with a clean 400 instead of letting Stripe take
 * the money for a feature the user cannot consume.
 */
@Service
public class LiveEligibilityService {

    private final TeslaConnectionRepository teslaConnectionRepository;

    public LiveEligibilityService(TeslaConnectionRepository teslaConnectionRepository) {
        this.teslaConnectionRepository = teslaConnectionRepository;
    }

    /**
     * @return true iff the user owns at least one Tesla connection (paired or not).
     */
    public boolean isEligibleForLive(UUID userId) {
        return teslaConnectionRepository.existsByUserId(userId);
    }
}
