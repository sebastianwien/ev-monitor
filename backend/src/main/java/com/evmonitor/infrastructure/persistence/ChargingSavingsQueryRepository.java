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

    /**
     * Heimladen je Kalenderjahr: geladene kWh und tatsaechlich gezahlte Kosten.
     *
     * Nur Jahre mit bepreisten Ladungen - ohne Kosten laesst sich nichts vergleichen.
     * Nulltarife bleiben drin, PV-Ueberschuss ist ein echter Wert.
     */
    public List<YearTotals> homeYearTotals(UUID userId) {
        return jdbc.query("""
                SELECT EXTRACT(YEAR FROM e.logged_at)::int AS jahr,
                       SUM(e.kwh_charged) AS kwh,
                       -- cost_eur zuerst: das ist der tatsaechlich gezahlte Gesamtbetrag
                       -- samt Session-Gebuehr. price_per_kwh traegt nur den Arbeitspreis,
                       -- danach gerechnet faellt die Gebuehr unter den Tisch und die
                       -- ausgewiesene Ersparnis waere zu hoch.
                       SUM(COALESCE(e.cost_eur, e.price_per_kwh * e.kwh_charged)) AS kosten
                FROM ev_log e JOIN car c ON c.id = e.car_id
                WHERE c.user_id = ?
                  AND e.is_public_charging IS FALSE
                  AND e.kwh_charged > 0
                  AND (e.price_per_kwh IS NOT NULL OR e.cost_eur IS NOT NULL)
                GROUP BY 1 ORDER BY 1
                """,
                (rs, i) -> new YearTotals(rs.getInt("jahr"), rs.getBigDecimal("kwh"), rs.getBigDecimal("kosten")),
                userId);
    }

    /**
     * Oeffentliches Preisniveau je Kalenderjahr - erst die eigenen Ladungen, sonst das
     * Land. Ein historisches Jahr mit dem heutigen Median zu vergleichen waere falsch:
     * die oeffentlichen Preise haben sich seit 2022 erheblich bewegt.
     */
    public List<YearPrice> publicPriceByYear(UUID userId, String country, int minOwnLogs, int minCountryLogs) {
        return jdbc.query("""
                WITH eigene AS (
                  SELECT EXTRACT(YEAR FROM e.logged_at)::int AS jahr,
                         percentile_cont(0.5) WITHIN GROUP (ORDER BY %s) AS preis,
                         count(*)::int AS n
                  FROM ev_log e JOIN car c ON c.id = e.car_id
                  WHERE c.user_id = ? AND e.is_public_charging IS TRUE
                    AND e.kwh_charged > 0 AND e.cost_eur > 0
                    AND %s BETWEEN 0.01 AND 2.0
                  GROUP BY 1
                ), land AS (
                  SELECT EXTRACT(YEAR FROM e.logged_at)::int AS jahr,
                         percentile_cont(0.5) WITHIN GROUP (ORDER BY %s) AS preis,
                         count(*)::int AS n
                  FROM ev_log e JOIN car c ON c.id = e.car_id JOIN app_user u ON u.id = c.user_id
                  WHERE e.is_public_charging IS TRUE
                    AND e.kwh_charged > 0 AND e.cost_eur > 0
                    AND u.country IS NOT DISTINCT FROM ?
                    AND %s BETWEEN 0.01 AND 2.0
                  GROUP BY 1
                )
                SELECT COALESCE(e.jahr, l.jahr) AS jahr,
                       CASE WHEN e.n >= ? THEN e.preis
                            WHEN l.n >= ? THEN l.preis END AS preis,
                       CASE WHEN e.n >= ? THEN 'OWN_PUBLIC' ELSE 'COUNTRY' END AS quelle
                FROM eigene e FULL OUTER JOIN land l ON l.jahr = e.jahr
                ORDER BY 1
                """.formatted(PRICE_EXPR, PRICE_EXPR, PRICE_EXPR, PRICE_EXPR),
                (rs, i) -> new YearPrice(rs.getInt("jahr"), rs.getBigDecimal("preis"), rs.getString("quelle")),
                userId, country, minOwnLogs, minCountryLogs, minOwnLogs);
    }

    public record YearTotals(int year, BigDecimal kwh, BigDecimal paidEur) {}

    public record YearPrice(int year, BigDecimal pricePerKwh, String source) {}

    public record RegionMedian(BigDecimal median, int sampleSize) {}
}
