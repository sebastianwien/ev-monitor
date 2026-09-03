package com.evmonitor.application.publicapi;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Die oeffentliche API bleibt bei is_public_charging zweiwertig.
 *
 * Intern ist der Ladeort seit V166 dreiwertig (oeffentlich / daheim / unbekannt),
 * damit Aggregationen unbekannte Ladungen nicht als Heimladungen zaehlen. Nach
 * aussen wuerde ein plotzliches null jedoch den Vertrag brechen - fremde Clients
 * parsen das Feld als Boolean. Unbekannt wird deshalb an der API-Grenze zu false,
 * so wie es sich vor V166 verhalten hat.
 */
class ApiSessionResponsePublicChargingTest {

    @Test
    void unknownLocation_isReportedAsFalse_notNull() {
        ApiSessionResponse response = ApiSessionResponse.fromEvLog(logWith(null));

        assertNotNull(response.isPublicCharging(),
                "Die oeffentliche API darf kein null liefern - fremde Clients parsen einen Boolean");
        assertFalse(response.isPublicCharging());
    }

    @Test
    void publicLocation_isReportedAsTrue() {
        assertTrue(ApiSessionResponse.fromEvLog(logWith(Boolean.TRUE)).isPublicCharging());
    }

    @Test
    void homeLocation_isReportedAsFalse() {
        assertFalse(ApiSessionResponse.fromEvLog(logWith(Boolean.FALSE)).isPublicCharging());
    }

    private EvLog logWith(Boolean publicCharging) {
        return EvLog.createFromInternal(
                UUID.randomUUID(), new BigDecimal("42.0"), 60, "u1hcy",
                LocalDateTime.now(), null, null, DataSource.TESLA_LIVE,
                new BigDecimal("12.34"), ChargingType.AC,
                10_000, null, null, null, null,
                publicCharging, null);
    }
}
