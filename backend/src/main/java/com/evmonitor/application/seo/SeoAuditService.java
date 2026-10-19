package com.evmonitor.application.seo;

import com.evmonitor.application.seo.SeoAudit.AuditRow;
import com.evmonitor.application.seo.SeoAudit.Check;
import com.evmonitor.application.seo.SeoAudit.Finding;
import com.evmonitor.application.seo.SeoAudit.PageReport;
import com.evmonitor.application.seo.SeoAudit.Severity;
import com.evmonitor.application.seo.SeoAudit.Summary;
import com.evmonitor.application.seo.SeoAudit.View;
import com.evmonitor.application.seo.SeoAudit.Vitals;
import com.evmonitor.application.seo.SeoAudit.VitalsSnapshot;
import com.evmonitor.infrastructure.external.audit.SiteCrawler;
import com.evmonitor.infrastructure.external.audit.SiteCrawler.Fetched;
import com.evmonitor.infrastructure.persistence.seo.SeoAuditRepository;
import com.evmonitor.infrastructure.persistence.seo.SeoSyncRunEntity;
import com.evmonitor.infrastructure.persistence.seo.SeoSyncRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Nächtlicher On-Page-Check: alle Sitemap-URLs holen und prüfen, Linkziele außerhalb der Sitemap einmal
 * nachholen, daraus kaputte und verwaiste Seiten ableiten. Ein Prüftag wird komplett ersetzt.
 */
@Service
@Slf4j
public class SeoAuditService {

    public static final String SOURCE_AUDIT = "AUDIT";
    private static final TypeReference<List<Finding>> FINDINGS = new TypeReference<>() {
    };

    /** pages = geprüfte Sitemap-URLs, extraUrls = zusätzlich geholte Linkziele. */
    public record AuditResult(UUID runId, int pages, int extraUrls, String status) {
    }

    private final SiteCrawler crawler;
    private final PageAuditor auditor;
    private final SeoAuditRepository repository;
    private final SeoSyncRunRepository runs;
    private final Deps deps;

    /** Konfiguration, JSON und Uhr gebündelt, damit der Konstruktor unter sechs Parametern bleibt. */
    record Deps(SeoAuditProperties properties, ObjectMapper objectMapper, Clock clock) {
    }

    @Autowired
    public SeoAuditService(SiteCrawler crawler, PageAuditor auditor, SeoAuditRepository repository, SeoSyncRunRepository runs,
                           SeoAuditProperties properties) {
        this(crawler, auditor, repository, runs, new Deps(properties, new ObjectMapper(), Clock.systemDefaultZone()));
    }

    SeoAuditService(SiteCrawler crawler, PageAuditor auditor, SeoAuditRepository repository, SeoSyncRunRepository runs, Deps deps) {
        this.crawler = crawler;
        this.auditor = auditor;
        this.repository = repository;
        this.runs = runs;
        this.deps = deps;
    }

    public boolean isEnabled() {
        return deps.properties().isEnabled();
    }

