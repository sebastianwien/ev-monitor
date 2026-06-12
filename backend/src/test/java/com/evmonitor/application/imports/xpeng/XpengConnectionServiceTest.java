package com.evmonitor.application.imports.xpeng;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.CarStatus;
import com.evmonitor.infrastructure.persistence.xpeng.XpengConnection;
import com.evmonitor.infrastructure.persistence.xpeng.XpengConnectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class XpengConnectionServiceTest {

    @Mock CarRepository carRepository;
    @Mock XpengConnectionRepository connectionRepo;
    @InjectMocks XpengConnectionService service;

    private static final UUID USER = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();
    private static final UUID CAR = UUID.randomUUID();
    private static final String VALID_VIN = "L1NN12345678ABCDE";

    @Test
    void rejectsConnectionForCarOfOtherUser() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(OTHER_USER)));
        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.grantConsent(USER, CAR, VALID_VIN, "127.0.0.1", "ua", false, null));
        assertTrue(ex.getMessage().contains("gehört"));
        verify(connectionRepo, never()).save(any());
    }

    @Test
    void rejectsInvalidVinLength() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        assertThrows(IllegalArgumentException.class,
                () -> service.grantConsent(USER, CAR, "TOO_SHORT", "127.0.0.1", "ua", false, null));
    }

    @Test
    void createsNewConsentWhenNoneExists() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        when(connectionRepo.findByCarId(CAR)).thenReturn(Optional.empty());
        when(connectionRepo.save(any(XpengConnection.class))).thenAnswer(inv -> {
            XpengConnection c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        XpengConnection result = service.grantConsent(USER, CAR, VALID_VIN, "1.2.3.4", "Mozilla", false, null);

        assertEquals(VALID_VIN, result.getVin());
        assertEquals("1.2.3.4", result.getConsentIp());
        assertEquals(USER, result.getUserId());
        assertNotNull(result.getConsentGrantedAt());
        assertNull(result.getConsentRevokedAt());
        assertEquals(XpengConnection.CURRENT_CONSENT_VERSION, result.getConsentVersion());
        assertFalse(result.isAutoSyncEnabled());
        assertNull(result.getXpengEmail());
    }

    @Test
    void reGrantingRevokedConsentResetsRevocation() {
        XpengConnection revoked = XpengConnection.builder()
                .id(UUID.randomUUID()).userId(USER).carId(CAR).vin(VALID_VIN)
                .consentGrantedAt(LocalDateTime.now().minusMonths(1))
                .consentRevokedAt(LocalDateTime.now().minusDays(1))
                .consentVersion(XpengConnection.CURRENT_CONSENT_VERSION)
                .build();
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        when(connectionRepo.findByCarId(CAR)).thenReturn(Optional.of(revoked));
        when(connectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        XpengConnection result = service.grantConsent(USER, CAR, VALID_VIN, "5.6.7.8", "ua2", false, null);

        assertNull(result.getConsentRevokedAt(), "Revocation must be cleared");
        assertEquals("5.6.7.8", result.getConsentIp());
    }

    @Test
    void revokeRefusesIfNotOwner() {
        UUID connectionId = UUID.randomUUID();
        XpengConnection conn = XpengConnection.builder()
                .id(connectionId).userId(OTHER_USER).carId(CAR).vin(VALID_VIN)
                .consentGrantedAt(LocalDateTime.now()).consentVersion("v1.0").build();
        when(connectionRepo.findById(connectionId)).thenReturn(Optional.of(conn));

        assertThrows(SecurityException.class, () -> service.revokeConsent(USER, connectionId));
        verify(connectionRepo, never()).save(any());
    }

    @Test
    void revokeSetsTimestamp() {
        UUID connectionId = UUID.randomUUID();
        XpengConnection conn = XpengConnection.builder()
                .id(connectionId).userId(USER).carId(CAR).vin(VALID_VIN)
                .consentGrantedAt(LocalDateTime.now()).consentVersion("v1.0").build();
        when(connectionRepo.findById(connectionId)).thenReturn(Optional.of(conn));
        when(connectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.revokeConsent(USER, connectionId);

        assertNotNull(conn.getConsentRevokedAt());
        assertFalse(conn.isActive());
    }

    @Test
    void autoSyncGrantSetsConsentV2AndEmail() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        when(connectionRepo.findByCarId(CAR)).thenReturn(Optional.empty());
        when(connectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        XpengConnection result = service.grantConsent(USER, CAR, VALID_VIN, "1.1.1.1", "ua",
                true, "user@xpeng.com");

        assertTrue(result.isAutoSyncEnabled());
        assertEquals(XpengConnection.AUTOSYNC_CONSENT_VERSION, result.getConsentVersion());
        assertEquals("user@xpeng.com", result.getXpengEmail());
    }

    @Test
    void autoSyncGrantSucceedsForFreeUser() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));
        when(connectionRepo.findByCarId(CAR)).thenReturn(Optional.empty());
        when(connectionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        XpengConnection conn = service.grantConsent(USER, CAR, VALID_VIN, "1.1.1.1", "ua", true, "user@xpeng.com");

        assertTrue(conn.isAutoSyncEnabled());
        verify(connectionRepo).save(any());
    }

    @Test
    void rejectsInvalidXpengEmail() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(ownedBy(USER)));

        assertThrows(IllegalArgumentException.class,
                () -> service.grantConsent(USER, CAR, VALID_VIN, "1.1.1.1", "ua", true, "not-an-email"));
    }

    @Test
    void updateXpengEmailSavesEmailForOwner() {
        UUID connId = UUID.randomUUID();
        XpengConnection conn = activeConnection(connId, USER);
        when(connectionRepo.findById(connId)).thenReturn(Optional.of(conn));
        when(connectionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        XpengConnection result = service.updateXpengEmail(USER, connId, "new@xpeng.com");

        assertEquals("new@xpeng.com", result.getXpengEmail());
    }

    @Test
    void updateXpengEmailRejectsNonOwner() {
        UUID connId = UUID.randomUUID();
        when(connectionRepo.findById(connId)).thenReturn(Optional.of(activeConnection(connId, OTHER_USER)));

        assertThrows(SecurityException.class, () -> service.updateXpengEmail(USER, connId, "x@xpeng.com"));
    }

    @Test
    void updateXpengEmailRejectsInvalidFormat() {
        UUID connId = UUID.randomUUID();
        when(connectionRepo.findById(connId)).thenReturn(Optional.of(activeConnection(connId, USER)));

        assertThrows(IllegalArgumentException.class, () -> service.updateXpengEmail(USER, connId, "not-an-email"));
    }

    @Test
    void getConnectionsDueForAutoRequestDelegatesToRepository() {
        XpengConnection due = XpengConnection.builder()
                .id(UUID.randomUUID()).userId(USER).carId(CAR).vin(VALID_VIN)
                .autoSyncEnabled(true).consentVersion(XpengConnection.AUTOSYNC_CONSENT_VERSION)
                .consentGrantedAt(LocalDateTime.now()).build();
        when(connectionRepo.findDueForAutoRequest(any())).thenReturn(List.of(due));

        List<XpengConnection> result = service.getConnectionsDueForAutoRequest();

        assertEquals(1, result.size());
        verify(connectionRepo).findDueForAutoRequest(any());
    }

    private XpengConnection activeConnection(UUID connId, UUID owner) {
        return XpengConnection.builder()
                .id(connId).userId(owner).carId(CAR).vin(VALID_VIN)
                .autoSyncEnabled(true).consentVersion(XpengConnection.AUTOSYNC_CONSENT_VERSION)
                .consentGrantedAt(LocalDateTime.now()).build();
    }

    private Car ownedBy(UUID owner) {
        return Car.builder()
                .id(CAR).userId(owner)
                .model(CarBrand.CarModel.XPENG_G9).year(2025)
                .status(CarStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

}
