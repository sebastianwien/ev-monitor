package com.evmonitor.application;

import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.persistence.LeaderboardQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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

    @Cacheable(value = "tickerPersonal", key = "#userId")
    public List<TickerItemDTO> getItems(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        String month = String.valueOf(today.getMonthValue());

        List<TickerItemDTO> items = new ArrayList<>();
        queryRepository.getTopCarMonthSummary(userId, monthStart.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .ifPresent(summary -> {
                    items.add(monthItem(summary, month));
                    consumptionItem(summary, userId, monthStart, today, month).ifPresent(items::add);
                });
        rankItem(userId).ifPresent(item -> items.add(Math.min(1, items.size()), item));
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

    private Optional<TickerItemDTO> consumptionItem(MonthChargeSummary summary, UUID userId,
                                                    LocalDate monthStart, LocalDate today, String month) {
        return statisticsService.getPeerBenchmark(summary.carId(), userId, monthStart, today)
                .filter(pb -> pb.userPeriodConsumptionKwhPer100km() != null
                        && pb.peerAvgConsumptionKwhPer100km() != null
                        && !pb.peerConsumptionLifetime()
                        && pb.uniquePeerUsers() >= MIN_PEER_USERS)
                .map(pb -> TickerItemDTO.personal("my_consumption", Map.of(
                        "month", month,
                        "mine", oneDecimal(pb.userPeriodConsumptionKwhPer100km()),
                        "peers", oneDecimal(pb.peerAvgConsumptionKwhPer100km()))));
    }

    private Optional<TickerItemDTO> rankItem(UUID userId) {
        if (!userRepository.isLeaderboardVisible(userId)) return Optional.empty();
        return leaderboardService.getMyBestStanding(userId).map(standing -> {
            if (standing.gapToNext() == null) {
                return TickerItemDTO.personal("my_rank_leader", Map.of(
                        "category", standing.category().name(),
                        "value", standing.value().toPlainString()));
            }
            return TickerItemDTO.personal("my_rank", Map.of(
                    "category", standing.category().name(),
                    "rank", Integer.toString(standing.rank()),
                    "gap", standing.gapToNext().toPlainString()));
        });
    }

    private static String oneDecimal(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }
}
