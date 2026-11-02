package com.evmonitor.application.range;

import com.evmonitor.application.DriveConsumptionService;
import com.evmonitor.application.DriveConsumptionService.PeerQuery;
import com.evmonitor.application.DriveConsumptionService.Period;
import com.evmonitor.application.DriveConsumptionService.Season;
import com.evmonitor.application.DriveStatistics;
import com.evmonitor.application.PublicModelService;
import com.evmonitor.application.SeasonalConsumptionResult;
import com.evmonitor.application.community.CommunityConsumptionService;
import com.evmonitor.application.community.CommunityConsumptionService.ModelCommunity;
import com.evmonitor.application.community.WltpFleetFactors;
import com.evmonitor.application.consumption.ConsumptionCalculationService;
import com.evmonitor.domain.*;
import com.evmonitor.domain.RangeConsumptionPolicy.PeerFallback;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Reichweite eines eigenen Autos für die Statistik: je Zeitraum (4 Wochen, Sommer, Winter) die vier
 * Stufen der {@link RangeConsumptionPolicy} und die km nach {@link RangeMath}. Die Modellwerte für
 * Stufe 4 (Ladungen der Fahrer des Modells, Flottenfaktor) kommen gecacht aus dem {@link CommunityConsumptionService};
 * sie sind modellweit wie auf der Modellseite, also mit den eigenen Ladungen, Saisons erst ab 10 Ladungen.
 */
@Service
@RequiredArgsConstructor
public class RangeEstimateService {

    private final DriveConsumptionService driveConsumptionService;
    private final CommunityConsumptionService community;
    private final ConsumptionCalculationService calculationService;

    /** Eigener Verbrauch aus Ladungen (Stufe 3) im Zeitraum sowie Sommer und Winter aller Jahre. */
    public record OwnCharges(BigDecimal current, BigDecimal summer, BigDecimal winter) {
        public static final OwnCharges NONE = new OwnCharges(null, null, null);
    }

    /** Reichweite je Zeitraum; ein Zeitraum ohne jeden Wert ist null. */
    public record RangeStatistics(RangeEstimate current, RangeEstimate summer, RangeEstimate winter) {}

    /** Ownership prüft der Aufrufer ({@code EvLogStatisticsService.getStatistics}). */
    @Transactional(readOnly = true)
    public RangeStatistics forStatistics(Car car, User owner, Period period, DriveStatistics drive, OwnCharges charges) {
        BigDecimal capacity = car.getEffectiveBatteryCapacityKwh();
        boolean seed = owner.isSeedData();
        ModelCommunity model = car.getModel() == null ? ModelCommunity.NONE : community.forModel(car.getModel(), seed);
        SeasonalConsumptionResult seasonal = model.seasonal();
        WltpFleetFactors fleet = community.fleetFactors(seed);
        BigDecimal wltp = calculationService.lookupWltp(car);
        // Saisons der Fahrer des Modells erst ab der Schwelle der Modellseite (10 Ladungen), wie dort
        boolean summerOk = seasonal.summerLogCount() >= PublicModelService.MIN_LOGS_PER_SEASON;
        boolean winterOk = seasonal.winterLogCount() >= PublicModelService.MIN_LOGS_PER_SEASON;
        return new RangeStatistics(
                estimate(capacity, drive.driveConsumptionKwhPer100km(), peer(car, owner, period, Season.ALL), charges.current(),
                        new PeerFallback(model.overall().value(), wltp, fleet.overallOrFallback())),
                estimate(capacity, drive.driveSummerConsumptionKwhPer100km(), peer(car, owner, Period.ALL, Season.SUMMER), charges.summer(),
                        new PeerFallback(summerOk ? seasonal.summerConsumptionKwhPer100km() : null, wltp, fleet.summerOrFallback())),
                estimate(capacity, drive.driveWinterConsumptionKwhPer100km(), peer(car, owner, Period.ALL, Season.WINTER), charges.winter(),
                        new PeerFallback(winterOk ? seasonal.winterConsumptionKwhPer100km() : null, wltp, fleet.winterOrFallback())));
    }

    private static RangeEstimate estimate(BigDecimal capacity, BigDecimal ownDrive, PeerDrive peer, BigDecimal ownCharges, PeerFallback fallback) {
        return RangeEstimate.of(capacity, RangeConsumptionPolicy.decide(ownDrive, peer, ownCharges, fallback));
    }

    private PeerDrive peer(Car car, User owner, Period period, Season season) {
        return driveConsumptionService.peerDrive(new PeerQuery(car.getModel(), car.getVehicleSpecificationId(), period, season, owner.getId()));
    }
}