    public AuditResult run() {
        LocalDate today = LocalDate.now(deps.clock());
        SeoSyncRunEntity run = new SeoSyncRunEntity(UUID.randomUUID(), SOURCE_AUDIT, LocalDateTime.now(deps.clock()), null,
                SeoSyncRunEntity.STATUS_RUNNING, 0, today, today, null);
        runs.save(run);

        List<String> sitemap;
        try {
            sitemap = crawler.sitemapPaths();
        } catch (Exception e) {
            log.warn("SEO-Audit: Sitemap nicht lesbar: {}", e.getMessage());
            return finish(run, SeoSyncRunEntity.STATUS_FAILED, 0, e.getMessage(), new AuditResult(run.getId(), 0, 0, SeoSyncRunEntity.STATUS_FAILED));
        }

        Map<String, PageReport> reports = new LinkedHashMap<>();
        for (String path : sitemap) {
            Fetched f = fetchPolitely(path);
            reports.put(path, auditor.audit(path, f.status(), f.body()));
        }
        Map<String, String> problems = new HashMap<>();
        reports.forEach((path, r) -> problemOf(r).ifPresent(p -> problems.put(path, p)));
        int extras = checkExtraTargets(reports, problems);

        List<SeoAuditRepository.AuditRow> rows = new ArrayList<>();
        Map<String, Integer> inbound = inbound(reports);
        int unreachable = 0;
        for (PageReport r : reports.values()) {
            if (r.status() == 0) unreachable++;
            List<Finding> findings = new ArrayList<>(r.findings());
            for (String link : r.links()) {
                String problem = problems.get(link);
                if (problem != null) findings.add(new Finding(Check.BROKEN_LINK, Severity.ERROR, "Link auf " + link + ": " + problem));
            }
            if (!"/".equals(r.path()) && inbound.getOrDefault(r.path(), 0) == 0) {
                findings.add(new Finding(Check.ORPHAN, Severity.WARNING, "Keine andere Seite der Sitemap verlinkt hierher"));
            }
            rows.add(new SeoAuditRepository.AuditRow(r.path(), r.status(), SeoAudit.score(findings),
                    count(findings, Severity.ERROR), count(findings, Severity.WARNING), json(findings)));
        }
        repository.replaceDay(today, rows);
        int purged = repository.purgeBefore(today.minusDays(deps.properties().getRetentionDays()));
        if (purged > 0) log.info("SEO-Audit: {} alte Zeilen gelöscht", purged);

        String status = unreachable == 0 ? SeoSyncRunEntity.STATUS_SUCCESS
                : unreachable < reports.size() ? SeoSyncRunEntity.STATUS_PARTIAL
                : SeoSyncRunEntity.STATUS_FAILED;
        String error = unreachable == 0 ? null : unreachable + " von " + reports.size() + " Seiten nicht erreichbar";
        log.info("SEO-Audit {}: {} Seiten, {} Linkziele, Status {}", today, reports.size(), extras, status);
        return finish(run, status, rows.size(), error, new AuditResult(run.getId(), reports.size(), extras, status));
    }

    /** Linkziele außerhalb der Sitemap einmal holen, Sprachvarianten auslassen, gedeckelt. */
    private int checkExtraTargets(Map<String, PageReport> reports, Map<String, String> problems) {
        List<String> targets = new ArrayList<>();
        for (PageReport r : reports.values()) {
            for (String link : r.links()) {
                if (!reports.containsKey(link) && !targets.contains(link) && !ignored(link)) targets.add(link);
            }
        }
        int max = Math.max(0, deps.properties().getMaxExtraUrls());
        if (targets.size() > max) {
            log.info("SEO-Audit: {} Linkziele außerhalb der Sitemap, nur {} geprüft", targets.size(), max);
        }
        List<String> checked = targets.subList(0, Math.min(max, targets.size()));
        for (String target : checked) {
            Fetched f = fetchPolitely(target);
            problemOf(auditor.audit(target, f.status(), f.body())).ifPresent(p -> problems.put(target, p));
        }
        return checked.size();
    }

