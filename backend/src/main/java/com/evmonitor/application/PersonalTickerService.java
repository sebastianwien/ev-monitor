package com.evmonitor.application;

import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.persistence.LeaderboardQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Persönliche Ticker-Einträge aus den eigenen Daten des Nutzers: Monatssumme, Verbrauch gegen
 * Fahrer desselben Modells und bester Rang. Arbeitet nur mit der userId aus dem JWT.
 */
@Service
@RequiredArgsConstructor
public class PersonalTickerService {

    /** Unter drei Vergleichsfahrern ließe sich der Wert eines einzelnen Fremden ablesen. */
    static final int MIN_PEER_USERS = 3;

    private final LeaderboardQueryRepository queryRepository;
    private final EvLogStatisticsService statisticsService;
    private final LeaderboardService leaderboardService;
    private final UserRepository userRepository;

    /**
     * Laufender Monat bis heute oder Vormonat komplett (Monatsrückblick), je Nutzer und Monat gecacht.
     * Das heutige Datum im Key verhindert, dass am Monatsersten ein Eintrag vom Vortag als kompletter Vormonat gilt.
     */
    @Cacheable(value = "tickerPersonal", key = "#userId + ':' + #yearMonth + ':' + T(java.time.LocalDate).now()")
    public List<TickerItemDTO> getItems(UUID userId, YearMonth yearMonth) {
        TickerMonth window = TickerMonth.of(yearMonth, LocalDate.now());
        String month = window.monthParam();

        List<TickerItemDTO> items = new ArrayList<>();
        queryRepository.getTopCarMonthSummary(userId, window.start(), window.end())
                .ifPresent(summary -> {
                    items.add(monthItem(summary, month));
                    consumptionItem(summary, userId, window).ifPresent(items::add);
                });
        rankItem(userId, yearMonth, month).ifPresent(item -> items.add(Math.min(1, items.size()), item));
        return items;
    }

    private static TickerItemDTO monthItem(MonthChargeSummary summary, String month) {
        long homePercent = Math.round(summary.homeCharges() * 100.0 / summary.charges());
        return TickerItemDTO.personal("my_month", Map.of(
                "month", month,
                "kwh", summary.kwh().setScale(0, RoundingMode.HALF_UP).toPlainString(),
                "charges", Long.toString(summary.charges()),
                "homePercent", Long.toString(homePercent)));
    }

    private Optional<TickerItemDTO> consumptionItem(MonthChargeSummary summary, UUID userId, TickerMonth window) {
        String month = window.monthParam();
        return statisticsService.getPeerBenchmark(summary.carId(), userId, window.firstDay(), window.lastDay())
                .filter(pb -> pb.userPeriodConsumptionKwhPer100km() != null
                        && pb.peerAvgConsumptionKwhPer100km() != null
                        && !pb.peerConsumptionLifetime()
                        && pb.uniquePeerUsers() >= MIN_PEER_USERS)
                // carId: der Vergleich gilt für das Top-Auto des Monats; die Story zeigt ihn nur für genau dieses Auto.
                .map(pb -> TickerItemDTO.personal("my_consumption", Map.of(
                        "month", month,
                        "carId", summary.carId().toString(),
                        "mine", oneDecimal(pb.userPeriodConsumptionKwhPer100km()),
                        "peers", oneDecimal(pb.peerAvgConsumptionKwhPer100km()))));
    }

    private Optional<TickerItemDTO> rankItem(UUID userId, YearMonth yearMonth, String month) {
        if (!userRepository.isLeaderboardVisible(userId)) return Optional.empty();
        return leaderboardService.getMyBestStanding(userId, yearMonth).map(standing -> TickerItemDTO.personal("my_standing", Map.of(
                "category", standing.category().name(),
                "rank", Integer.toString(standing.rank()),
                "value", standing.value().toPlainString(),
                "month", month)));
    }

    private static String oneDecimal(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }
}
