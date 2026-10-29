package com.evmonitor.application.tariff.crawl;

import com.evmonitor.application.tariff.crawl.TariffExtractor.OperatorName;
import com.evmonitor.application.tariff.crawl.TariffExtractor.TierMembers;
import com.evmonitor.application.tariff.crawl.TariffProposalBook.Draft;
import com.evmonitor.application.tariff.crawl.TariffReconciler.Change;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.infrastructure.persistence.JpaTariffSourceRepository;
import com.evmonitor.infrastructure.persistence.TariffProposalEntity;
import com.evmonitor.infrastructure.persistence.TariffProposalEntity.Kind;
import com.evmonitor.infrastructure.persistence.TariffSourceEntity;
import com.evmonitor.infrastructure.persistence.TariffSourceEntity.Mode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ein Lauf des Tarif-Crawlers über alle fälligen Quellen.
 *
 * <ul>
 *   <li>Seite nicht lesbar: Fehler zählen, nichts an den Tarifen ändern.</li>
 *   <li>WATCH (Preise nur als Bild): Fingerabdruck gleich dem bestätigten heißt "unverändert",
 *       sonst ein Vorschlag für den Admin.</li>
 *   <li>EXTRACT: Seite unverändert und nichts offen heißt "unverändert", ohne Sprachmodell.
 *       Sonst extrahieren, gegen die Tabelle abgleichen, Unterschiede ins Vorschlagsbuch.</li>
 * </ul>
 * "Unverändert" setzt den Stand der Tarife dieser Quelle auf jetzt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TariffCrawlService {

    private final JpaTariffSourceRepository sources;
    private final TariffSourceReader reader;
    private final TariffProposalBook book;
    private final ChargingProviderTariffRepository tariffs;
    private final TariffCrawlProperties props;

    /** Status, die am nächsten Tag einen neuen Versuch auslösen statt erst nach {@code refetch-days}. */
    private static final java.util.Set<String> RETRY_SOON = java.util.Set.of("HTTP_ERROR", "ROBOTS", "UNREADABLE", "EXTRACT_ERROR");

    /**
     * Ergebnis eines Laufs: je Quelle der Status, plus was ein Admin entscheiden muss.
     * {@code extractionErrors}: Fehler des Sprachmodells (keine Seitenfehler), je Text einmal.
     */
    public record RunSummary(Map<String, String> statusBySource, int applied, List<TariffProposalEntity> needAdmin,
                             List<TariffSourceEntity> newlyFailing, List<String> extractionErrors) {}

    /** {@code onlyDue} false: alle aktiven Quellen (Knopf im Admin-Tab). */
    public RunSummary run(boolean onlyDue, LocalDateTime now) {
        Map<String, String> status = new LinkedHashMap<>();
        List<TariffProposalEntity> needAdmin = new ArrayList<>();
        List<TariffSourceEntity> newlyFailing = new ArrayList<>();
        java.util.Set<String> extractionErrors = new java.util.LinkedHashSet<>();
        int applied = 0;
        for (TariffSourceEntity source : sources.findByEnabledTrueOrderByEmpNameAsc()) {
            if (onlyDue && !isDue(source, now)) continue;
            try {
                TariffProposalBook.Recorded r = crawl(source, now);
                applied += r.applied();
                needAdmin.addAll(r.needAdmin());
            } catch (TariffExtractionException e) {
                // Das Sprachmodell, nicht die Seite: kein Seitenfehler, aber einmal je Lauf gemeldet.
                source.setLastStatus("EXTRACT_ERROR");
                source.setLastError(e.getMessage());
                extractionErrors.add(e.getMessage());
            } catch (RuntimeException e) {
                log.error("Tarif-Crawler: Quelle {} abgebrochen", source.getUrl(), e);
                source.setLastStatus("EXTRACT_ERROR");
                source.setLastError("interner Fehler");
                extractionErrors.add("interner Fehler, siehe Log");
            }
            if (source.getConsecutiveFailures() == 2) newlyFailing.add(source);
            sources.save(source);
            status.put(source.getEmpName() + " " + source.getUrl(), source.getLastStatus());
        }
        return new RunSummary(status, applied, needAdmin, newlyFailing, List.copyOf(extractionErrors));
    }

    /**
     * Regulär alle {@code refetch-days}. Nach {@code confirm-after-hours} schon wieder, wenn ein bestätigbarer
     * Vorschlag offen ist oder der letzte Lauf scheiterte (Seite oder Sprachmodell).
     */
    boolean isDue(TariffSourceEntity source, LocalDateTime now) {
        if (source.getLastFetchedAt() == null) return true;
        if (!now.isBefore(source.getLastFetchedAt().plusDays(props.getRefetchDays()))) return true;
        if (now.isBefore(source.getLastFetchedAt().plusHours(props.getConfirmAfterHours()))) return false;
        return RETRY_SOON.contains(source.getLastStatus()) || book.hasConfirmable(source.getId());
    }

    private TariffProposalBook.Recorded crawl(TariffSourceEntity source, LocalDateTime now) {
        TariffPageFetcher.Fetched fetched = reader.fetch(source.getUrl());
        source.setLastFetchedAt(now);
        source.setLastHttpStatus(fetched.httpStatus() == 0 ? null : fetched.httpStatus());
        if (fetched.outcome() != TariffPageFetcher.Outcome.OK) {
            fail(source, fetched.outcome() == TariffPageFetcher.Outcome.ROBOTS ? "ROBOTS" : "HTTP_ERROR",
                    fetched.httpStatus(), fetched.error(), now);
            return new TariffProposalBook.Recorded(0, List.of());
        }
        PageText page = fetched.page();
        String fingerprint = page.fingerprint();
        List<ChargingProviderTariff> known = tariffs.findValidOn(now.toLocalDate()).stream()
                .filter(t -> source.getUrl().equals(t.getSourceUrl()))
                .toList();
        if (source.getMode() == Mode.WATCH) return watch(source, page, fingerprint, known, now);

        if (fingerprint.equals(source.getContentHash()) && !book.hasOpen(source.getId())) {
            succeed(source, "UNCHANGED", fingerprint, now);
            book.touch(known, now);
            return new TariffProposalBook.Recorded(0, List.of());
        }
        if (!reader.canExtract()) {
            source.setLastStatus("EXTRACT_ERROR");
            source.setLastError("Kein Sprachmodell konfiguriert (ANTHROPIC_API_KEY oder MISTRAL_API_KEY)");
            return new TariffProposalBook.Recorded(0, List.of());
        }
        TariffSourceReader.Reading reading = reader.read(source, page, known);
        if (reading.tariffs().isEmpty()) {
            fail(source, "UNREADABLE", fetched.httpStatus(), "Keine belegten Preise auf der Seite gefunden", now);
            return new TariffProposalBook.Recorded(0, List.of());
        }
        TariffReconciler.Result result = TariffReconciler.reconcile(known, reading.tariffs());
        book.touch(result.unchanged(), now);
        List<Draft> drafts = new ArrayList<>();
        for (Change c : result.changes()) {
            drafts.add(new Draft(Kind.CHANGE, c.current().getId(), proposed(c.found()), c.found().evidence(), c.holdReason()));
        }
        for (ExtractedTariff added : result.added()) {
            drafts.add(new Draft(Kind.NEW, null, proposed(added), added.evidence(), "Neuer Tarif auf der Seite"));
        }
        for (ChargingProviderTariff missing : result.missing()) {
            drafts.add(new Draft(Kind.MISSING, missing.getId(), TariffProposalBook.toMap(TariffSourceReader.nameOnly(missing)),
                    null, "Steht nicht mehr auf der Seite"));
        }
        drafts.addAll(mappingDrafts(source.getEmpName(), reading.tiers()));
        succeed(source, drafts.isEmpty() ? "OK" : "CHANGED", fingerprint, now);
        return book.record(source, drafts, now);
    }

    private TariffProposalBook.Recorded watch(TariffSourceEntity source, PageText page, String fingerprint,
                                              List<ChargingProviderTariff> known, LocalDateTime now) {
        if (source.getApprovedHash() == null) {
            // Erste Sichtung: die Werte wurden beim Anlegen der Quelle von Hand geprüft.
            source.setApprovedHash(fingerprint);
        }
        if (fingerprint.equals(source.getApprovedHash())) {
            succeed(source, "UNCHANGED", fingerprint, now);
            book.touch(known, now);
            return book.record(source, List.of(), now);
        }
        succeed(source, "CHANGED", fingerprint, now);
        Map<String, Object> proposed = new LinkedHashMap<>();
        proposed.put("pageFingerprint", fingerprint);
        proposed.put("priceImages", page.priceImages());
        return book.record(source, List.of(new Draft(Kind.WATCH_CHANGED, null, proposed, null,
                "Preisbilder oder Text der Seite haben sich geändert")), now);
    }

    /** Nur Betreiber, die die Seite nennt und deren Stufe abweicht; "und viele weitere" bleibt unangetastet. */
    private List<Draft> mappingDrafts(String empName, List<TierMembers> tiers) {
        List<Draft> drafts = new ArrayList<>();
        for (TierMembers m : tiers) {
            for (OperatorName o : m.operators()) {
                Optional<String> current = tariffs.findTierForCpo(empName, o.catalog());
                if (current.isPresent() && current.get().equals(m.tier())) continue;
                Map<String, Object> proposed = new LinkedHashMap<>();
                proposed.put("operator", o.catalog());
                proposed.put("tier", m.tier());
                proposed.put("previousTier", current.orElse(null));
                drafts.add(new Draft(Kind.MAPPING, null, proposed, o.onPage(), "Preisstufe eines Betreibers geändert"));
            }
        }
        return drafts;
    }

    /** Werte für den Vorschlag, ohne Zitat und Seitennamen: die zählen nicht zum Fingerabdruck. */
    private static Map<String, Object> proposed(ExtractedTariff f) {
        Map<String, Object> m = TariffProposalBook.toMap(f);
        m.remove("evidence");
        m.remove("operatorOnPage");
        return m;
    }

    private static void succeed(TariffSourceEntity source, String status, String fingerprint, LocalDateTime now) {
        source.setLastStatus(status);
        source.setLastSuccessAt(now);
        source.setLastError(null);
        source.setConsecutiveFailures(0);
        source.setContentHash(fingerprint);
    }

    private static void fail(TariffSourceEntity source, String status, int httpStatus, String error, LocalDateTime now) {
        source.setLastFetchedAt(now);
        source.setLastStatus(status);
        source.setLastHttpStatus(httpStatus == 0 ? null : httpStatus);
        source.setLastError(error == null ? null : error.length() > 300 ? error.substring(0, 300) : error);
        source.setConsecutiveFailures(source.getConsecutiveFailures() + 1);
    }
}
