package com.evmonitor.application.seo;

import com.evmonitor.application.seo.SeoAudit.Vitals;
import com.evmonitor.application.seo.SeoAudit.VitalsSnapshot;
import com.evmonitor.infrastructure.external.audit.PageSpeedClient;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository;
import com.evmonitor.infrastructure.persistence.seo.SeoAuditRepository;
import com.evmonitor.infrastructure.persistence.seo.SeoAuditRepository.VitalsRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Core Web Vitals einmal je Woche für die meistgesehenen Seiten (Impressionen der letzten 28 Tage), nur Seiten,
 * die im letzten Prüflauf mit 200 antworteten. Ohne PageSpeed-Key aus.
 */
@Service
@Slf4j
public class SeoVitalsService {

    static final int IMPRESSION_DAYS = 28;

    private final PageSpeedClient client;
    private final GscDailyRepository gsc;
    private final SeoAuditRepository repository;
    private final SeoAuditProperties properties;
    private final Clock clock;

    @Autowired
    public SeoVitalsService(PageSpeedClient client, GscDailyRepository gsc, SeoAuditRepository repository, SeoAuditProperties properties) {
        this(client, gsc, repository, properties, Clock.systemDefaultZone());
    }

    SeoVitalsService(PageSpeedClient client, GscDailyRepository gsc, SeoAuditRepository repository, SeoAuditProperties properties,
                     Clock clock) {
        this.client = client;
        this.gsc = gsc;
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Misst, wenn die laufende Woche noch keine Werte hat. Gibt die Zahl gespeicherter Seiten zurück. */
    public int refreshIfDue() {
        if (!properties.isVitalsEnabled() || !client.isEnabled()) return 0;
        LocalDate week = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (repository.latestVitalsWeek().filter(week::equals).isPresent()) return 0;
        Optional<LocalDate> auditDay = repository.latestDay();
        if (auditDay.isEmpty()) return 0;

        List<VitalsRow> rows = new ArrayList<>();
        for (String path : topPaths(auditDay.get())) {
            client.measure("https://" + properties.getSiteHost() + path)
                    .ifPresent(v -> rows.add(new VitalsRow(path, v.lcpMs(), v.cls(), v.inpMs(), v.source())));
        }
        repository.replaceWeek(week, rows);
        log.info("Core Web Vitals Woche {}: {} Seiten gemessen", week, rows.size());
        return rows.size();
    }

    /** Erreichbare Seiten des Prüftags nach Impressionen; ohne GSC-Daten in Sitemap-Reihenfolge aufgefüllt. */
    private List<String> topPaths(LocalDate auditDay) {
        Set<String> candidates = new LinkedHashSet<>();
        repository.rows(auditDay).stream().filter(r -> r.httpStatus() == 200).forEach(r -> candidates.add(r.path()));
        int max = Math.max(0, properties.getVitalsTopPages());
        Set<String> top = new LinkedHashSet<>();
        gsc.pageTotals(auditDay.minusDays(IMPRESSION_DAYS), auditDay.minusDays(1)).stream()
                .filter(p -> candidates.contains(p.path()))
                .sorted(Comparator.comparingLong(GscDailyRepository.PageTotals::impressions).reversed())
                .limit(max)
                .forEach(p -> top.add(p.path()));
        for (String path : candidates) {
            if (top.size() >= max) break;
            top.add(path);
        }
        return new ArrayList<>(top);
    }

    public VitalsSnapshot latest() {
        Optional<LocalDate> week = repository.latestVitalsWeek();
        if (week.isEmpty()) return VitalsSnapshot.EMPTY;
        Map<String, Vitals> byPath = new LinkedHashMap<>();
        for (VitalsRow r : repository.vitals(week.get())) {
            byPath.put(r.path(), new Vitals(r.lcpMs(), r.cls(), r.inpMs(), r.source()));
        }
        return new VitalsSnapshot(week.get(), byPath);
    }
}
