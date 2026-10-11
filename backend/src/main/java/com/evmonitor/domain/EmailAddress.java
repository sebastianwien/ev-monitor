package com.evmonitor.domain;

import java.util.Locale;

/** Canonical form of an address for lookups and rate-limit keys: trimmed, lower case. */
public final class EmailAddress {

    private EmailAddress() {}

    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
