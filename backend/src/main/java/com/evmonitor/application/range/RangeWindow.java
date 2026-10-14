package com.evmonitor.application.range;

import java.math.BigDecimal;

/**
 * What "range" means on the public pages: the distance from 100 to 10 % of the net battery.
 * Model list, model page, personal range result and embed tile all use this share.
 */
public final class RangeWindow {

    public static final BigDecimal SHARE = new BigDecimal("0.9");

    private RangeWindow() {}
}
