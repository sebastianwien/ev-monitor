package com.evmonitor.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceStationCellsTest {

    /** Die Karte lädt je Ausschnitt bis zu zwölf Zellen; 300 neue Zellen je Stunde reichen für langes Schieben. */
    @Test
    void dreihundertNeueZellenJeNutzerUndStunde() {
        RateLimitService service = new RateLimitService();
        ReflectionTestUtils.setField(service, "enabled", true);

        for (int i = 0; i < 300; i++) assertThat(service.tryConsumeStationCells("user:a")).isTrue();
        assertThat(service.tryConsumeStationCells("user:a")).isFalse();
        assertThat(service.tryConsumeStationCells("user:b")).isTrue();
    }

    /** Eigener Topf: das Schieben der Karte leert nicht den Umkreis-Topf des Formulars. */
    @Test
    void eigenerTopfNebenDerUmkreissuche() {
        RateLimitService service = new RateLimitService();
        ReflectionTestUtils.setField(service, "enabled", true);

        for (int i = 0; i < 300; i++) service.tryConsumeStationCells("user:a");
        assertThat(service.tryConsumeCpoLookup("user:a")).isTrue();
    }
}
