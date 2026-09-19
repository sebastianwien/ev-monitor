package com.evmonitor.application;

import com.evmonitor.domain.Car;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Loescht soft-geloeschte Fahrzeuge endgueltig, sobald das Restore-Fenster abgelaufen ist.
 * Laeuft einmal taeglich; erst hier greifen Bildloeschung und FK-Kaskade auf die Ladelogs.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CarPurgeScheduler {

    private final CarService carService;

    @Scheduled(cron = "0 15 3 * * *")
    public void purgeExpiredDeletedCars() {
        int removed = carService.purgeExpiredDeletedCars();
        if (removed > 0) {
            log.info("Fahrzeug-Purge: {} Fahrzeuge nach {} Tagen endgueltig geloescht",
                    removed, Car.RESTORE_WINDOW_DAYS);
        }
    }
}
