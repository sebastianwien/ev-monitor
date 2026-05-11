package com.evmonitor.domain.xpeng;

/** Helpers around VIN handling (formatting, masking). */
public final class VinUtils {
    private VinUtils() {}

    /** Returns a masked VIN: first 4 chars visible, rest replaced by {@code *}. */
    public static String mask(String vin) {
        if (vin == null || vin.length() < 6) return "***";
        return vin.substring(0, 4) + "*".repeat(vin.length() - 4);
    }
}