    private boolean ignored(String path) {
        return deps.properties().getIgnoredLinkPrefixes().stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    /** Warum ein Linkziel kaputt ist; leer, wenn es mit 200 und ohne noindex antwortet. */
    private static Optional<String> problemOf(PageReport r) {
        if (r.status() == 0) return Optional.of("nicht erreichbar");
        if (r.status() != 200) return Optional.of("HTTP " + r.status());
        if (r.noindex()) return Optional.of("noindex");
        return Optional.empty();
    }

    private static Map<String, Integer> inbound(Map<String, PageReport> reports) {
        Map<String, Integer> inbound = new HashMap<>();
        for (PageReport r : reports.values()) {
            for (String link : r.links()) {
                if (!link.equals(r.path()) && reports.containsKey(link)) inbound.merge(link, 1, Integer::sum);
            }
        }
        return inbound;
    }

    private Fetched fetchPolitely(String path) {
        long delay = deps.properties().getDelayMs();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return crawler.fetch(path);
    }

    private AuditResult finish(SeoSyncRunEntity run, String status, int rows, String error, AuditResult result) {
        run.setFinishedAt(LocalDateTime.now(deps.clock()));
        run.setStatus(status);
        run.setRowCount(rows);
        run.setError(error);
        runs.save(run);
        return result;
    }

    /** Letzter Prüftag ohne Core Web Vitals. */
    public View view() {
        return view(VitalsSnapshot.EMPTY);
    }

    /**
     * Letzter Prüftag für den Unter-Tab, schlechteste zuerst. Vitals kommen wöchentlich und werden hier an jeden
     * Prüftag gehängt; Werte über dem Grenzwert zählen als Hinweis in Score und Hinweiszahl.
     */
    public View view(VitalsSnapshot vitals) {
        Optional<LocalDate> day = repository.latestDay();
        LocalDateTime lastRunAt = runs.findTop10ByOrderByStartedAtDesc().stream()
                .filter(r -> SOURCE_AUDIT.equals(r.getSource()))
                .map(SeoSyncRunEntity::getStartedAt)
                .findFirst().orElse(null);
        if (day.isEmpty()) {
            return new View(isEnabled(), null, lastRunAt, vitals.week(), new Summary(0, 0, 0, 0, 0), List.of());
        }
        List<AuditRow> rows = repository.rows(day.get()).stream()
                .map(r -> withVitals(r, vitals.byPath().get(r.path())))
                .sorted(Comparator.comparingInt(AuditRow::score).thenComparing(AuditRow::path))
                .toList();
        return new View(isEnabled(), day.get(), lastRunAt, vitals.week(), summary(rows), rows);
    }

    private AuditRow withVitals(SeoAuditRepository.AuditRow r, Vitals v) {
        List<Finding> findings = new ArrayList<>(findings(r.findings()));
        if (v == null) return new AuditRow(r.path(), r.httpStatus(), r.score(), r.errorCount(), r.warningCount(), findings, null);
        List<Finding> cwv = cwvFindings(v);
        findings.addAll(cwv);
        return new AuditRow(r.path(), r.httpStatus(), SeoAudit.score(findings), r.errorCount(), r.warningCount() + cwv.size(), findings, v);
    }

    private List<Finding> cwvFindings(Vitals v) {
        SeoAuditProperties p = deps.properties();
        String label = "FIELD".equals(v.source()) ? "echte Nutzer" : "Labormessung";
        List<Finding> out = new ArrayList<>();
        if (v.lcpMs() != null && v.lcpMs() > p.getVitalsLcpMaxMs()) {
            out.add(new Finding(Check.CWV, Severity.WARNING, "LCP " + decimal(v.lcpMs() / 1000.0, 1) + " s über "
                    + decimal(p.getVitalsLcpMaxMs() / 1000.0, 1) + " s (mobil, " + label + ")"));
        }
        if (v.cls() != null && v.cls() > p.getVitalsClsMax()) {
            out.add(new Finding(Check.CWV, Severity.WARNING, "CLS " + decimal(v.cls(), 2) + " über " + decimal(p.getVitalsClsMax(), 2)
                    + " (mobil, " + label + ")"));
        }
        if (v.inpMs() != null && v.inpMs() > p.getVitalsInpMaxMs()) {
            out.add(new Finding(Check.CWV, Severity.WARNING, "INP " + v.inpMs() + " ms über " + p.getVitalsInpMaxMs() + " ms (mobil, " + label + ")"));
        }
        return out;
    }

    private static String decimal(double value, int digits) {
        return String.format(java.util.Locale.GERMANY, "%." + digits + "f", value);
    }

    private static Summary summary(List<AuditRow> rows) {
        int withErrors = 0;
        int prerender = 0;
        int broken = 0;
        int orphans = 0;
        for (AuditRow r : rows) {
            if (r.errors() > 0) withErrors++;
            if (has(r, Check.PRERENDER)) prerender++;
            if (has(r, Check.BROKEN_LINK)) broken++;
            if (has(r, Check.ORPHAN)) orphans++;
        }
        return new Summary(rows.size(), withErrors, prerender, broken, orphans);
    }

    private static boolean has(AuditRow r, Check check) {
        return r.findings().stream().anyMatch(f -> f.check() == check);
    }

    private static int count(List<Finding> findings, Severity severity) {
        return (int) findings.stream().filter(f -> f.severity() == severity).count();
    }

    private String json(List<Finding> findings) {
        try {
            return deps.objectMapper().writeValueAsString(findings);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Befunde nicht serialisierbar", e);
        }
    }

    private List<Finding> findings(String json) {
        try {
            return deps.objectMapper().readValue(json, FINDINGS);
        } catch (JsonProcessingException e) {
            log.warn("SEO-Audit: Befunde nicht lesbar: {}", e.getMessage());
            return List.of();
        }
    }
}
