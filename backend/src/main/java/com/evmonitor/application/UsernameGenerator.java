package com.evmonitor.application;

import java.util.function.Predicate;

/** Free username derived from the local part of an address, numbered on collision (3 to 20 chars). */
public final class UsernameGenerator {

    private static final int MAX_LENGTH = 20;

    private UsernameGenerator() {}

    public static String fromEmail(String email, Predicate<String> isTaken) {
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "_");
        if (base.length() < 3) base = base + "___";
        if (base.length() > MAX_LENGTH) base = base.substring(0, MAX_LENGTH);
        String candidate = base;
        int suffix = 1;
        while (isTaken.test(candidate)) {
            String s = String.valueOf(suffix++);
            candidate = base.substring(0, Math.min(base.length(), MAX_LENGTH - s.length())) + s;
        }
        return candidate;
    }
}
