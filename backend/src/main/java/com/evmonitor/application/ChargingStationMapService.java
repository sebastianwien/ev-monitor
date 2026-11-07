package com.evmonitor.application;

import com.evmonitor.application.tariff.TariffCatalog;
import com.evmonitor.application.tariff.savings.GermanyBorder;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Säulenkarte: Standorte je Zelle aus dem Register-Cache, dazu je Standort der günstigste
 * Katalogpreis je Ladeart. Preise werden je Anfrage gerechnet, der Katalog ändert sich mit dem
 * Crawler; gecacht sind nur die Registerdaten.
 *
 * <p>Preise nur an bekannten Betreibern: ein Kartenpreis "an allen anderen Säulen" ist an einem
 * unbekannten Betreiber nicht belegt (siehe {@link CpoPriceBenchmarkService}).
 */
@Service
@RequiredArgsConstructor
public class ChargingStationMapService {

    /** Mehr Zellen braucht kein Kartenausschnitt in Straßenzoom; darüber zeigt die Karte "näher heranzoomen". */
    public static final int MAX_CELLS = 12;

    private final ChargingStationCellService cells;
    private final TariffCatalog catalog;

    /**
     * @param cellIds        gültige Geohash-6-Zellen, ohne Doppelte
     * @param mayAskRegister je Zelle, die das Register fragen müsste: darf sie das (Drossel)?
     *                       Gecachte Zellen fragen nicht.
     */
    public ChargingStationMap stations(List<String> cellIds, Predicate<String> mayAskRegister) {
        TariffCatalog.Snapshot snapshot = catalog.forGermanyOn(LocalDate.now());
        List<ChargingStationMap.Cell> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String cell : cellIds) {
            Optional<List<MapStation>> stations = cells.isCached(cell) || mayAskRegister.test(cell)
                    ? cells.stationsInCell(cell)
                    : Optional.empty();
            stations.ifPresentOrElse(
                    list -> found.add(new ChargingStationMap.Cell(cell, GermanyBorder.containsGeohash(cell),
                            list.stream().map(s -> priced(s, snapshot)).toList())),
                    () -> missing.add(cell));
        }
        return new ChargingStationMap(found, missing);
    }

    private static ChargingStationMap.Station priced(MapStation s, TariffCatalog.Snapshot snapshot) {
        return new ChargingStationMap.Station(s.id(), s.name(), s.known(), s.latitude(), s.longitude(),
                s.maxAcKw(), s.maxDcKw(), s.chargePoints(), s.plugTypes(),
                s.address(), s.openingHours(), s.payment(),
                cheapest(s, ChargingType.AC, s.maxAcKw(), snapshot),
                cheapest(s, ChargingType.DC, s.maxDcKw(), snapshot));
    }

    /** Günstigster Preis je kWh ohne Grundgebühr; null ohne diese Ladeart an der Säule oder ohne Tarif. */
    private static ChargingStationMap.Price cheapest(MapStation s, ChargingType type, Double powerKw,
                                                     TariffCatalog.Snapshot snapshot) {
        if (!s.known() || powerKw == null) return null;
        List<ChargingProviderTariff> at = snapshot.at(s.name(), type, (int) Math.round(powerKw));
        if (at.isEmpty()) return null;
        ChargingProviderTariff best = at.getFirst();
        return new ChargingStationMap.Price(best.getPricePerKwh().setScale(3, RoundingMode.HALF_UP),
                best.getEmpName(), best.getTariffVariant());
    }
}
