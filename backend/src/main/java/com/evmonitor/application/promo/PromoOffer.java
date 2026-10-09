package com.evmonitor.application.promo;

/** A promo code the user may redeem right now, with its trial length. */
public record PromoOffer(String code, int trialDays) {}
