package com.evmonitor.application;

import com.evmonitor.domain.CoinType;
import com.evmonitor.domain.LeaderboardCategory;
import com.evmonitor.infrastructure.external.ExternalJokeService;
import com.evmonitor.infrastructure.external.FuelPriceService;
import com.evmonitor.infrastructure.persistence.LeaderboardQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private static final int TOP_N = 10;

    private final LeaderboardQueryRepository queryRepository;
    private final CoinLogService coinLogService;
    private final ExternalJokeService externalJokeService;
    private final FuelPriceService fuelPriceService;

    @Cacheable(value = "leaderboard", key = "'board:' + #category.name() + ':' + #requestingUserId")
    public LeaderboardResponseDTO getLeaderboard(LeaderboardCategory category, UUID requestingUserId) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfToday = today.plusDays(1).atStartOfDay();
        LocalDateTime endOfYesterday = today.atStartOfDay();

        List<LeaderboardRankRow> todayRanking = getRanking(category, startOfMonth, endOfToday);
        List<LeaderboardRankRow> yesterdayRanking = getRanking(category, startOfMonth, endOfYesterday);

        // Rank delta is tracked by entityId (carId for car-based, userId for coins)
        Map<UUID, Integer> yesterdayRanks = buildRankMap(yesterdayRanking);
        boolean hasDeltaData = !yesterdayRanking.isEmpty();

        List<LeaderboardEntryDTO> top10 = new ArrayList<>();
        Set<UUID> top10EntityIds = new HashSet<>();
        Set<UUID> top10UserIds = new HashSet<>();

        for (int i = 0; i < Math.min(TOP_N, todayRanking.size()); i++) {
            LeaderboardRankRow row = todayRanking.get(i);
            int currentRank = i + 1;
            Integer prevRank = yesterdayRanks.get(row.entityId());
            Integer delta = (hasDeltaData && prevRank != null) ? prevRank - currentRank : null;
            boolean isNew = hasDeltaData && prevRank == null;

            top10.add(buildEntry(category, row, currentRank, prevRank, delta, isNew));
            top10EntityIds.add(row.entityId());
            top10UserIds.add(row.userId());
        }

        // Show ownEntry only if NONE of the user's entries are in top 10
        LeaderboardEntryDTO ownEntry = null;
        if (requestingUserId != null && !top10UserIds.contains(requestingUserId)) {
            for (int i = TOP_N; i < todayRanking.size(); i++) {
                LeaderboardRankRow row = todayRanking.get(i);
                if (row.userId().equals(requestingUserId)) {
                    int currentRank = i + 1;
                    Integer prevRank = yesterdayRanks.get(row.entityId());
                    Integer delta = (hasDeltaData && prevRank != null) ? prevRank - currentRank : null;
                    boolean isNew = hasDeltaData && prevRank == null;
                    ownEntry = buildEntry(category, row, currentRank, prevRank, delta, isNew);
                    break; // first match = best-ranked car for this user
                }
            }
        }

        String period = today.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return new LeaderboardResponseDTO(
                category,
                category.getDisplayName(),
                category.getUnit(),
                category.isLowerIsBetter(),
                period,
                top10,
                ownEntry
        );
    }

    public List<MyLeaderboardStandingDTO> getMyStandings(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfToday = today.plusDays(1).atStartOfDay();
        LocalDateTime endOfYesterday = today.atStartOfDay();

        List<MyLeaderboardStandingDTO> result = new ArrayList<>();

        for (LeaderboardCategory cat : LeaderboardCategory.values()) {
            List<LeaderboardRankRow> todayRanking = getRanking(cat, startOfMonth, endOfToday);
            List<LeaderboardRankRow> yesterdayRanking = getRanking(cat, startOfMonth, endOfYesterday);
            Map<UUID, Integer> yesterdayRanks = buildRankMap(yesterdayRanking);
            boolean hasDeltaData = !yesterdayRanking.isEmpty();

            Integer rank = null;
            BigDecimal value = null;
            Integer rankDelta = null;
            boolean isNew = false;
            String carLabel = null;

            for (int i = 0; i < todayRanking.size(); i++) {
                LeaderboardRankRow row = todayRanking.get(i);
                if (row.userId().equals(userId)) {
                    rank = i + 1;
                    value = formatValue(cat, row.value());
                    carLabel = row.carLabel();
                    Integer prevRank = yesterdayRanks.get(row.entityId());
                    rankDelta = (hasDeltaData && prevRank != null) ? prevRank - rank : null;
                    isNew = hasDeltaData && prevRank == null;
                    break; // first match = best-ranked car for this user
                }
            }

            result.add(new MyLeaderboardStandingDTO(
                    cat,
                    cat.getDisplayName(),
                    cat.getUnit(),
                    cat.isLowerIsBetter(),
                    rank,
                    value,
                    rankDelta,
                    isNew,
                    carLabel
            ));
        }

        return result;
    }

    @Cacheable(value = "leaderboard", key = "'ticker'")
    public List<TickerItemDTO> getTicker() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfToday = today.plusDays(1).atStartOfDay();
        String month = today.format(DateTimeFormatter.ofPattern("MMMM", Locale.GERMAN));

        List<TickerItemDTO> items = new ArrayList<>();

        // Category leaders
        for (LeaderboardCategory cat : LeaderboardCategory.values()) {
            List<LeaderboardRankRow> ranking = getRanking(cat, startOfMonth, endOfToday);
            if (!ranking.isEmpty()) {
                LeaderboardRankRow leader = ranking.get(0);
                String valueStr = formatValue(cat, leader.value()).toPlainString();
                String displayName = leader.carLabel() != null
                        ? leader.username() + " (" + leader.carLabel() + ")"
                        : leader.username();
                items.add(new TickerItemDTO(
                        "LEADER",
                        "#1 " + cat.getDisplayName() + ": " + displayName + " mit " + valueStr + " " + cat.getUnit(),
                        "trophy"
                ));
            }
        }

        // Community stats
        BigDecimal totalKwh = queryRepository.getTotalKwhThisMonth(startOfMonth, endOfToday);
        ChargeCountStats chargeCounts = queryRepository.getChargeCountStats(startOfMonth, endOfToday);
        long totalCharges = chargeCounts.total();
        long homeCharges = chargeCounts.home();
        long totalMinutes = queryRepository.getTotalChargeDurationMinutes(startOfMonth, endOfToday);
        BigDecimal totalCostEur = queryRepository.getTotalCostEur(startOfMonth, endOfToday);
        TopCpoResult topCpo = queryRepository.getTopPublicCpo(startOfMonth, endOfToday);
        double kwhDouble = totalKwh.doubleValue();

        // Basis-Stat
        items.add(new TickerItemDTO(
                "STAT",
                "Community " + month + ": " + totalKwh.setScale(0, RoundingMode.HALF_UP).toPlainString()
                        + " kWh in " + totalCharges + " Ladevorgaengen",
                "bolt"
        ));

        if (kwhDouble > 0) {
            // Annahmen: 20 kWh/100km (EV-Durchschnitt), 380g CO2/kWh (dt. Strommix), 160g CO2/km (Verbrenner-Durchschnitt)
            double distanceKm = kwhDouble / 20.0 * 100.0;
            double iceCo2Kg = distanceKm * 0.160;
            double evCo2Kg = kwhDouble * 0.380;
            double savedCo2Kg = iceCo2Kg - evCo2Kg;

            if (savedCo2Kg > 0) {
                String co2Text = savedCo2Kg >= 1000
                        ? String.format("%.1f Tonnen", savedCo2Kg / 1000.0)
                        : String.format("%.0f kg", savedCo2Kg);
                items.add(new TickerItemDTO("STAT",
                        "Community " + month + ": " + co2Text + " CO2 gegenueber Verbrennern gespart",
                        "bolt"));
            }

            // Haushalte (dt. Durchschnitt ~290 kWh/Monat)
            long haushalte = Math.round(kwhDouble / 290.0);
            if (haushalte > 0) {
                items.add(new TickerItemDTO("STAT",
                        "Mit unserem Strom im " + month + " haetten " + haushalte + " Haushalte einen Monat lang geleuchtet",
                        "bolt"));
            }

            // Solarmodule (400W Panel, ~33 kWh/Monat in DE)
            long panels = Math.round(kwhDouble / 33.0);
            if (panels > 0) {
                items.add(new TickerItemDTO("STAT",
                        "Mit " + panels + " Solarmodulen auf dem Dach haette die Community ihren Strom im " + month + " selbst erzeugt",
                        "bolt"));
            }

            // Windrad-Stunden (3 MW Onshore-Windrad)
            double windStunden = kwhDouble / 3000.0;
            if (windStunden >= 0.5) {
                String windText = windStunden < 1
                        ? String.format("%.0f Minuten", windStunden * 60)
                        : String.format("%.1f Stunden", windStunden);
                items.add(new TickerItemDTO("STAT",
                        "Ein Windrad muesste " + windText + " laufen um unsere Community im " + month + " zu versorgen",
                        "bolt"));
            }

            // Ersparnis vs. Benzin/Diesel (7L/100km Verbrenner-Durchschnitt)
            double avgFuelPrice = fuelPriceService.getAvgFuelPrice();
            double fuelCost = distanceKm / 100.0 * 7.0 * avgFuelPrice;
            double evCost = totalCostEur.doubleValue();
            if (evCost > 0) {
                double savings = fuelCost - evCost;
                if (savings > 0) {
                    items.add(new TickerItemDTO("STAT",
                            String.format("Community " + month + ": %.0f € gespart gegenueber Benzin/Diesel (%.2f €/L)",
                                    savings, avgFuelPrice),
                            "bolt"));
                }
            }
        }

        // Ladezeit
        if (totalMinutes > 0) {
            double ladeTage = totalMinutes / 60.0 / 24.0;
            String ladeText = ladeTage >= 1
                    ? String.format("%.1f Tage", ladeTage)
                    : String.format("%.0f Stunden", totalMinutes / 60.0);
            items.add(new TickerItemDTO("STAT",
                    "Unsere Community hat im " + month + " insgesamt " + ladeText + " am Stueck geladen",
                    "bolt"));
        }

        // Heimladen-Quote
        if (totalCharges > 0) {
            long homePercent = Math.round(homeCharges * 100.0 / totalCharges);
            items.add(new TickerItemDTO("STAT",
                    homePercent + "% aller Ladevorgänge im " + month + " fanden Zuhause statt",
                    "bolt"));
        }

        // Beliebtester öffentlicher Ladeanbieter
        if (topCpo != null) {
            items.add(new TickerItemDTO("STAT",
                    "Beliebtester öffentlicher Ladeanbieter im " + month + ": " + topCpo.cpoName()
                            + " mit " + topCpo.count() + " Ladevorgängen",
                    "bolt"));
        }

        Collections.shuffle(items, new Random());
        return items;
    }

    /**
     * Awards bonus coins to top 3 of each category with hasMonthEndReward=true.
     * Idempotent: action_description includes category + month period.
     */
    public void awardMonthEndRewards(LocalDate month) {
        LocalDateTime start = month.withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).withDayOfMonth(1).atStartOfDay();
        String period = month.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        for (LeaderboardCategory cat : LeaderboardCategory.values()) {
            if (!cat.isHasMonthEndReward()) continue;

            List<LeaderboardRankRow> ranking = getRanking(cat, start, end);
            int[] amounts = {100, 50, 25};

            for (int place = 1; place <= Math.min(3, ranking.size()); place++) {
                LeaderboardRankRow row = ranking.get(place - 1);
                String description = "Leaderboard Platz " + place + " - " + cat.getDisplayName() + " - " + period;
                if (!coinLogService.hasEverReceivedCoinForAction(row.userId(), description)) {
                    coinLogService.awardCoins(row.userId(), CoinType.SOCIAL_COIN, amounts[place - 1], description);
                }
            }
        }
    }

    // ---- Private helpers ----

    private List<LeaderboardRankRow> getRanking(LeaderboardCategory category, LocalDateTime start, LocalDateTime end) {
        return switch (category) {
            case MONTHLY_KWH -> queryRepository.getKwhRanking(start, end);
            case MONTHLY_CHARGES -> queryRepository.getChargesRanking(start, end);
            case MONTHLY_DISTANCE -> queryRepository.getDistanceRanking(start, end);
            case MONTHLY_CHEAPEST -> queryRepository.getCheapestRanking(start, end);
            case MONTHLY_NIGHT_OWL -> queryRepository.getNightOwlRanking(start, end);
            case MONTHLY_ICE_CHARGER -> queryRepository.getIceChargerRanking(start, end);
            case MONTHLY_POWER_CHARGER -> queryRepository.getPowerChargerRanking(start, end);
        };
    }

    private Map<UUID, Integer> buildRankMap(List<LeaderboardRankRow> ranking) {
        Map<UUID, Integer> map = new HashMap<>();
        for (int i = 0; i < ranking.size(); i++) {
            map.put(ranking.get(i).entityId(), i + 1);
        }
        return map;
    }

    private LeaderboardEntryDTO buildEntry(LeaderboardCategory category, LeaderboardRankRow row,
                                           int rank, Integer prevRank, Integer delta, boolean isNew) {
        boolean isCheapest = category == LeaderboardCategory.MONTHLY_CHEAPEST;
        return new LeaderboardEntryDTO(
                rank,
                row.username(),
                row.carLabel(),
                formatValue(category, row.value()),
                category.getUnit(),
                prevRank,
                delta,
                isNew,
                isCheapest ? row.kwhTotal() : null,
                isCheapest ? row.sessionCount() : null
        );
    }

    private BigDecimal formatValue(LeaderboardCategory category, BigDecimal raw) {
        return switch (category) {
            case MONTHLY_KWH -> raw.setScale(1, RoundingMode.HALF_UP);
            case MONTHLY_CHEAPEST -> raw.setScale(2, RoundingMode.HALF_UP);
            case MONTHLY_POWER_CHARGER -> raw.setScale(1, RoundingMode.HALF_UP);
            case MONTHLY_ICE_CHARGER -> raw.setScale(1, RoundingMode.HALF_UP);
            default -> raw.setScale(0, RoundingMode.HALF_UP);
        };
    }
}
