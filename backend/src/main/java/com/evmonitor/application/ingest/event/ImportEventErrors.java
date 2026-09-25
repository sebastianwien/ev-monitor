package com.evmonitor.application.ingest.event;

import java.util.regex.Pattern;

/**
 * Macht aus einer Exception die Fehlerart für {@code import_event.error}: Klassenname und Meldung,
 * aber ohne Inhalte. Meldungen tragen teils Dateinamen ({@code "...abgelehnt: " + name}), Tokens aus
 * der Datei (Jackson) oder Zahlen. Alles in Anführungszeichen oder Klammern, Pfade, alles nach dem
 * ersten Doppelpunkt und alle Ziffern fliegen raus - das hält Inhalte fern und gruppiert gleiche Fehler gleich.
 */
public final class ImportEventErrors {

    static final int MAX_LENGTH = 200;

    private static final Pattern QUOTED_OR_BRACKETED = Pattern.compile("'[^']*'|\"[^\"]*\"|\\([^)]*\\)");
    private static final Pattern PATHS = Pattern.compile("\\S*[/\\\\]\\S*");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ImportEventErrors() {
    }

    public static String describe(Throwable error) {
        if (error == null) return null;
        String type = error.getClass().getSimpleName();
        String message = error.getMessage();
        if (message == null) return type;

        message = QUOTED_OR_BRACKETED.matcher(message).replaceAll(" ");
        message = PATHS.matcher(message).replaceAll(" ");
        int colon = message.indexOf(':');
        if (colon >= 0) message = message.substring(0, colon);
        message = DIGITS.matcher(message).replaceAll(" ");
        message = WHITESPACE.matcher(message).replaceAll(" ").strip();

        String described = message.isEmpty() ? type : type + ": " + message;
        return described.length() > MAX_LENGTH ? described.substring(0, MAX_LENGTH) : described;
    }
}
