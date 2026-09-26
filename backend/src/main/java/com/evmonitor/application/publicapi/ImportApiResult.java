package com.evmonitor.application.publicapi;

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
