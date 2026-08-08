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
 * Only what was actually paid at this location counts. Owning a card says nothing about who paid
 * for a particular charge point - that assumption is what priced Tesla Supercharger sessions with
 * a supermarket tariff. A card's configured price is a list price and is applied only when the
 * user explicitly asks for it, via {@link #costUnder}.
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
     * The tariff to apply at this location: the last charge the user recorded here that carries a
     * price, which is what they actually paid. Nothing else counts - a card's list price is not
     * evidence of a payment and quietly produces wrong numbers. Empty when the location is unknown,
     * too coarse to identify a charge point, or has no priced charge yet.
     */
    public Optional<Tariff> tariffAt(UUID userId, String geohash) {
        if (geohash == null || geohash.length() < MIN_GEOHASH_LENGTH) return Optional.empty();

        return evLogRepository.findMostRecentPricedLogAtGeohash(userId, geohash)
                // A session fee is already baked into the recorded amount, so it is never added
                // on top - it is spread across the kWh of that charge.
                .map(anchor -> new Tariff(
                        anchor.getCostEur().divide(anchor.costBasisKwh(), 4, RoundingMode.HALF_UP),
                        BigDecimal.ZERO,
                        anchor.getChargingProviderId()));
    }

    /**
     * Fills in cost and card from the location's history - but only where the log has none.
     * Anything the user or the charging network already stated always wins, so this is safe to
     * call on any log and repeatedly. Returns the unchanged instance when nothing applies.
     */
    public EvLog enrich(EvLog log, UUID userId) {
        if (log.getCostEur() != null && log.getChargingProviderId() != null) return log;

        Optional<Tariff> tariff = tariffAt(userId, log.getGeohash());
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
