package com.evmonitor.application.ingest;

import com.evmonitor.application.CoinLogService.CoinEvent;
import com.evmonitor.domain.DataSource;

import java.time.Duration;

/**
 * Die Policy-Tabelle: einzige Stelle, an der eine Quelle über Regeln entscheidet. Stand R2a bildet
 * sie die gemessenen Regeln der beiden alten Türen ab, Verhalten identisch.
 */
public final class IngestPolicies {

    /**
     * Das VW-Portal liefert denselben Ladevorgang im 15-Minuten-Feed und im Historien-Export mit leicht
     * abweichendem Startzeitpunkt - daher für den VW-Upload ein Fenster um die Minute.
     */
    static final Duration VW_UPLOAD_DEDUP_TOLERANCE = Duration.ofMinutes(3);

    private IngestPolicies() {}

    public static IngestPolicy forSource(DataSource source, IngestDoor door) {
        return switch (door) {
            // Kein Erben: Bulk-Importe kommen historisch und u.U. nicht chronologisch - "letzter Wert
            // in der DB" wäre dann der zukünftige Wert und würde still über die Historie propagieren.
            case IMPORT_BATCH -> new IngestPolicy(batchDedupWindow(source), true, false,
                    CoinEvent.API_UPLOAD_LOG, reportsVehicleSideKwh(source), true, false);
            // Live-Daten tragen weder Reifen noch Strecke; ohne Erben kippt die "letzten Wert
            // übernehmen"-UX still auf SUMMER/COMBINED.
            case CONNECTOR_PUSH -> new IngestPolicy(Duration.ZERO, false, true,
                    pushCoinEvent(source), false, false, true);
        };
    }

    private static Duration batchDedupWindow(DataSource source) {
        return switch (source) {
            case EU_DATA_ACT_IMPORT -> VW_UPLOAD_DEDUP_TOLERANCE;
            default -> Duration.ZERO;
        };
    }

    /** Tronity meldet im kWh-Feld die Energie im Akku, nicht die am Netz. */
    private static boolean reportsVehicleSideKwh(DataSource source) {
        return switch (source) {
            case TRONITY_IMPORT -> true;
            default -> false;
        };
    }

    /** Watt gibt es bisher nur für Tesla-Syncs; go-e und OCPP sind noch offen. */
    private static CoinEvent pushCoinEvent(DataSource source) {
        return switch (source) {
            case TESLA_FLEET_IMPORT, TESLA_LIVE -> CoinEvent.TESLA_DAILY_LOG;
            default -> null;
        };
    }
}
