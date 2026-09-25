package com.evmonitor.application.ingest;

/**
 * Über welche alte Tür ein Import hereinkommt. Die Regeln hängen heute an der Tür, nicht an der
 * Quelle: {@code EU_DATA_ACT_SYNC} kommt über beide und wird verschieden behandelt (Coins,
 * Vererbung, SoH-Event). Die Policy ist deshalb nach Quelle und Tür geschlüsselt. Fällt weg, sobald
 * jede Quelle nur noch eine Tür nutzt (Entscheidung 25.09.2026, frühestens nach R2d/R4).
 */
public enum IngestDoor {
    /** Tür 1: Batch aus Public API, Datei-Importen und VW-Historie/Feed ({@code PublicApiImportService}). */
    IMPORT_BATCH,
    /** Tür 2: Einzelsatz aus Connectors und Wallbox ({@code /api/internal/logs}). */
    CONNECTOR_PUSH
}
