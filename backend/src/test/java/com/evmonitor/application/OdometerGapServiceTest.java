package com.evmonitor.application;

import com.evmonitor.domain.TripOdometerSpan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Lücken im Kilometerstand zwischen zwei aufgezeichneten Fahrten. Reine Funktion: Fahrten rein,
 * Lücken raus. Fenster ist hier immer der ganze Testzeitraum, außer im Anker-Test.
 */
class OdometerGapServiceTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-09-17T08:00:00+02:00");
    private static final OffsetDateTime FROM = T0.minusYears(1);
    private static final OffsetDateTime TO = T0.plusYears(1);

    /** Fahrt ab T0 + startHours, eine Stunde lang. */
    private static TripOdometerSpan trip(String source, double startHours, String odoStart, String odoEnd) {
        OffsetDateTime start = T0.plusMinutes(Math.round(startHours * 60));
        return new TripOdometerSpan(start, start.plusHours(1),
                odoStart == null ? null : new BigDecimal(odoStart),
                odoEnd == null ? null : new BigDecimal(odoEnd),
                source);
    }

    private static List<OdometerGap> detect(TripOdometerSpan... trips) {
        return OdometerGapService.detect(List.of(trips), FROM, TO);
    }

    @Test
    void jumpBelowOneKilometer_isNoGap() {
        assertThat(detect(
                trip("TESSIE", 0, "100.0", "150.0"),
                trip("TESSIE", 5, "150.9", "160.0"))).isEmpty();
    }

    @Test
    void jumpOfExactlyOneKilometer_isAGap() {
        List<OdometerGap> gaps = detect(
                trip("TESSIE", 0, "100.0", "150.0"),
                trip("TESSIE", 5, "151.0", "160.0"));

        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0).distanceKm()).isEqualByComparingTo("1.0");
    }

    @Test
    void gapRunsFromEndOfPreviousTripToStartOfNextTrip() {
        List<OdometerGap> gaps = detect(
                trip("TESLA_LIVE", 0, "100.0", "150.0"),
                trip("TESLA_LIVE", 5, "173.7", "180.0"));

        assertThat(gaps).containsExactly(new OdometerGap(
                T0.plusHours(1), T0.plusHours(5), new BigDecimal("23.7")));
    }

    @Test
    void twoOverlappingSources_doNotCreateAGap() {
        // Tessie und Tesla live zeichnen dieselbe Fahrt auf, mit leicht anderen Ständen.
        assertThat(detect(
                trip("TESSIE", 0, "100.0", "150.0"),
                trip("TESLA_LIVE", 0.1, "100.2", "149.6"),
                trip("TESSIE", 5, "150.3", "160.0"))).isEmpty();
    }

    @Test
    void odometerGoingBackwards_isNoGap() {
        assertThat(detect(
                trip("TESSIE", 0, "100.0", "150.0"),
                trip("TESSIE", 5, "140.0", "149.0"))).isEmpty();
    }

    @Test
    void tripWithoutOdometer_isSkipped() {
        List<OdometerGap> gaps = detect(
                trip("TESSIE", 0, "100.0", "150.0"),
                trip("TESSIE", 3, null, null),
                trip("TESSIE", 5, "170.0", "180.0"));

        assertThat(gaps).containsExactly(new OdometerGap(
                T0.plusHours(1), T0.plusHours(5), new BigDecimal("20.0")));
    }

    @Test
    void gapFasterThan150KmhOnAverage_isHidden() {
        // 151 km in einer Stunde
        assertThat(detect(
                trip("TESSIE", 0, "100.0", "150.0"),
                trip("TESSIE", 2, "301.0", "310.0"))).isEmpty();
    }

    @Test
    void gapOfExactly150KmhOnAverage_isShown() {
        assertThat(detect(
                trip("TESSIE", 0, "100.0", "150.0"),
                trip("TESSIE", 2, "300.0", "310.0"))).hasSize(1);
    }

    @Test
    void gapWithTimeOverlap_isHidden() {
        // Nächste Fahrt beginnt, bevor die vorige endet, der Stand springt trotzdem.
        assertThat(detect(
                trip("TESSIE", 0, "100.0", "150.0"),
                trip("TESSIE", 0.5, "160.0", "170.0"))).isEmpty();
    }

    @Test
    void anchorTripBeforeWindow_countsForTheFirstGapInside() {
        TripOdometerSpan anchor = trip("TESSIE", 0, "100.0", "150.0");
        TripOdometerSpan inside = trip("TESSIE", 48, "180.0", "190.0");
        OffsetDateTime windowFrom = T0.plusDays(1);

        List<OdometerGap> gaps = OdometerGapService.detect(List.of(anchor, inside), windowFrom, TO);

        assertThat(gaps).containsExactly(new OdometerGap(
                T0.plusHours(1), T0.plusHours(48), new BigDecimal("30.0")));
    }

    @Test
    void gapEndingBeforeWindow_isNotReturned() {
        List<OdometerGap> gaps = OdometerGapService.detect(List.of(
                trip("TESSIE", 0, "100.0", "150.0"),
                trip("TESSIE", 5, "170.0", "180.0")), T0.plusDays(1), TO);

        assertThat(gaps).isEmpty();
    }

    @Test
    void gapBeforeSmartcarTrip_isNotShown() {
        assertThat(detect(
                trip("TESSIE", 0, "100.0", "150.0"),
                trip("SMARTCAR_LIVE", 5, "170.0", "180.0"))).isEmpty();
    }

    @Test
    void smartcarTrip_fillsTheGapBeforeATessieTrip() {
        assertThat(detect(
                trip("TESSIE", 0, "100.0", "150.0"),
                trip("SMARTCAR_LIVE", 3, "150.0", "170.0"),
                trip("TESSIE", 5, "170.0", "180.0"))).isEmpty();
    }

    @Test
    void userCreatedAndUnknownSources_doNotThrowAndShowNoGap() {
        assertThatCode(() -> assertThat(detect(
                trip("TESSIE", 0, "100.0", "150.0"),
                trip("USER_CREATED", 5, "170.0", "180.0"),
                trip("SOMETHING_NEW", 10, "200.0", "210.0"),
                trip(null, 15, "230.0", "240.0"))).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void tripsArriveUnsorted_areSortedByStart() {
        List<OdometerGap> gaps = detect(
                trip("TESSIE", 5, "173.7", "180.0"),
                trip("TESSIE", 0, "100.0", "150.0"));

        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0).distanceKm()).isEqualByComparingTo("23.7");
    }
}
