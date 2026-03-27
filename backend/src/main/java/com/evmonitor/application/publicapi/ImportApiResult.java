package com.evmonitor.application.publicapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record ImportApiResult(int imported, int skipped, int errors, int warnings, List<ImportedSession> results) {

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
