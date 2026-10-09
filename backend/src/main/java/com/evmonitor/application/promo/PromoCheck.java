package com.evmonitor.application.promo;

/** Result of checking a code for one user: either an offer or the reason why not. */
public record PromoCheck(PromoOffer offer, Reason reason) {

    public enum Reason { UNKNOWN, NOT_ELIGIBLE, TRIAL_USED, ALREADY_REDEEMED, EXHAUSTED }

    static PromoCheck valid(PromoOffer offer) {
        return new PromoCheck(offer, null);
    }

    static PromoCheck rejected(Reason reason) {
        return new PromoCheck(null, reason);
    }

    public boolean valid() {
        return offer != null;
    }
}
