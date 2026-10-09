package com.evmonitor.domain.rating;

/**
 * Lebenslauf einer Textfassung. Öffentlich ist nur {@link #APPROVED}. Eine neue Fassung
 * verdrängt eine ältere offene ({@link #SUPERSEDED}), eine freigegebene bleibt sichtbar,
 * bis die neue freigegeben ist.
 */
public enum ReviewStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUPERSEDED
}
