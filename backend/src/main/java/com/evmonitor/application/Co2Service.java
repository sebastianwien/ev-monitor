package com.evmonitor.application;

import com.evmonitor.application.consumption.ConsumptionCalculationService;
import com.evmonitor.domain.Co2Balance;
import com.evmonitor.domain.Co2Calculator;
import com.evmonitor.domain.Co2Segment;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.VehicleCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bindet den {@link Co2Calculator} an: Netz-kWh je Ladung aus
 * {@link ConsumptionCalculationService#gridSideKwhEstimate} (Fahrzeug-kWh mit dem Ladewirkungsgrad
 * hochgerechnet) und die Verbrauchsgrenzen auf Netzseite. Persönliche Statistik und Community-Ticker
 * rechnen beide hierüber.
 */
@Service
@RequiredArgsConstructor
public class Co2Service {

    private final ConsumptionCalculationService calculationService;
    private final PlausibilityProperties plausibility;
    private final EvLogRepository evLogRepository;

    /** Tacho-Abschnitte eines Autos aus allen seinen Ladungen. */
    public List<Co2Segment> segments(VehicleCategory category, List<EvLog> logs) {
        return calculator().segments(logs, category, calculationService::gridSideKwhEstimate);
    }

    /**
     * Community-Bilanz: je Auto dieselbe Rechnung über seine ganze Historie, gezählt werden die
     * Abschnitte, deren schließende Ladung in [start, endExclusive) liegt. Null ohne Abschnitt.
     */
    public Co2Balance community(Map<UUID, VehicleCategory> categoryByCar, LocalDateTime start, LocalDateTime endExclusive) {
        if (categoryByCar.isEmpty()) return null;
        Map<UUID, List<EvLog>> logsByCar = evLogRepository.findAllByCarIds(new ArrayList<>(categoryByCar.keySet())).stream()
                .collect(Collectors.groupingBy(EvLog::getCarId));
        List<Co2Segment> inWindow = new ArrayList<>();
        logsByCar.forEach((carId, logs) -> segments(categoryByCar.get(carId), logs).stream()
                .filter(s -> !s.closedAt().isBefore(start) && s.closedAt().isBefore(endExclusive))
                .forEach(inWindow::add));
        return Co2Balance.of(inWindow);
    }

    private Co2Calculator calculator() {
        // Die harten Grenzen gelten für den Akku; auf Netzseite liegt der Verbrauch um den Ladeverlust
        // höher, oben deshalb mit dem schlechtesten Wirkungsgrad (AC unter 4 kW) hochgerechnet.
        return new Co2Calculator(plausibility.getAbsoluteMinKwhPer100km(),
                plausibility.getAbsoluteMaxKwhPer100km() / plausibility.getAcChargingEfficiencySlow());
    }
}
