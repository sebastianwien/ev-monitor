package com.evmonitor.application.user;

import com.evmonitor.application.CarImageService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * DSGVO-Kontolöschung (Plan A): Autos samt Ladevorgängen und Trips bleiben ohne Personenbezug erhalten,
 * damit die Community-Statistik der Public-Model-Seiten nicht schrumpft. Was fällt: Besitzer, Kennzeichen,
 * Bild, Geohashes, Routen, Rohdaten, Freitexte, Tarif-Verknüpfung, exakte Zeitpunkte (auf Tag gerundet).
 * Was bleibt: Spec, Baujahr, Kapazität, SoH, kWh, km-Stand, Preis, AC/DC, Temperatur, SoC, Distanz, routeType.
 * Siehe docs/features/account-anonymization.md.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountAnonymizationService {

    private final CarRepository carRepository;
    private final EvLogRepository evLogRepository;
    private final EvTripRepository evTripRepository;
    private final CarImageService carImageService;

    /** @return die IDs der anonymisierten Autos (für den Connector-Purge) */
    @Transactional
    public List<UUID> anonymizeCarsOf(UUID userId) {
        List<Car> cars = carRepository.findAllByUserId(userId);
        for (Car car : cars) {
            if (car.getImagePath() != null) carImageService.deleteImage(car.getId());
            carRepository.save(car.anonymize());
        }
        List<UUID> carIds = cars.stream().map(Car::getId).toList();
        if (carIds.isEmpty()) return carIds;

        int logs = anonymizeLogs(carIds);
        int trips = anonymizeTrips(carIds);
        log.info("[ANONYMIZE] userId={} cars={} logs={} trips={}", userId, carIds.size(), logs, trips);
        return carIds;
    }

    private int anonymizeLogs(List<UUID> carIds) {
        List<EvLog> logs = evLogRepository.findAllByCarIds(carIds).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .toList();
        DaySequence seq = new DaySequence();
        for (EvLog l : logs) {
            evLogRepository.save(l.anonymize(seq.next(l.getCarId(), l.getLoggedAt().toLocalDate())));
        }
        return logs.size();
    }

    private int anonymizeTrips(List<UUID> carIds) {
        List<EvTrip> trips = evTripRepository.findAllByCarIdIn(carIds);
        List<EvTrip> softDeleted = trips.stream().filter(t -> t.getDeletedAt() != null).toList();
        evTripRepository.deleteAll(softDeleted);
        List<EvTrip> kept = trips.stream()
                .filter(t -> t.getDeletedAt() == null)
                .sorted(Comparator.comparing(EvTrip::getTripStartedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        DaySequence seq = new DaySequence();
        for (EvTrip t : kept) {
            LocalDate day = t.getTripStartedAt() == null ? null : t.getTripStartedAt().toLocalDate();
            t.anonymize(seq.next(t.getCarId(), day));
        }
        evTripRepository.saveAll(kept);
        return kept.size();
    }

    /** Laufende Nummer pro Auto und Tag - Minutenversatz, der die Tagesreihenfolge nach der Rundung erhält. */
    private static final class DaySequence {
        private final Map<String, Integer> next = new HashMap<>();
        int next(UUID carId, LocalDate day) {
            return next.merge(carId + "|" + day, 1, Integer::sum) - 1;
        }
    }
}
