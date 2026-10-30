package com.evmonitor.domain;

import com.evmonitor.testutil.AbstractPostgresIT;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Besonderheiten liegen als Postgres-Array in ev_trip.load_extras; ohne Angabe ist die Liste leer, nie null. */
class EvTripLoadExtrasPostgresIT extends AbstractPostgresIT {

    @Test
    void loadExtrasSurviveRoundTripAndDefaultToEmpty() {
        EvTrip plain = tripRepository.save(trip());
        EvTrip withExtras = trip();
        withExtras.setLoadExtras(List.of("TRAILER", "ROOF_BOX"));
        withExtras = tripRepository.save(withExtras);

        assertThat(tripRepository.findById(plain.getId()).orElseThrow().getLoadExtras()).isEmpty();
        assertThat(tripRepository.findById(withExtras.getId()).orElseThrow().getLoadExtras())
                .containsExactly("TRAILER", "ROOF_BOX");
    }

    private static EvTrip trip() {
        return EvTrip.builder()
                .userId(null)
                .carId(UUID.randomUUID())
                .dataSource(EvTrip.DATA_SOURCE_API_UPLOAD)
                .tripStartedAt(OffsetDateTime.now().minusHours(2))
                .tripEndedAt(OffsetDateTime.now().minusHours(1))
                .build();
    }
}
