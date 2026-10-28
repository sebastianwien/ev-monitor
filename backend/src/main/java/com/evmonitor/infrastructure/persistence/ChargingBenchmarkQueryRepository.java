package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.ChargingType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Rohdaten fuer den Preisvergleich je Betreiber: eine Zeile je bezahlter oeffentlicher Ladung.
 * Verdichtet wird im {@code CpoPriceBenchmarkService} - der Betreibername muss erst auf den
 * Katalog normalisiert werden (Tesla haengt den Standort an den Marker), das kann SQL nicht
 * ohne eine zweite Normalisierung.
 *
 * Preis je kWh wie in der App je Ladung: Kosten durch kWh an der Saeule, ohne sie durch kWh am
 * Fahrzeug. {@code price_per_kwh} bleibt aussen vor, die Spalte ist bei Ladungen nie gesetzt.
 */
@Repository
public class ChargingBenchmarkQueryRepository {

    private final JdbcTemplate jdbc;

    public ChargingBenchmarkQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Ladungen seit {@code since}: oeffentlich, mit Betreiber, AC oder DC, mit Kosten und fuer
     * die Statistik freigegeben. Die Karte nur, wenn sie weder privat noch geloescht ist - eine
     * private Karte ist der Heimtarif, eine geloeschte hat der Nutzer zurueckgenommen.
     * Fahrer ist der Nutzer, bei anonymisierten Autos (Kontoloeschung, user_id NULL) das Auto
     * selbst - wie bei den uebrigen Community-Zaehlungen.
     */
    public List<PublicChargePrice> publicChargePrices(LocalDateTime since) {
        return jdbc.query("""
                SELECT COALESCE(c.user_id, c.id) AS driver_id, e.cpo_name, e.charging_type,
                       e.cost_eur / COALESCE(NULLIF(e.kwh_charged, 0), e.kwh_at_vehicle) AS price,
                       p.provider_name, p.emp_name
                FROM ev_log e
                JOIN car c ON c.id = e.car_id AND c.deleted_at IS NULL
                LEFT JOIN user_charging_providers p ON p.id = e.charging_provider_id
                     AND p.deleted_at IS NULL AND p.is_private IS FALSE
                WHERE e.deleted_at IS NULL
                  AND e.is_public_charging IS TRUE
                  AND e.include_in_statistics IS TRUE
                  AND e.cpo_name IS NOT NULL
                  AND e.charging_type IN ('AC', 'DC')
                  AND e.cost_eur > 0
                  AND COALESCE(NULLIF(e.kwh_charged, 0), e.kwh_at_vehicle) > 0
                  AND e.logged_at >= ?
                """,
                (rs, i) -> new PublicChargePrice(
                        rs.getObject("driver_id", UUID.class),
                        rs.getString("cpo_name"),
                        ChargingType.valueOf(rs.getString("charging_type")),
                        rs.getBigDecimal("price"),
                        rs.getString("provider_name"),
                        rs.getString("emp_name")),
                since);
    }

    /** Eine bezahlte Ladung; {@code cardName}/{@code cardEmp} null ohne (zaehlbare) Karte. */
    public record PublicChargePrice(UUID driverId, String cpoName, ChargingType chargingType,
                                    BigDecimal pricePerKwh, String cardName, String cardEmp) {}
}
