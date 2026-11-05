package com.evmonitor.application.seo;

import com.evmonitor.infrastructure.persistence.seo.SeoAuditRepository;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Zählt Abrufe öffentlicher Seiten durch Such- und KI-Crawler. nginx meldet jeden Bot-Abruf einer Prerender-Seite;
 * gespeichert werden nur Tag, erkannter Bot und normalisierter Pfad, keine IP und kein User-Agent-Text.
 * Pfade, die nicht im letzten Technik-Crawl (= Sitemap) stehen, zählen in einer Sammelzeile: ein gefälschter
 * Bot-User-Agent mit erfundenen Pfaden erzeugt so keine neuen Zeilen.
 */
@Service
public class BotHitService {

    static final int TOP_PATHS = 5;
    /** Sammelzeile für Pfade außerhalb der Sitemap; beginnt absichtlich nicht mit "/". */
    public static final String OTHER_PATHS = "(nicht in der Sitemap)";

    public record PathHits(String path, long hits) {
    }

    /** Ein Bot im Zeitraum: Summe der Abrufe und die meistabgerufenen Seiten. */
    public record BotSummary(CrawlerBot bot, String label, boolean ai, long hits, List<PathHits> topPaths) {
    }

    private final SeoBotRepository repository;
    private final SeoAuditRepository audits;
    private final SeoAuditProperties properties;
    private final Clock clock;
    private volatile LocalDate purgedOn;
    private volatile Set<String> knownPaths = Set.of();
    private volatile LocalDate knownOn;

    @Autowired
    public BotHitService(SeoBotRepository repository, SeoAuditRepository audits, SeoAuditProperties properties) {
        this(repository, audits, properties, Clock.systemDefaultZone());
    }

    BotHitService(SeoBotRepository repository, SeoAuditRepository audits, SeoAuditProperties properties, Clock clock) {
        this.repository = repository;
        this.audits = audits;
        this.properties = properties;
        this.clock = clock;
    }

    /** Unbekannte User-Agents (Browser, eigener Audit) werden ignoriert. */
    public void record(String userAgent, String uri) {
        CrawlerBot.of(userAgent).ifPresent(bot -> {
            LocalDate today = LocalDate.now(clock);
            String path = SeoPath.normalize(uri);
            boolean newRow = repository.increment(today, bot.name(), known(today).contains(path) ? path : OTHER_PATHS);
            // Einmal am Tag beim ersten neuen Eintrag aufräumen, Aufbewahrung wie beim Audit
            if (newRow && !today.equals(purgedOn)) {
                purgedOn = today;
                repository.purgeBefore(today.minusDays(properties.getRetentionDays()));
            }
        });
    }

    /** Sitemap-Pfade des letzten Crawls, einmal je Tag geladen. */
    private Set<String> known(LocalDate today) {
        if (!today.equals(knownOn)) {
            knownPaths = audits.latestDay().map(d -> audits.rows(d).stream().map(SeoAuditRepository.AuditRow::path)
                    .collect(Collectors.toUnmodifiableSet())).orElse(Set.of());
            knownOn = today;
        }
        return knownPaths;
    }

    /** Verwirft die geladenen Sitemap-Pfade, der nächste Abruf lädt neu (Tests, frischer Crawl). */
    public void forgetKnownPaths() {
        knownOn = null;
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
            // echte Seiten zuerst, die Sammelzeile zuletzt
            paths.sort(Comparator.comparing((PathHits p) -> OTHER_PATHS.equals(p.path()))
                    .thenComparing(Comparator.comparingLong(PathHits::hits).reversed()).thenComparing(PathHits::path));
            long total = paths.stream().mapToLong(PathHits::hits).sum();
            out.add(new BotSummary(bot, bot.getLabel(), bot.isAi(), total, List.copyOf(paths.subList(0, Math.min(TOP_PATHS, paths.size())))));
        });
        out.sort(Comparator.comparing((BotSummary s) -> !s.ai()).thenComparing(Comparator.comparingLong(BotSummary::hits).reversed()));
        return out;
    }
}
