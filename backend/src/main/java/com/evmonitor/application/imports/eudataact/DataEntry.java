package com.evmonitor.application.imports.eudataact;

import java.time.OffsetDateTime;

/** Ein Rohwert aus dem EU-Data-Act-Export. */
record DataEntry(String field, String value, OffsetDateTime timestamp) {

    /** Werte kommen teils mit Whitespace ("3.0 "). Nicht-numerische Werte -> 0.0. */
    double asDouble() {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    boolean isNumeric() {
        try {
            Double.parseDouble(value.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
