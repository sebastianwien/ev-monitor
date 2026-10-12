package com.evmonitor.application.charging;

import java.math.BigDecimal;

/**
 * Mean DC power a model reaches on a long-distance stop from a low state of charge, and where the
 * figure comes from. Built by FastChargePowerTable; every caller (personal calculator, model page
 * story, ranking, partner tile) reads it from there.
 *
 * @param sessionCount qualifying DC sessions of this model, also when they are too few to count
 */
public record FastChargePower(BigDecimal kw, Source source, int sessionCount) {

    public enum Source {
        /** p75 of the model's own short DC sessions, from FastChargePowerTable.MIN_SESSIONS on */
        MEASURED,
        /** median of the measured models, for a model with too few own sessions */
        FLEET_MEDIAN,
        /** no model measured at all (empty database) */
        DEFAULT
    }

    /** Median of the measured models on 12.10.2026 (28 models, 64 to 249 kW); only used without any measurement. */
    public static final BigDecimal DEFAULT_KW = new BigDecimal("104");

    public static FastChargePower measured(BigDecimal kw, int sessionCount) {
        return new FastChargePower(kw, Source.MEASURED, sessionCount);
    }

    public static FastChargePower fallback() {
        return new FastChargePower(DEFAULT_KW, Source.DEFAULT, 0);
    }

    public boolean isMeasured() {
        return source == Source.MEASURED;
    }
}
