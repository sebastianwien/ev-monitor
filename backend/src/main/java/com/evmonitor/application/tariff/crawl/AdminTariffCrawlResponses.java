package com.evmonitor.application.tariff.crawl;

import com.evmonitor.application.tariff.AdminTariffResponses;
import com.evmonitor.infrastructure.persistence.TariffProposalEntity;
import com.evmonitor.infrastructure.persistence.TariffSourceEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Antworten und Eingaben des Crawler-Teils im Admin-Tab "Ladekarten". */
public final class AdminTariffCrawlResponses {

    private AdminTariffCrawlResponses() {}

    /**
     * {@code extractorAvailable} false: weder Anthropic- noch Mistral-Key, nur WATCH-Quellen laufen.
     * {@code autoApply} false: jede Preisänderung wartet auf den Admin.
     */
    public record Overview(List<SourceRow> sources, List<ProposalRow> proposals, boolean extractorAvailable, boolean enabled,
                           boolean autoApply) {}

    public record SourceRow(UUID id, String empName, String url, String mode, boolean enabled, LocalDateTime lastFetchedAt,
                            LocalDateTime lastSuccessAt, String lastStatus, Integer lastHttpStatus, String lastError,
                            int consecutiveFailures) {

        public static SourceRow of(TariffSourceEntity s) {
            return new SourceRow(s.getId(), s.getEmpName(), s.getUrl(), s.getMode().name(), s.isEnabled(), s.getLastFetchedAt(),
                    s.getLastSuccessAt(), s.getLastStatus(), s.getLastHttpStatus(), s.getLastError(), s.getConsecutiveFailures());
        }
    }

    /** {@code current}: der gepflegte Tarif, auf den sich der Vorschlag bezieht (CHANGE, MISSING), sonst null. */
    public record ProposalRow(UUID id, UUID sourceId, String empName, String kind, String description, Map<String, Object> proposed,
                              AdminTariffResponses.Row current, String evidence, String holdReason, int seenCount,
                              LocalDateTime firstSeenAt, LocalDateTime lastSeenAt) {

        public static ProposalRow of(TariffProposalEntity p, AdminTariffResponses.Row current) {
            return new ProposalRow(p.getId(), p.getSourceId(), p.getEmpName(), p.getKind().name(), TariffCrawlScheduler.describe(p),
                    p.getProposed(), current, p.getEvidence(), p.getHoldReason(), p.getSeenCount(), p.getFirstSeenAt(), p.getLastSeenAt());
        }
    }

    /** Quelle anlegen oder ändern. Nur https; der Crawler prüft den Host beim Abruf zusätzlich. */
    public record SourceCommand(
            @NotBlank(message = "Anbieter ist Pflicht.") @Size(max = 100) String empName,
            @NotBlank @Size(max = 500) @Pattern(regexp = "https://[^\\s/?#@]+(/\\S*)?", message = "Quelle muss ein https-Link sein.") String url,
            @NotNull @Pattern(regexp = "EXTRACT|WATCH", message = "Modus ist EXTRACT oder WATCH.") String mode,
            boolean enabled) {}

    public record Applied(boolean applied) {}
}
