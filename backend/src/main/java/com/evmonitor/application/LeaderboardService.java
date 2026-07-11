package com.evmonitor.application;

import com.evmonitor.domain.CoinType;
import com.evmonitor.domain.LeaderboardCategory;
import com.evmonitor.infrastructure.external.ExternalJokeService;
import com.evmonitor.infrastructure.external.ExternalNewsService;
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
    private final ExternalNewsService externalNewsService;
    private final FuelPriceService fuelPriceService;

    @Cacheable(value = "leaderboard", key = "'board:' + #category.name() + ':' + #requestingUserId")
    public LeaderboardResponseDTO getLeaderboard(LeaderboardCategory category, UUID requestingUserId) {
        return getLeaderboard(category, requestingUserId, LocalDate.now());
    }

    /**
     * Wie {@link #getLeaderboard(LeaderboardCategory, UUID)}, aber mit frei waehlbarem Referenzdatum.
     * Die gesamte Fenster-/Delta-/Period-Logik ist datums-relativ - ein verschobenes Referenzdatum
     * (z.B. letzter Tag des Vormonats) liefert damit ohne Sonderfall den Endstand jenes Monats.
     */
    public LeaderboardResponseDTO getLeaderboard(LeaderboardCategory category, UUID requestingUserId, LocalDate referenceDate) {
        LocalDate today = referenceDate;
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
                category.isHasMonthEndReward(),
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
        // Month is emitted as its 1-12 number; the frontend renders the localised name.
        String month = String.valueOf(today.getMonthValue());

        List<TickerItemDTO> items = new ArrayList<>();

        // Category leaders - the frontend maps category (enum name) + value to a localised sentence.
        for (LeaderboardCategory cat : LeaderboardCategory.values()) {
            List<LeaderboardRankRow> ranking = getRanking(cat, startOfMonth, endOfToday);
            if (!ranking.isEmpty()) {
                LeaderboardRankRow leader = ranking.get(0);
                String valueStr = formatValue(cat, leader.value()).toPlainString();
                String displayName = leader.carLabel() != null
                        ? leader.username() + " (" + leader.carLabel() + ")"
                        : leader.username();
                items.add(TickerItemDTO.leader(Map.of(
                        "category", cat.name(),
                        "name", displayName,
                        "value", valueStr
                )));
            }
        }

        // Community stats
        BigDecimal totalKwh = queryRepository.getTotalKwhThisMonth(startOfMonth, endOfToday);
        ChargeCountStats chargeCounts = queryRepository.getChargeCountStats(startOfMonth, endOfToday);
        long totalCharges = chargeCounts.total();
        long homeCharges = chargeCounts.home();
        long totalMinutes = queryRepository.getTotalChargeDurationMinutes(startOfMonth, endOfToday);
        BigDecimal totalCostEur = queryRepository.getTotalCostEur(startOfMonth, endOfToday);
        TopProviderResult topProvider = queryRepository.getTopPublicProvider(startOfMonth, endOfToday);
        double kwhDouble = totalKwh.doubleValue();

        // Basis-Stat
        items.add(TickerItemDTO.stat("stat_base", "energy", Map.of(
                "month", month,
                "kwh", totalKwh.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                "charges", Long.toString(totalCharges)
        )));

        if (kwhDouble > 0) {
            // Annahmen: 20 kWh/100km (EV-Durchschnitt), 380g CO2/kWh (dt. Strommix), 160g CO2/km (Verbrenner-Durchschnitt)
            double distanceKm = kwhDouble / 20.0 * 100.0;
            double iceCo2Kg = distanceKm * 0.160;
            double evCo2Kg = kwhDouble * 0.380;
            double savedCo2Kg = iceCo2Kg - evCo2Kg;

            if (savedCo2Kg > 0) {
                boolean tonnes = savedCo2Kg >= 1000;
                items.add(TickerItemDTO.stat("co2_saved", "eco", Map.of(
                        "month", month,
                        "amount", tonnes ? num(savedCo2Kg / 1000.0, 1) : num(savedCo2Kg, 0),
                        "unit", tonnes ? "tonnes" : "kg")));
            }

            // Haushalte (dt. Durchschnitt ~290 kWh/Monat)
            long haushalte = Math.round(kwhDouble / 290.0);
            if (haushalte > 0) {
                items.add(TickerItemDTO.stat("households", "eco", Map.of(
                        "month", month, "count", Long.toString(haushalte))));
            }

            // Solarmodule (400W Panel, ~33 kWh/Monat in DE)
            long panels = Math.round(kwhDouble / 33.0);
            if (panels > 0) {
                items.add(TickerItemDTO.stat("solar", "eco", Map.of(
                        "month", month, "count", Long.toString(panels))));
            }

            // Windrad-Stunden (3 MW Onshore-Windrad)
            double windStunden = kwhDouble / 3000.0;
            if (windStunden >= 0.5) {
                boolean hours = windStunden >= 1;
                items.add(TickerItemDTO.stat("wind", "eco", Map.of(
                        "month", month,
                        "amount", hours ? num(windStunden, 1) : num(windStunden * 60, 0),
                        "unit", hours ? "hours" : "minutes")));
            }

            // Ersparnis vs. Benzin/Diesel (7L/100km Verbrenner-Durchschnitt)
            double avgFuelPrice = fuelPriceService.getAvgFuelPrice();
            double fuelCost = distanceKm / 100.0 * 7.0 * avgFuelPrice;
            double evCost = totalCostEur.doubleValue();
            if (evCost > 0) {
                double savings = fuelCost - evCost;
                if (savings > 0) {
                    items.add(TickerItemDTO.stat("savings_vs_fuel", "money", Map.of(
                            "month", month,
                            "amount", num(savings, 0),
                            "price", num(avgFuelPrice, 2))));
                }
            }
        }

        // Ladezeit
        if (totalMinutes > 0) {
            double ladeTage = totalMinutes / 60.0 / 24.0;
            boolean days = ladeTage >= 1;
            items.add(TickerItemDTO.stat("charge_time", "energy", Map.of(
                    "month", month,
                    "amount", days ? num(ladeTage, 1) : num(totalMinutes / 60.0, 0),
                    "unit", days ? "days" : "hours")));
        }

        // Heimladen-Quote
        if (totalCharges > 0) {
            long homePercent = Math.round(homeCharges * 100.0 / totalCharges);
            items.add(TickerItemDTO.stat("home_quota", "energy", Map.of(
                    "month", month, "percent", Long.toString(homePercent))));
        }

        // Beliebtester öffentlicher Ladeanbieter (Ladekarte des Users, sonst CPO-Name)
        if (topProvider != null) {
            items.add(TickerItemDTO.stat("top_provider", "energy", Map.of(
                    "month", month,
                    "provider", topProvider.providerName(),
                    "count", Long.toString(topProvider.count()))));
        }

        // Good news from external RSS feeds (external titles - passed through verbatim)
        List<ExternalNewsService.NewsItem> newsItems = externalNewsService.getNewsItems();
        if (!newsItems.isEmpty()) {
            // Pick up to 2 news items per ticker refresh (rotate by day-of-year)
            int offset = LocalDate.now().getDayOfYear() % newsItems.size();
            for (int i = 0; i < Math.min(2, newsItems.size()); i++) {
                ExternalNewsService.NewsItem news = newsItems.get((offset + i) % newsItems.size());
                items.add(TickerItemDTO.news(news.title(), news.url()));
            }
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
            case MONTHLY_HEAT_CHARGER -> queryRepository.getHeatChargerRanking(start, end);
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

    /** Format a number as a locale-neutral plain string (dot decimal) for the frontend to parse and re-format. */
    private static String num(double value, int decimals) {
        return String.format(Locale.US, "%." + decimals + "f", value);
    }

    private BigDecimal formatValue(LeaderboardCategory category, BigDecimal raw) {
        return switch (category) {
            case MONTHLY_KWH -> raw.setScale(1, RoundingMode.HALF_UP);
            case MONTHLY_CHEAPEST -> raw.setScale(2, RoundingMode.HALF_UP);
            case MONTHLY_POWER_CHARGER -> raw.setScale(1, RoundingMode.HALF_UP);
            case MONTHLY_ICE_CHARGER -> raw.setScale(1, RoundingMode.HALF_UP);
            case MONTHLY_HEAT_CHARGER -> raw.setScale(1, RoundingMode.HALF_UP);
            default -> raw.setScale(0, RoundingMode.HALF_UP);
        };
    }
}
