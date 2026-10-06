package com.evmonitor.infrastructure.scheduling;

import com.evmonitor.application.recap.MonthlyRecap;
import com.evmonitor.application.recap.MonthlyRecapService;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.github.GitHubIssueService;
import com.evmonitor.infrastructure.persistence.MonthlyRecapQueryRepository;
import com.evmonitor.infrastructure.persistence.MonthlyRecapQueryRepository.RecapCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Verschickt am 2. des Monats den Rückblick auf den Vormonat, am 3. holt derselbe Lauf nach,
 * was am 2. gescheitert ist.
 *
 * <p>Jeder Versand legt vorher einen Marker in {@code monthly_recap_sent} an. Damit mailt auch
 * dann niemand doppelt, wenn bei Blue/Green kurz zwei Instanzen denselben Cron auslösen. Scheitert
 * der Versand, wird der Marker wieder freigegeben.
 *
 * <p>Aus, solange {@code app.monthly-recap.enabled} nicht true ist: Deploy und Start sind getrennt.
 */
@Component
@Slf4j
public class MonthlyRecapJob {

    public record Report(int candidates, int sent, int skipped, int failed) {}

    private final MonthlyRecapQueryRepository repository;
    private final MonthlyRecapService recapService;
    private final EmailService emailService;
    private final GitHubIssueService gitHubIssueService;

    @Value("${app.monthly-recap.enabled:false}")
    private boolean enabled;

    public MonthlyRecapJob(MonthlyRecapQueryRepository repository, MonthlyRecapService recapService,
                           EmailService emailService, GitHubIssueService gitHubIssueService) {
        this.repository = repository;
        this.recapService = recapService;
        this.emailService = emailService;
        this.gitHubIssueService = gitHubIssueService;
    }

    @Scheduled(cron = "${app.monthly-recap.cron:0 0 10 2,3 * *}", zone = "Europe/Berlin")
    public void sendMonthlyRecaps() {
        if (!enabled) {
            return;
        }
        run(YearMonth.now(ZoneId.of("Europe/Berlin")).minusMonths(1));
    }

    public Report run(YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        var candidates = repository.findCandidates(monthStart);
        log.info("Monthly recap {}: {} candidate(s)", month, candidates.size());

        int sent = 0;
        int skipped = 0;
        int failed = 0;
        for (RecapCandidate candidate : candidates) {
            if (!repository.claim(candidate.userId(), monthStart, candidate.carId())) {
                continue;
            }
            try {
                Optional<MonthlyRecap> recap = recapService.build(candidate, month);
                if (recap.isEmpty()) {
                    repository.release(candidate.userId(), monthStart);
                    skipped++;
                    continue;
                }
                emailService.sendMonthlyRecapEmail(recap.get());
                sent++;
            } catch (Exception e) {
                repository.release(candidate.userId(), monthStart);
                failed++;
                log.error("Monthly recap send failed for user {}", candidate.userId(), e);
            }
        }

        Report report = new Report(candidates.size(), sent, skipped, failed);
        log.info("Monthly recap {} report: {}", month, report);
        if (failed > 0) {
            gitHubIssueService.createIssue(
                    "monthly-recap-error-" + month,
                    "🚨 [EV Monitor] Monatsrückblick: " + failed + " Versand(e) fehlgeschlagen",
                    "## Versand-Fehler\n\nMonat: `%s`\n\n%d von %d Mails fehlgeschlagen (Details im Log). Der Lauf am 3. versucht sie erneut."
                            .formatted(month, failed, candidates.size())
            );
        }
        return report;
    }
}
