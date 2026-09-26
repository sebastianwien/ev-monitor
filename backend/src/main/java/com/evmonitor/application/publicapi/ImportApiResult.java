package com.evmonitor.application.publicapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * @param skippedDeleted Teilmenge von {@code skipped}: der Zeitpunkt ist durch einen gelöschten Eintrag belegt.
 *                       Gelöscht bleibt gelöscht, auch bei erneutem Upload.
 */
public record ImportApiResult(int imported, int skipped, int errors, int warnings, List<ImportedSession> results,
                              int skippedDeleted) {

    public ImportApiResult(int imported, int skipped, int errors, int warnings, List<ImportedSession> results) {
        this(imported, skipped, errors, warnings, results, 0);
    }

    /** Hinweis für API-Nutzer, warum ein Re-Upload nichts anlegt. */
    @JsonProperty("hint")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String hint() {
        return skippedDeleted > 0
                ? skippedDeleted + " Ladevorgang/Ladevorgänge hast du gelöscht, sie werden nicht erneut importiert. "
                  + "Wiederherstellen oder endgültig entfernen unter Ladevorgänge > Gelöscht."
                : null;
    }

    public record ImportedSession(
            @JsonProperty("date") String date,
            @JsonProperty("id") UUID id
    ) {}

    public static ImportApiResult withoutIds(int imported, int skipped, int errors) {
        return new ImportApiResult(imported, skipped, errors, 0, List.of());
    }

    public static ImportApiResult withoutIds(int imported, int skipped, int errors, int warnings) {
        return new ImportApiResult(imported, skipped, errors, warnings, List.of());
    }
}
