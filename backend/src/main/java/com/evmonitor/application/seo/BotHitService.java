package com.evmonitor.application.seo;

import com.evmonitor.infrastructure.persistence.seo.SeoBotRepository;
import com.evmonitor.infrastructure.persistence.seo.SeoBotRepository.BotPathHits;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Zählt Abrufe öffentlicher Seiten durch Such- und KI-Crawler. nginx meldet jeden Bot-Abruf einer Prerender-Seite;
 * gespeichert werden nur Tag, erkannter Bot und normalisierter Pfad, keine IP und kein User-Agent-Text.
 */
@Service
public class BotHitService {

    static final int TOP_PATHS = 5;

    public record PathHits(String path, long hits) {
    }

    /** Ein Bot im Zeitraum: Summe der Abrufe und die meistabgerufenen Seiten. */
    public record BotSummary(CrawlerBot bot, String label, boolean ai, long hits, List<PathHits> topPaths) {
    }

    private final SeoBotRepository repository;
    private final SeoAuditProperties properties;
    private final Clock clock;
    private volatile LocalDate purgedOn;

    @Autowired
    public BotHitService(SeoBotRepository repository, SeoAuditProperties properties) {
        this(repository, properties, Clock.systemDefaultZone());
    }

    BotHitService(SeoBotRepository repository, SeoAuditProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Unbekannte User-Agents (Browser, eigener Audit) werden ignoriert. */
    public void record(String userAgent, String uri) {
        CrawlerBot.of(userAgent).ifPresent(bot -> {
            LocalDate today = LocalDate.now(clock);
            boolean newRow = repository.increment(today, bot.name(), SeoPath.normalize(uri));
            // Einmal am Tag beim ersten neuen Eintrag aufräumen, Aufbewahrung wie beim Audit
            if (newRow && !today.equals(purgedOn)) {
                purgedOn = today;
                repository.purgeBefore(today.minusDays(properties.getRetentionDays()));
            }
        });
    }

    /** Letzte {@code days} Tage bis heute; KI-Crawler zuerst, dann nach Abrufen. */
    public List<BotSummary> summary(int days) {
        if (!SeoInsightsService.ALLOWED_PERIODS.contains(days)) {
            throw new IllegalArgumentException("days muss einer von " + SeoInsightsService.ALLOWED_PERIODS + " sein");
        }
        LocalDate to = LocalDate.now(clock);
        Map<CrawlerBot, List<PathHits>> byBot = new EnumMap<>(CrawlerBot.class);
        for (BotPathHits row : repository.totals(to.minusDays(days - 1L), to)) {
            CrawlerBot bot;
            try {
                bot = CrawlerBot.valueOf(row.bot());
            } catch (IllegalArgumentException e) {
                continue;
            }
            byBot.computeIfAbsent(bot, b -> new ArrayList<>()).add(new PathHits(row.path(), row.hits()));
        }
        List<BotSummary> out = new ArrayList<>();
        byBot.forEach((bot, paths) -> {
            paths.sort(Comparator.comparingLong(PathHits::hits).reversed().thenComparing(PathHits::path));
            long total = paths.stream().mapToLong(PathHits::hits).sum();
            out.add(new BotSummary(bot, bot.getLabel(), bot.isAi(), total, List.copyOf(paths.subList(0, Math.min(TOP_PATHS, paths.size())))));
        });
        out.sort(Comparator.comparing((BotSummary s) -> !s.ai()).thenComparing(Comparator.comparingLong(BotSummary::hits).reversed()));
        return out;
    }
}
