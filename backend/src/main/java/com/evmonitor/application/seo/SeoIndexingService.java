package com.evmonitor.application.seo;

import com.evmonitor.application.seo.SeoAudit.IndexInfo;
import com.evmonitor.infrastructure.external.gsc.GscClient;
import com.evmonitor.infrastructure.external.gsc.GscClient.IndexStatus;
import com.evmonitor.infrastructure.external.indexnow.IndexNowClient;
import com.evmonitor.infrastructure.persistence.seo.SeoAuditRepository;
import com.evmonitor.infrastructure.persistence.seo.SeoAuditRepository.AuditRow;
import com.evmonitor.infrastructure.persistence.seo.SeoIndexRepository;
import com.evmonitor.infrastructure.persistence.seo.SeoIndexRepository.IndexRow;
import com.evmonitor.infrastructure.persistence.seo.SeoIndexRepository.Inspection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Nach dem nächtlichen Crawl: Google-Index-Status je Sitemap-URL (URL Inspection API) und Meldung geänderter Seiten
 * an IndexNow (Bing, Yandex, Seznam). Geändert heißt: der Inhalts-Hash weicht von der letzten angenommenen Meldung ab.
 * Grundlage ist immer der letzte Prüftag, gemeldet werden nur erreichbare Seiten ohne noindex.
 */
@Service
@Slf4j
public class SeoIndexingService {

    private final GscClient gsc;
    private final IndexNowClient indexNow;
    private final SeoAuditRepository audits;
    private final SeoIndexRepository index;
    private final Deps deps;

    /** Konfiguration, JSON und Uhr gebündelt, damit der Konstruktor unter sechs Parametern bleibt. */
    record Deps(SeoAuditProperties properties, ObjectMapper objectMapper, Clock clock) {
    }

    @Autowired
    public SeoIndexingService(GscClient gsc, IndexNowClient indexNow, SeoAuditRepository audits, SeoIndexRepository index,
                              SeoAuditProperties properties) {
        this(gsc, indexNow, audits, index, new Deps(properties, new ObjectMapper(), Clock.systemDefaultZone()));
    }

    SeoIndexingService(GscClient gsc, IndexNowClient indexNow, SeoAuditRepository audits, SeoIndexRepository index, Deps deps) {
        this.gsc = gsc;
        this.indexNow = indexNow;
        this.audits = audits;
        this.index = index;
        this.deps = deps;
    }

    public boolean isIndexNowEnabled() {
        return indexNow.isEnabled();
    }

    public void run() {
        List<AuditRow> rows = latestRows();
        if (rows.isEmpty()) return;
        index.retainOnly(rows.stream().map(AuditRow::path).toList());
        inspectAll(rows);
        submitChanged(rows);
    }

    /**
     * Meldet einen Pfad sofort an IndexNow, auch ohne Änderung. Nur Pfade des letzten Prüftags, die erreichbar sind
     * und kein noindex tragen; sonst IllegalArgumentException.
     */
    public boolean submitManual(String path) {
        AuditRow row = latestRows().stream().filter(r -> r.path().equals(path)).findFirst()
                .filter(this::indexable)
                .orElseThrow(() -> new IllegalArgumentException("Pfad steht nicht als indexierbare Seite in der Sitemap"));
        boolean ok = indexNow.submit(List.of(path));
        if (ok) index.saveIndexNow(Map.of(path, row.contentHash()), now());
        return ok;
    }

    /** Index-Stand je Pfad für die Technik-Ansicht. */
    public Map<String, IndexInfo> latest() {
        Map<String, IndexInfo> out = new LinkedHashMap<>();
        index.all().forEach((path, r) -> out.put(path, new IndexInfo(r.inspection().verdict(), r.inspection().coverageState(),
                r.inspection().lastCrawlAt(), r.inspection().googleCanonical(), r.checkedAt(), r.indexnowAt())));
        return out;
    }

    /** Am längsten nicht geprüfte Seiten zuerst: bricht ein Lauf ab, kommt der Rest beim nächsten Mal dran. */
    private void inspectAll(List<AuditRow> rows) {
        if (!deps.properties().isIndexInspectionEnabled() || !gsc.isEnabled()) return;
        Map<String, IndexRow> known = index.all();
        List<AuditRow> ordered = rows.stream()
                .sorted(java.util.Comparator.comparing((AuditRow r) -> Optional.ofNullable(known.get(r.path())).map(IndexRow::checkedAt).orElse(null),
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .toList();
        int checked = 0;
        for (AuditRow r : ordered) {
            if (r.httpStatus() != 200) continue;
            pause();
            try {
                IndexStatus s = gsc.inspect("https://" + deps.properties().getSiteHost() + r.path());
                LocalDateTime lastCrawl = s.lastCrawlTime() == null ? null
                        : LocalDateTime.ofInstant(s.lastCrawlTime(), deps.clock().getZone());
                index.saveInspection(r.path(), now(), new Inspection(s.verdict(), s.coverageState(), lastCrawl, s.googleCanonical()));
                checked++;
            } catch (Exception e) {
                // Meist Kontingent (429) oder Token: der Rest des Laufs hätte denselben Fehler
                log.warn("SEO-Index: Prüfung nach {} URLs abgebrochen: {}", checked, e.getMessage());
                return;
            }
        }
        log.info("SEO-Index: {} URLs bei Google geprüft", checked);
    }

    private void submitChanged(List<AuditRow> rows) {
        if (!indexNow.isEnabled()) return;
        Map<String, IndexRow> known = index.all();
        Map<String, String> changed = new LinkedHashMap<>();
        for (AuditRow r : rows) {
            if (!indexable(r)) continue;
            String sent = Optional.ofNullable(known.get(r.path())).map(IndexRow::indexnowHash).orElse(null);
            if (!Objects.equals(sent, r.contentHash())) changed.put(r.path(), r.contentHash());
        }
        if (changed.isEmpty()) return;
        if (indexNow.submit(List.copyOf(changed.keySet()))) {
            index.saveIndexNow(changed, now());
            log.info("IndexNow: {} geänderte Seiten gemeldet", changed.size());
        }
    }

    private boolean indexable(AuditRow r) {
        return r.httpStatus() == 200 && r.contentHash() != null && !hasNoindex(r.findings());
    }

    private boolean hasNoindex(String findingsJson) {
        try {
            for (JsonNode f : deps.objectMapper().readTree(findingsJson)) {
                if (SeoAudit.Check.NOINDEX.name().equals(f.path("check").asText())) return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private List<AuditRow> latestRows() {
        return audits.latestDay().map(audits::rows).orElse(List.of());
    }

    private void pause() {
        long delay = deps.properties().getDelayMs();
        if (delay <= 0) return;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(deps.clock());
    }
}
