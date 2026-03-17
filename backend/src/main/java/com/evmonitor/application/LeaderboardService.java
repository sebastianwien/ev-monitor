package com.evmonitor.application;

import com.evmonitor.domain.CoinType;
import com.evmonitor.domain.LeaderboardCategory;
import com.evmonitor.infrastructure.external.ExternalJokeService;
import com.evmonitor.infrastructure.persistence.LeaderboardQueryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class LeaderboardService {

    private static final int TOP_N = 10;

    /**
     * EV-spezifische Witze und Sarkasmus.
     * Format: bewusst kurz und knackig fuer den Ticker.
     */
    private static final List<String> EV_JOKES = List.of(
            "Benziner-Fahrer erklären dir gerne, dass E-Autos nichts taugen. Meistens von der Tankstelle aus.",
            "'Aber woher kommt der Strom?' - fragt derselbe Mensch, der nie gefragt hat, woher das Öl kommt.",
            "Mein E-Auto hat heute 0 Liter Sprit verbraucht. Der Arzt sagt, das ist normal.",
            "Wallbox zuhause = Tankstelle im Schlafanzug. Der Traum ist real.",
            "Laden dauert 20 Minuten? Dein letzter Netflix-Binge dauerte 6 Stunden. Kein Kommentar.",
            "Reichweitenangst: die Angst vor leeren Akkus bei Leuten, die täglich 30 km fahren und zuhause laden.",
            "Lade ich bei 22% noch oder warte ich bis 18%? Die Frage, die Hamlet nie kannte.",
            "Mein Akku hat heute 94%. Das ist mehr als mein letztes Mitarbeitergespräch.",
            "AC oder DC laden? Immer DC. Die Band geht auch schneller - obwohl beides Hochspannung ist.",
            "Das E-Auto macht keine Geräusche. Dafür macht der Besitzer umso mehr.",
            "Fun Fact: Der erste Elektrowagen wurde 1884 gebaut. Reichweitenangst existiert also seit 142 Jahren.",
            "Spoiler: Auch Verbrenner-Besitzer kommen irgendwann an. Nur nicht günstig.",
            "In Deutschland gibt es mehr Ladepunkte als du je brauchst. Nur halt nie wo du gerade bist.",
            "Das E-Auto beschleunigt schneller als ein Klischee über E-Autos veraltet.",
            "Klimaschutz beim Verbrenner: Reifendruck prüfen, Fenster auf, trotzdem 12L/100km.",
            "Warum ist das E-Auto so ruhig? Weil es nichts zu sagen hat. Anders als sein Besitzer.",
            "Gutes Laden ist wie guter Kaffee: kurz warten, lange Wirkung.",
            "Der Verbrenner macht auch Geräusche. Leider meistens Geldbeutel-Geräusche.",
            "Rekuperation: Sparen beim Ausgeben. Dem Finanzamt würde das gefallen.",
            "Fun Fact: 'Range Anxiety' ist Deutsch für 'Ich hab den Unterschied zwischen 20% und 80% nicht gecheckt.'",
            "E-Autos sind nicht die Zukunft. Sie sind die Gegenwart. Die Zukunft sind fliegende Autos - auch mit Lader.",
            "Wusstest du? Ein Tesla beschleunigt schneller als manche Meinungen im Internet wechseln.",
            "Jeden Morgen voll: Wallbox. Kein Kommentar zu anderen Beziehungen."
    );

    private final LeaderboardQueryRepository queryRepository;
    private final CoinLogService coinLogService;
    private final ExternalJokeService externalJokeService;

    public LeaderboardService(LeaderboardQueryRepository queryRepository,
                              CoinLogService coinLogService,
                              ExternalJokeService externalJokeService) {
        this.queryRepository = queryRepository;
        this.coinLogService = coinLogService;
        this.externalJokeService = externalJokeService;
    }

    public LeaderboardResponseDTO getLeaderboard(LeaderboardCategory category, UUID requestingUserId) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfToday = today.plusDays(1).atStartOfDay();
        LocalDateTime endOfYesterday = today.atStartOfDay();

        List<LeaderboardRankRow> todayRanking = getRanking(category, startOfMonth, endOfToday);
        List<LeaderboardRankRow> yesterdayRanking = getRanking(category, startOfMonth, endOfYesterday);

        Map<UUID, Integer> yesterdayRanks = buildRankMap(yesterdayRanking);
        boolean hasDeltaData = !yesterdayRanking.isEmpty();

        List<LeaderboardEntryDTO> top10 = new ArrayList<>();
        Set<UUID> top10UserIds = new HashSet<>();

        for (int i = 0; i < Math.min(TOP_N, todayRanking.size()); i++) {
            LeaderboardRankRow row = todayRanking.get(i);
            int currentRank = i + 1;
            Integer prevRank = yesterdayRanks.get(row.userId());
            Integer delta = (hasDeltaData && prevRank != null) ? prevRank - currentRank : null;
            boolean isNew = hasDeltaData && prevRank == null;

            top10.add(new LeaderboardEntryDTO(
                    currentRank,
                    row.username(),
                    formatValue(category, row.value()),
                    category.getUnit(),
                    prevRank,
                    delta,
                    isNew
            ));
            top10UserIds.add(row.userId());
        }

        LeaderboardEntryDTO ownEntry = null;
        if (requestingUserId != null && !top10UserIds.contains(requestingUserId)) {
            for (int i = 0; i < todayRanking.size(); i++) {
                LeaderboardRankRow row = todayRanking.get(i);
                if (row.userId().equals(requestingUserId)) {
                    int currentRank = i + 1;
                    Integer prevRank = yesterdayRanks.get(requestingUserId);
                    Integer delta = (hasDeltaData && prevRank != null) ? prevRank - currentRank : null;
                    boolean isNew = hasDeltaData && prevRank == null;
                    ownEntry = new LeaderboardEntryDTO(
                            currentRank,
                            row.username(),
                            formatValue(category, row.value()),
                            category.getUnit(),
                            prevRank,
                            delta,
                            isNew
                    );
                    break;
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

            for (int i = 0; i < todayRanking.size(); i++) {
                LeaderboardRankRow row = todayRanking.get(i);
                if (row.userId().equals(userId)) {
                    rank = i + 1;
                    value = formatValue(cat, row.value());
                    Integer prevRank = yesterdayRanks.get(userId);
                    rankDelta = (hasDeltaData && prevRank != null) ? prevRank - rank : null;
                    isNew = hasDeltaData && prevRank == null;
                    break;
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
                    isNew
            ));
        }

        return result;
    }

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
                items.add(new TickerItemDTO(
                        "LEADER",
                        "#1 " + cat.getDisplayName() + ": " + leader.username() + " mit " + valueStr + " " + cat.getUnit(),
                        "trophy"
                ));
            }
        }

        // Community stat
        BigDecimal totalKwh = queryRepository.getTotalKwhThisMonth(startOfMonth, endOfToday);
        long totalCharges = queryRepository.getTotalChargesThisMonth(startOfMonth, endOfToday);
        items.add(new TickerItemDTO(
                "STAT",
                "Community " + month + ": " + totalKwh.setScale(0, RoundingMode.HALF_UP).toPlainString()
                        + " kWh in " + totalCharges + " Ladevorgaengen",
                "bolt"
        ));

        // EV jokes - pick 2 based on day rotation
        int base = today.getDayOfMonth();
        for (int i = 0; i < 2; i++) {
            items.add(new TickerItemDTO("FACT", EV_JOKES.get((base + i * 7) % EV_JOKES.size()), "light-bulb"));
        }

        // External jokes - max 2
        List<String> external = externalJokeService.getJokes();
        for (int i = 0; i < Math.min(2, external.size()); i++) {
            items.add(new TickerItemDTO("JOKE", external.get((base + i) % external.size()), "face-smile"));
        }

        Collections.shuffle(items, new Random(today.getDayOfYear()));
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
            case MONTHLY_COINS -> queryRepository.getCoinsRanking(start, end);
            case MONTHLY_CHEAPEST -> queryRepository.getCheapestRanking(start, end);
            case MONTHLY_NIGHT_OWL -> queryRepository.getNightOwlRanking(start, end);
            case MONTHLY_ICE_CHARGER -> queryRepository.getIceChargerRanking(start, end);
            case MONTHLY_POWER_CHARGER -> queryRepository.getPowerChargerRanking(start, end);
        };
    }

    private Map<UUID, Integer> buildRankMap(List<LeaderboardRankRow> ranking) {
        Map<UUID, Integer> map = new HashMap<>();
        for (int i = 0; i < ranking.size(); i++) {
            map.put(ranking.get(i).userId(), i + 1);
        }
        return map;
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
