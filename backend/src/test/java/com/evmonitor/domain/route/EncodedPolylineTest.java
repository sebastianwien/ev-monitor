package com.evmonitor.domain.route;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Der Dekoder liest, was der connectors-service und openrouteservice schreiben. Er ist nur
 * dann richtig, wenn er das dokumentierte Referenzbeispiel des Formats trifft - deshalb
 * steht es hier als erster Fall.
 */
class EncodedPolylineTest {

    /** Standardbeispiel des Encoded Polyline Algorithm Format. */
    private static final String REFERENCE = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";

    @Test
    void decodesTheReferenceExampleOfTheFormat() {
        List<double[]> points = EncodedPolyline.decode(REFERENCE);

        assertThat(points).hasSize(3);
        assertThat(points.get(0)[0]).isEqualTo(38.5, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(points.get(0)[1]).isEqualTo(-120.2, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(points.get(1)[0]).isEqualTo(40.7, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(points.get(1)[1]).isEqualTo(-120.95, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(points.get(2)[0]).isEqualTo(43.252, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(points.get(2)[1]).isEqualTo(-126.453, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void emptyInputIsAnEmptyLine() {
        assertThat(EncodedPolyline.decode(null)).isEmpty();
        assertThat(EncodedPolyline.decode("")).isEmpty();
        assertThat(EncodedPolyline.decode("   ")).isEmpty();
    }

    @Test
    void brokenInputYieldsNothingInsteadOfThrowing() {
        // Ein abgeschnittener String endet mitten in einer Zahl - der Rest ist unbrauchbar,
        // aber ein Fehler an dieser Stelle wuerde die Fahrt kosten, nicht nur ihre Linie.
        assertThat(EncodedPolyline.decode("_p~iF~ps|U_ulL")).hasSize(1);
        assertThat(EncodedPolyline.decode("§§§")).isEmpty();
    }

    @Test
    void thinsToTheRequestedSizeKeepingBothEnds() {
        List<double[]> points = EncodedPolyline.decode(REFERENCE);

        List<double[]> thinned = EncodedPolyline.thin(points, 2);

        assertThat(thinned).hasSize(2);
        assertThat(thinned.get(0)).isEqualTo(points.get(0));
        assertThat(thinned.get(1)).isEqualTo(points.get(2));
    }

    @Test
    void thinningLeavesShortLinesAlone() {
        List<double[]> points = EncodedPolyline.decode(REFERENCE);

        assertThat(EncodedPolyline.thin(points, 50)).isSameAs(points);
    }
}
