package com.evmonitor.application.range;

import com.evmonitor.domain.RangeMath;

import java.math.BigDecimal;

/**
 * The range window of the public pages (100 to 10 % of the net battery) for the trip planner and the
 * personal calculator. The value and the range formula are owned by {@link RangeMath}.
 */
public final class RangeWindow {

    public static final BigDecimal SHARE = RangeMath.SHARE;

    private RangeWindow() {}
}
