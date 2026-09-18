package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DSGVO-Anonymisierung: Ein Auto ohne Besitzer (userId NULL) darf nie als "gehört jemandem"
 * gelten und darf keine NPE auslösen - jeder Ownership-Check muss über isOwnedBy laufen.
 */
class CarOwnershipTest {

    private final UUID owner = UUID.randomUUID();

    @Test
    void isOwnedBy_matchesOwnerOnly() {
        Car car = Car.builder().id(UUID.randomUUID()).userId(owner).build();
        assertTrue(car.isOwnedBy(owner));
        assertFalse(car.isOwnedBy(UUID.randomUUID()));
        assertFalse(car.isOwnedBy(null));
    }

    @Test
    void anonymizedCar_isOwnedByNobodyAndKeepsSpec() {
        UUID spec = UUID.randomUUID();
        Car car = Car.builder().id(UUID.randomUUID()).userId(owner).licensePlate("B-EV 123")
                .imagePath("/img/x.jpg").imagePublic(true).vehicleSpecificationId(spec).year(2022).build();

        Car anon = car.anonymize();

        assertNull(anon.getUserId());
        assertNull(anon.getLicensePlate());
        assertNull(anon.getImagePath());
        assertFalse(anon.isImagePublic());
        assertNotNull(anon.getAnonymizedAt());
        assertTrue(anon.isAnonymized());
        assertFalse(car.isAnonymized());
        assertFalse(anon.isOwnedBy(owner));
        assertFalse(anon.isOwnedBy(null));
        assertEquals(spec, anon.getVehicleSpecificationId());
        assertEquals(2022, anon.getYear());
    }
}
