package com.evmonitor.application.tessie;

public record TessieImportResult(
        int drivesImported,
        int chargesImported,
        int skipped,
        int evLogsCreated,
        int evTripsCreated
) {
    public static TessieImportResult empty() {
        return new TessieImportResult(0, 0, 0, 0, 0);
    }
}
