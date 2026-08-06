package com.evmonitor.application;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.infrastructure.persistence.JpaUserChargingProviderRepository;
import com.evmonitor.infrastructure.persistence.UserChargingProviderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

/**
 * What a charge at a given location costs, and which card paid for it.
 *
 * This is the single place that answers both questions. Every path that creates a log - manual
 * entry, connector import, public API upload, geohash backfill - goes through {@link #enrich};
 * the log form's price suggestion reads {@link #tariffAt}. Before this existed the rule was
 * implemented four times and had drifted: imports inherited a price, manual entries only a card,
 * and the suggestion shown in the form could differ from what was actually stored.
 *
 * The location is the only evidence accepted. Owning a single card says nothing about who paid
 * for a particular charge point - that assumption is what priced Tesla Supercharger sessions with
 * a supermarket tariff.
 */
@Component
@RequiredArgsConstructor
public class LocationPricing {

    /** Private charges are stored at 6 chars (~600m), public ones at 7. Below that a geohash
     *  spans kilometers and would pull in the tariff of some unrelated charge nearby. */
    private static final int MIN_GEOHASH_LENGTH = 6;

    private final EvLogRepository evLogRepository;
    private final JpaUserChargingProviderRepository chargingProviderRepository;

    /** The tariff that last applied at a location. */
    public record Tariff(BigDecimal pricePerKwh, BigDecimal sessionFeeEur, UUID chargingProviderId) {

        public Optional<BigDecimal> costFor(BigDecimal kwh) {
            if (kwh == null || kwh.signum() <= 0 || pricePerKwh == null) return Optional.empty();
            BigDecimal fee = sessionFeeEur != null ? sessionFeeEur : BigDecimal.ZERO;
            return Optional.of(pricePerKwh.multiply(kwh).add(fee).setScale(2, RoundingMode.HALF_UP));
        }
    }

    /**
     * The tariff to apply at this location, in order of evidence:
     * <ol>
     *   <li>the last charge the user recorded here that carries a price - what they actually paid</li>
     *   <li>failing that, the list price of the card they last used here - the only way a location
     *       gets its first price at all, and the only carrier of a session fee</li>
     * </ol>
     * Empty when the location is unknown or too coarse to identify a charge point.
     */
    public Optional<Tariff> tariffAt(UUID userId, String geohash, ChargingType chargingType) {
        if (geohash == null || geohash.length() < MIN_GEOHASH_LENGTH) return Optional.empty();

        Optional<EvLog> anchor = evLogRepository.findMostRecentPricedLogAtGeohash(userId, geohash);
        if (anchor.isPresent()) {
            EvLog log = anchor.get();
            // The session fee is already baked into the amount the user recorded, so it is not
            // added a second time.
            return Optional.of(new Tariff(
                    log.getCostEur().divide(log.costBasisKwh(), 4, RoundingMode.HALF_UP),
                    BigDecimal.ZERO,
                    log.getChargingProviderId()));
        }

        return evLogRepository.findMostRecentChargingProviderAtGeohash(userId, geohash)
                .flatMap(chargingProviderRepository::findById)
                .map(card -> new Tariff(priceOf(card, chargingType), card.getSessionFeeEur(), card.getId()));
    }

    /**
     * Fills in cost and card from the location's history - but only where the log has none.
     * Anything the user or the charging network already stated always wins, so this is safe to
     * call on any log and repeatedly. Returns the unchanged instance when nothing applies.
     */
    public EvLog enrich(EvLog log, UUID userId) {
        if (log.getCostEur() != null && log.getChargingProviderId() != null) return log;

        Optional<Tariff> tariff = tariffAt(userId, log.getGeohash(), log.getChargingType());
        if (tariff.isEmpty()) return log;

        var builder = log.toBuilder();
        boolean changed = false;
        if (log.getChargingProviderId() == null && tariff.get().chargingProviderId() != null) {
            builder.chargingProviderId(tariff.get().chargingProviderId());
            changed = true;
        }
        if (log.getCostEur() == null) {
            Optional<BigDecimal> cost = tariff.get().costFor(log.costBasisKwh());
            if (cost.isPresent()) {
                builder.costEur(cost.get());
                changed = true;
            }
        }
        return changed ? builder.build() : log;
    }

    /**
     * Cost of this log under a card the user picked explicitly, rather than one derived from the
     * location. Used by the retroactive "apply this tariff to all charges here" action.
     */
    public Optional<BigDecimal> costUnder(UserChargingProviderEntity card, EvLog log) {
        BigDecimal price = priceOf(card, log.getChargingType());
        if (price == null) return Optional.empty();
        return new Tariff(price, card.getSessionFeeEur(), card.getId()).costFor(log.costBasisKwh());
    }

    private static BigDecimal priceOf(UserChargingProviderEntity card, ChargingType chargingType) {
        return chargingType == ChargingType.DC ? card.getDcPricePerKwh() : card.getAcPricePerKwh();
    }
}
