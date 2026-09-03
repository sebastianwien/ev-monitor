package com.evmonitor.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Abfragen fuer die Heimlade-Ersparnis.
 *
 * Ohne Zeitfenster: die Kachel spricht durchgaengig ueber die gesamte Zeit, in der daheim
 * geladen wurde. Ein rollierendes Jahr an einzelnen Stellen wuerde die Stichprobenzahl in
 * der Basiszeile gegen den Betrag darueber laufen lassen - und die Kachel bei jemandem
 * verschwinden lassen, der vor ueber einem Jahr aufgehoert hat, obwohl die Jahresreihe
 * Zahlen haette.
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
     * Gewichteter Heimpreis des rollierenden Jahres: Summe Kosten durch Summe kWh.
     *
     * Gewichtet, weil ein 2-kWh-Log nicht so schwer wiegen darf wie ein 60-kWh-Log.
     * cost_eur hat Vorrang vor price_per_kwh * kwh, weil es den tatsaechlich gezahlten
     * Gesamtbetrag samt Session-Gebuehr traegt. Nulltarife bleiben drin - PV-Ueberschuss
     * kostet nichts, und ein Filter auf "> 0" wuerde ausgerechnet der Gruppe mit der
     * groessten Ersparnis die kleinste Zahl zeigen.
     */
    public WeightedPrice homeWeightedPrice(UUID userId) {
        List<WeightedPrice> rows = jdbc.query("""
                SELECT SUM(COALESCE(e.cost_eur, e.price_per_kwh * e.kwh_charged))
                         / NULLIF(SUM(e.kwh_charged), 0) AS preis,
                       count(*)::int AS n
                FROM ev_log e JOIN car c ON c.id = e.car_id
                WHERE c.user_id = ?
                  AND e.is_public_charging IS FALSE
                  AND e.kwh_charged > 0
                  AND (e.price_per_kwh IS NOT NULL OR e.cost_eur IS NOT NULL)
                """,
                (rs, i) -> new WeightedPrice(rs.getBigDecimal("preis"), rs.getInt("n")),
                userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public record WeightedPrice(BigDecimal pricePerKwh, int sampleSize) {}

    /** Preise der eigenen oeffentlichen Ladungen im rollierenden Jahr. */
    public List<BigDecimal> ownPublicPrices(UUID userId) {
        return jdbc.queryForList("""
                SELECT %s AS price
                FROM ev_log e JOIN car c ON c.id = e.car_id
                WHERE c.user_id = ?
                  AND e.is_public_charging IS TRUE
                  AND e.kwh_charged > 0 AND e.cost_eur > 0
                """.formatted(PRICE_EXPR), BigDecimal.class, userId);
    }

    /**
     * Bisherige Laufzeit in Monaten, gerechnet ab der ersten bepreisten Heimladung.
     *
     * Nenner der Restlaufzeit: Gesamtersparnis durch Monate ergibt die Rate. Breiter
     * abgestuetzt als ein einzelnes Jahr und unempfindlich gegen einen schwachen Winter.
     */
    public BigDecimal monthsOfHomeCharging(UUID userId) {
        BigDecimal months = jdbc.queryForObject("""
                SELECT GREATEST(
                         EXTRACT(EPOCH FROM (now() - MIN(e.logged_at))) / (365.25 * 86400 / 12),
                         0.0)::numeric
                FROM ev_log e JOIN car c ON c.id = e.car_id
                WHERE c.user_id = ?
                  AND e.is_public_charging IS FALSE
                  AND e.kwh_charged > 0
                  AND (e.price_per_kwh IS NOT NULL OR e.cost_eur IS NOT NULL)
                """, BigDecimal.class, userId);
        return months != null ? months : BigDecimal.ZERO;
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
                  AND e.kwh_charged > 0 AND e.cost_eur > 0
                  AND u.country IS NOT DISTINCT FROM ?
                  AND %s BETWEEN 0.01 AND 2.0
                HAVING count(*) >= ?
                """.formatted(PRICE_EXPR, PRICE_EXPR),
                (rs, i) -> new RegionMedian(rs.getBigDecimal("median"), rs.getInt("n")),
                country, minLogs);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Land und hinterlegte Investition des Nutzers. */
    public UserProfileRow profile(UUID userId) {
        List<UserProfileRow> rows = jdbc.query("""
                SELECT u.country,
                       u.home_investment_eur
                FROM app_user u WHERE u.id = ?
                """,
                (rs, i) -> new UserProfileRow(
                        rs.getString("country"),
                        rs.getBigDecimal("home_investment_eur")),
                userId);
        return rows.isEmpty() ? new UserProfileRow(null, null) : rows.get(0);
    }

    public record UserProfileRow(String country, BigDecimal investmentEur) {}

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
