package com.evmonitor.infrastructure.web;

import com.evmonitor.application.TaxExportService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/api/tax-export")
public class TaxExportController {

    private final TaxExportService taxExportService;

    public TaxExportController(TaxExportService taxExportService) {
        this.taxExportService = taxExportService;
    }

    @GetMapping("/preview")
    public ResponseEntity<TaxExportPreviewResponse> preview(
            @RequestParam UUID carId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "true") boolean usePauschale,
            @RequestParam(required = false) BigDecimal customTariff,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        TaxExportService.TaxExportData data = taxExportService.buildExportData(
                carId, principal.getUser().getId(),
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                usePauschale, customTariff);

        return ResponseEntity.ok(new TaxExportPreviewResponse(
                data.sessions().size(),
                data.totalKwh(),
                data.totalCostEur(),
                data.tariffPerKwh(),
                data.isPauschale()));
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> downloadCsv(
            @RequestParam UUID carId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "true") boolean usePauschale,
            @RequestParam(required = false) BigDecimal customTariff,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        TaxExportService.TaxExportData data = taxExportService.buildExportData(
                carId, principal.getUser().getId(),
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                usePauschale, customTariff);

        byte[] csv = taxExportService.generateCsv(data);
        String filename = buildFilename(data.car().getLicensePlate(), from, to, "csv");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(csv);
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestParam UUID carId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "true") boolean usePauschale,
            @RequestParam(required = false) BigDecimal customTariff,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        TaxExportService.TaxExportData data = taxExportService.buildExportData(
                carId, principal.getUser().getId(),
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                usePauschale, customTariff);

        byte[] pdf = taxExportService.generatePdf(data);
        String filename = buildFilename(data.car().getLicensePlate(), from, to, "pdf");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }

    private String buildFilename(String licensePlate, LocalDate from, LocalDate to, String ext) {
        String plate = licensePlate != null ? licensePlate.replaceAll("[^A-Za-z0-9]", "-") : "fahrzeug";
        return "heimladen-nachweis-" + plate + "-"
                + from.format(DateTimeFormatter.ofPattern("yyyy-MM")) + "." + ext;
    }

    public record TaxExportPreviewResponse(
            int sessionCount,
            BigDecimal totalKwh,
            BigDecimal totalCostEur,
            BigDecimal tariffPerKwh,
            boolean isPauschale) {
    }
}
