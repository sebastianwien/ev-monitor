package com.evmonitor.application.recap;

import com.evmonitor.application.EvLogStatisticsService;
import com.evmonitor.application.LeaderboardService;
import com.evmonitor.application.MyLeaderboardStandingDTO;
import com.evmonitor.application.PersonalTickerService;
import com.evmonitor.application.recap.RecapHighlights.PeerConsumption;
import com.evmonitor.application.recap.RecapHighlights.Place;
import com.evmonitor.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Plätze und Modellvergleich für die Monatsrückblick-Mail, nach denselben Regeln wie die
 * Rückblick-Karte der Story. Fällt eine Quelle aus, fehlt nur ihre Zeile, die Mail geht trotzdem.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecapHighlightsService {

    static final int PODIUM = 3;
    /** Die Top-10-Listen sind öffentlich, darüber hinaus sagt ein Platz allein wenig. */
    static final int TOP = 10;

    private final LeaderboardService leaderboardService;
    private final UserRepository userRepository;
    private final EvLogStatisticsService statisticsService;

    public RecapHighlights highlights(UUID userId, UUID carId, YearMonth month) {
        return new RecapHighlights(places(userId, month), peer(userId, carId, month));
    }

    private List<Place> places(UUID userId, YearMonth month) {
        try {
            if (!userRepository.isLeaderboardVisible(userId)) return List.of();
            // Letzter Tag des Monats als Referenzdatum: der Endstand des ganzen Monats.
            return recapPlaces(leaderboardService.getMyStandings(userId, month.atEndOfMonth()));
        } catch (RuntimeException e) {
            log.warn("Monthly recap: ranks unavailable for user {}", userId, e);
            return List.of();
        }
    }

    /** Treppchenplätze alle (nach Platz sortiert), sonst nur der beste Platz, wenn er in den Top 10 liegt. */
    static List<Place> recapPlaces(List<MyLeaderboardStandingDTO> standings) {
        List<Place> all = standings.stream()
                .filter(s -> s.rank() != null)
                .sorted(Comparator.comparingInt(MyLeaderboardStandingDTO::rank))
                .map(s -> new Place(s.category(), s.rank()))
                .toList();
        List<Place> podium = all.stream().filter(p -> p.rank() <= PODIUM).toList();
        if (!podium.isEmpty()) return podium;
        return !all.isEmpty() && all.get(0).rank() <= TOP ? List.of(all.get(0)) : List.of();
    }

    /** Gleicher Monat, gleiches Modell, ab drei andere Nutzer, wie der Eintrag "my_consumption" im Ticker. */
    private PeerConsumption peer(UUID userId, UUID carId, YearMonth month) {
        try {
            return statisticsService.getPeerBenchmark(carId, userId, month.atDay(1), month.atEndOfMonth())
                    .filter(pb -> pb.userPeriodConsumptionKwhPer100km() != null
                            && pb.peerAvgConsumptionKwhPer100km() != null
                            && !pb.peerConsumptionLifetime()
                            && pb.uniquePeerUsers() >= PersonalTickerService.MIN_PEER_USERS)
                    .map(pb -> new PeerConsumption(pb.userPeriodConsumptionKwhPer100km(), pb.peerAvgConsumptionKwhPer100km()))
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("Monthly recap: peer consumption unavailable for car {}", carId, e);
            return null;
        }
    }
}
