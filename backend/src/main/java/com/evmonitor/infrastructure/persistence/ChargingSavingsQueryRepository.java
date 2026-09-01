package com.evmonitor.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Abfragen fuer die Heimlade-Ersparnis.
 *
 * Durchgaengig auf {@code is_public_charging IS TRUE/FALSE} statt auf Negation: seit V166
 * ist die Spalte dreiwertig, und ein Log ohne Angabe zum Ladeort darf weder als Heim- noch
 * als oeffentliche Ladung zaehlen. Genau dieser Fall - eine oeffentliche AC-Ladung, die als
 * Heimladung gilt - wuerde den Heimpreis nach oben ziehen und die Ersparnis kleinrechnen.
 */
@Repository
public class ChargingSavingsQueryRepository {

    /** Bevorzugt den exakt hinterlegten Satz, sonst aus Kosten und Menge gerechnet. */
    private static final String PRICE_EXPR =
            "COALESCE(e.price_per_kwh, e.cost_eur / NULLIF(e.kwh_charged, 0))";

    private final JdbcTemplate jdbc;

    public ChargingSavingsQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Preise der eigenen Heimladungen im rollierenden Jahr.
     *
     * Nulltarife bleiben drin: PV-Ueberschuss kostet nichts, und ein Filter auf "> 0"
     * wuerde auf Prod 1.322 Logs ueber 86 Fahrzeuge verwerfen - ausgerechnet die Gruppe
     * mit der groessten Ersparnis.
     */
    public List<BigDecimal> ownHomePrices(UUID userId) {
        return jdbc.queryForList("""
                SELECT %s AS price
                FROM ev_log e JOIN car c ON c.id = e.car_id
                WHERE c.user_id = ?
                  AND e.is_public_charging IS FALSE
                  AND e.logged_at > now() - interval '12 months'
                  AND e.kwh_charged > 0
                  AND (e.price_per_kwh IS NOT NULL OR e.cost_eur IS NOT NULL)
                """.formatted(PRICE_EXPR), BigDecimal.class, userId);
    }

    /** Preise der eigenen oeffentlichen Ladungen im rollierenden Jahr. */
    public List<BigDecimal> ownPublicPrices(UUID userId) {
        return jdbc.queryForList("""
                SELECT %s AS price
                FROM ev_log e JOIN car c ON c.id = e.car_id
                WHERE c.user_id = ?
                  AND e.is_public_charging IS TRUE
                  AND e.logged_at > now() - interval '12 months'
                  AND e.kwh_charged > 0 AND e.cost_eur > 0
                """.formatted(PRICE_EXPR), BigDecimal.class, userId);
    }

    /** Belegte Heim-kWh im rollierenden Jahr. */
    public BigDecimal homeKwhLast12Months(UUID userId) {
        BigDecimal kwh = jdbc.queryForObject("""
                SELECT COALESCE(SUM(e.kwh_charged), 0)
                FROM ev_log e JOIN car c ON c.id = e.car_id
                WHERE c.user_id = ?
                  AND e.is_public_charging IS FALSE
                  AND e.logged_at > now() - interval '12 months'
                """, BigDecimal.class, userId);
        return kwh != null ? kwh : BigDecimal.ZERO;
    }

