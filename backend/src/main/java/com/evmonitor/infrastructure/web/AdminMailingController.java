package com.evmonitor.infrastructure.web;

import com.evmonitor.application.mail.MailSendLog.RecentSend;
import com.evmonitor.application.mail.MailType;
import com.evmonitor.application.mail.MailingOverviewService;
import com.evmonitor.application.mail.MailingOverviewService.MailingRow;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.infrastructure.email.MailPreviewRenderer;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin-Tab "Mailings": Katalog, Versand, Plausible-Zahlen, Vorschau mit Beispieldaten. */
@RestController
@RequestMapping("/api/admin/mailings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMailingController {

    private final MailingOverviewService service;

    public AdminMailingController(MailingOverviewService service) {
        this.service = service;
    }

    public record PreviewDto(String html) {}

    @GetMapping
    public List<MailingRow> overview(@RequestParam(defaultValue = "30") int days) {
        return service.overview(days);
    }

    @GetMapping("/{key}/recent")
    public List<RecentSend> recent(@PathVariable String key) {
        return service.recent(type(key));
    }

    @GetMapping("/{key}/preview")
    public PreviewDto preview(@PathVariable String key) {
        return new PreviewDto(MailPreviewRenderer.render(type(key)));
    }

    private static MailType type(String key) {
        return MailType.fromKey(key).orElseThrow(() -> new NotFoundException("Mailing nicht gefunden."));
    }
}
