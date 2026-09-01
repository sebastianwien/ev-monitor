package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * is_public_charging kennt drei Zustaende: oeffentlich, daheim, unbekannt.
 *
 * Vor V166 war die Spalte NOT NULL DEFAULT false - jedes Log ohne explizite Angabe
 * galt damit als Heimladung. Die Connectors schicken fuer AC bereits bewusst null
 * ("AC/Home stays neutral"), weil eine AC-Ladung genauso an einer oeffentlichen
 * Saeule stattfinden kann. Der Core hat dieses Wissen an der Persistenzgrenze
 * verworfen, wodurch jede Tesla-AC-Ladung zur Heimladung wurde.
 *
 * Unbekannt bleibt jetzt unbekannt. Aggregationen entscheiden selbst, wie sie damit
 * umgehen - die Heimlade-Ersparnis etwa zaehlt nur belegte Heimladungen.
 */
class EvLogPublicChargingUnknownTest {

    @Test
    void createFromInternal_withNullPublicCharging_keepsUnknown() {
        assertNull(createWith(null).getPublicCharging(),
                "null muss als 'unbekannt' erhalten bleiben statt zu false zu kollabieren");
    }

    @Test
    void createFromInternal_withTrue_isPublic() {
        assertEquals(Boolean.TRUE, createWith(Boolean.TRUE).getPublicCharging());
    }

    @Test
    void createFromInternal_withFalse_isHome() {
        assertEquals(Boolean.FALSE, createWith(Boolean.FALSE).getPublicCharging());
    }

    /** Unbekannt ist nicht oeffentlich - aber auch nicht belegt daheim. */
    @Test
    void unknown_isNeitherPublicNorConfirmedHome() {
        EvLog log = createWith(null);

        assertFalse(log.isPublicChargingConfirmed());
        assertFalse(log.isHomeChargingConfirmed());
    }

    @Test
    void publicLog_isConfirmedPublicOnly() {
        EvLog log = createWith(Boolean.TRUE);

        assertTrue(log.isPublicChargingConfirmed());
        assertFalse(log.isHomeChargingConfirmed());
    }

    @Test
    void homeLog_isConfirmedHomeOnly() {
        EvLog log = createWith(Boolean.FALSE);

        assertFalse(log.isPublicChargingConfirmed());
        assertTrue(log.isHomeChargingConfirmed());
    }

    /** Ein Patch ohne Angabe darf den bekannten Zustand nicht auf unbekannt zuruecksetzen. */
    @Test
    void withPatch_nullPublicCharging_keepsExistingValue() {
        EvLog existing = createWith(Boolean.TRUE);

        EvLog patched = existing.withPatch(
                null, null, null, null, null, null, null, null, null, null,
                null, null, /* publicCharging */ null, null, null, null, null, null, null);

        assertEquals(Boolean.TRUE, patched.getPublicCharging());
    }

    /** Ein Patch mit expliziter Angabe setzt sie durch. */
    @Test
    void withPatch_explicitFalse_overwritesPublic() {
        EvLog existing = createWith(Boolean.TRUE);

        EvLog patched = existing.withPatch(
                null, null, null, null, null, null, null, null, null, null,
                null, null, /* publicCharging */ Boolean.FALSE, null, null, null, null, null, null);

        assertEquals(Boolean.FALSE, patched.getPublicCharging());
    }

    private EvLog createWith(Boolean publicCharging) {
        return EvLog.createFromInternal(
                UUID.randomUUID(), new BigDecimal("42.0"), 60, "u1hcy",
                LocalDateTime.now(), null, null, DataSource.TESLA_LIVE,
                new BigDecimal("12.34"), ChargingType.AC,
                10_000, null, null, null, null,
                publicCharging, null);
    }
}