    /** Haeufigster Ladeort der eigenen Heimladungen - Anker fuer die Regionsstufe. */
    public String homeGeohash(UUID userId) {
        List<String> rows = jdbc.queryForList("""
                SELECT e.geohash
                FROM ev_log e JOIN car c ON c.id = e.car_id
                WHERE c.user_id = ? AND e.is_public_charging IS FALSE AND e.geohash IS NOT NULL
                GROUP BY e.geohash ORDER BY count(*) DESC LIMIT 1
                """, String.class, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Median oeffentlicher Ladepreise in einer Geohash-Zelle, auf das Land des Nutzers
     * begrenzt. Ohne den Landesfilter wuerde eine Zelle an der Grenze deutsche und
     * daenische Preise mischen.
     *
     * @return {@code null}, wenn die Zelle das Mindestmass nicht erreicht - die Stufe
     *         faellt dann geraeuschlos durch
     */
    public RegionMedian regionMedian(String geohashPrefix, String country, int minLogs, int minCars) {
        List<RegionMedian> rows = jdbc.query("""
                SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY %s) AS median,
                       count(*)::int AS n
                FROM ev_log e
                JOIN car c ON c.id = e.car_id
                JOIN app_user u ON u.id = c.user_id
                WHERE e.is_public_charging IS TRUE
                  AND e.logged_at > now() - interval '12 months'
                  AND e.kwh_charged > 0 AND e.cost_eur > 0
                  AND e.geohash LIKE ? || '%%'
                  AND u.country IS NOT DISTINCT FROM ?
                  AND %s BETWEEN 0.01 AND 2.0
                HAVING count(*) >= ? AND count(DISTINCT e.car_id) >= ?
                """.formatted(PRICE_EXPR, PRICE_EXPR),
                (rs, i) -> new RegionMedian(rs.getBigDecimal("median"), rs.getInt("n")),
                geohashPrefix, country, minLogs, minCars);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Median oeffentlicher Ladepreise im Land des Nutzers. */
    public RegionMedian countryMedian(String country, int minLogs) {
        List<RegionMedian> rows = jdbc.query("""
                SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY %s) AS median,
                       count(*)::int AS n
                FROM ev_log e
                JOIN car c ON c.id = e.car_id
                JOIN app_user u ON u.id = c.user_id
                WHERE e.is_public_charging IS TRUE
                  AND e.logged_at > now() - interval '12 months'
                  AND e.kwh_charged > 0 AND e.cost_eur > 0
                  AND u.country IS NOT DISTINCT FROM ?
                  AND %s BETWEEN 0.01 AND 2.0
                HAVING count(*) >= ?
                """.formatted(PRICE_EXPR, PRICE_EXPR),
                (rs, i) -> new RegionMedian(rs.getBigDecimal("median"), rs.getInt("n")),
                country, minLogs);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Land, Preis der Heimstrom-Ladekarte und hinterlegte Investition.
     *
     * Die Karte muss heute gueltig sein - Strompreise aendern sich, und
     * user_charging_providers bildet das ueber active_from / active_until bereits ab.
     */
    public UserProfileRow profile(UUID userId) {
        List<UserProfileRow> rows = jdbc.query("""
                SELECT u.country,
                       u.home_investment_eur,
                       (SELECT p.ac_price_per_kwh
                          FROM user_charging_providers p
                         WHERE p.user_id = u.id AND p.is_home AND p.deleted_at IS NULL
                           AND p.active_from <= CURRENT_DATE
                           AND (p.active_until IS NULL OR p.active_until >= CURRENT_DATE)
                         ORDER BY p.active_from DESC LIMIT 1) AS home_price
                FROM app_user u WHERE u.id = ?
                """,
                (rs, i) -> new UserProfileRow(
                        rs.getString("country"),
                        rs.getBigDecimal("home_price"),
                        rs.getBigDecimal("home_investment_eur")),
                userId);
        return rows.isEmpty() ? new UserProfileRow(null, null, null) : rows.get(0);
    }

    public record UserProfileRow(String country, BigDecimal homeCardPricePerKwh, BigDecimal investmentEur) {}

    /** Jahre seit der ersten Ladung des Nutzers - Basis fuer die kumulierte Ersparnis. */
    public BigDecimal usageYears(UUID userId) {
        BigDecimal years = jdbc.queryForObject("""
                SELECT GREATEST(
                         EXTRACT(EPOCH FROM (now() - MIN(e.logged_at))) / (365.25 * 86400),
                         0.0)::numeric
                FROM ev_log e JOIN car c ON c.id = e.car_id
                WHERE c.user_id = ?
                """, BigDecimal.class, userId);
        return years != null ? years : BigDecimal.ZERO;
    }

    public record RegionMedian(BigDecimal median, int sampleSize) {}
}
