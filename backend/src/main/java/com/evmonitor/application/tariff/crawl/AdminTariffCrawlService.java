package com.evmonitor.application.tariff.crawl;

import com.evmonitor.application.tariff.AdminTariffResponses;
import com.evmonitor.application.user.EmpCatalog;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.infrastructure.persistence.JpaTariffProposalRepository;
import com.evmonitor.infrastructure.persistence.JpaTariffSourceRepository;
import com.evmonitor.infrastructure.persistence.TariffProposalEntity;
import com.evmonitor.infrastructure.persistence.TariffSourceEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Crawler-Teil des Admin-Tabs "Ladekarten": Quellen pflegen, Vorschläge entscheiden, Lauf starten. */
@Service
@RequiredArgsConstructor
public class AdminTariffCrawlService {

    private final JpaTariffSourceRepository sources;
    private final JpaTariffProposalRepository proposals;
    private final TariffProposalBook book;
    private final ChargingProviderTariffRepository tariffs;
    private final EmpCatalog empCatalog;

    @Transactional(readOnly = true)
    public AdminTariffCrawlResponses.Overview overview(boolean extractorAvailable, boolean enabled) {
        List<AdminTariffCrawlResponses.ProposalRow> open = proposals.findByStatusOrderByFirstSeenAtAsc(TariffProposalEntity.Status.PENDING).stream()
                .map(p -> AdminTariffCrawlResponses.ProposalRow.of(p, p.getTariffId() == null ? null
                        : tariffs.findById(p.getTariffId()).map(AdminTariffResponses.Row::of).orElse(null)))
                .toList();
        return new AdminTariffCrawlResponses.Overview(
                sources.findAllByOrderByEmpNameAsc().stream().map(AdminTariffCrawlResponses.SourceRow::of).toList(),
                open, extractorAvailable, enabled);
    }

    public AdminTariffCrawlResponses.Applied apply(UUID proposalId) {
        return new AdminTariffCrawlResponses.Applied(book.applyByAdmin(proposalId, LocalDateTime.now()));
    }

    public void reject(UUID proposalId) {
        book.rejectByAdmin(proposalId, LocalDateTime.now());
    }

    @Transactional
    public AdminTariffCrawlResponses.SourceRow create(AdminTariffCrawlResponses.SourceCommand cmd) {
        if (sources.existsByUrl(cmd.url().trim())) throw new ValidationException("Diese Quelle gibt es schon.");
        TariffSourceEntity s = new TariffSourceEntity();
        s.setId(UUID.randomUUID());
        return AdminTariffCrawlResponses.SourceRow.of(sources.save(apply(s, cmd)));
    }

    @Transactional
    public AdminTariffCrawlResponses.SourceRow update(UUID id, AdminTariffCrawlResponses.SourceCommand cmd) {
        TariffSourceEntity s = sources.findById(id).orElseThrow(() -> new NotFoundException("Quelle nicht gefunden."));
        String url = cmd.url().trim();
        if (!url.equals(s.getUrl()) && sources.existsByUrl(url)) throw new ValidationException("Diese Quelle gibt es schon.");
        if (!url.equals(s.getUrl())) {
            s.setContentHash(null);
            s.setApprovedHash(null);
        }
        return AdminTariffCrawlResponses.SourceRow.of(sources.save(apply(s, cmd)));
    }

    private TariffSourceEntity apply(TariffSourceEntity s, AdminTariffCrawlResponses.SourceCommand cmd) {
        s.setEmpName(empCatalog.resolve(cmd.empName().trim())
                .orElseThrow(() -> new ValidationException("Anbieter steht nicht im Katalog: " + cmd.empName().trim())));
        s.setUrl(cmd.url().trim());
        s.setMode(TariffSourceEntity.Mode.valueOf(cmd.mode()));
        s.setEnabled(cmd.enabled());
        return s;
    }
}
