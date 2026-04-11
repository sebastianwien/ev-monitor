package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.infrastructure.persistence.EvLogEntity;
import com.evmonitor.infrastructure.persistence.JpaEvLogRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaxExportService {

    public static final BigDecimal BMF_PAUSCHALE_2026 = new BigDecimal("0.3436");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final CarRepository carRepository;
    private final JpaEvLogRepository jpaEvLogRepository;

    public record TaxExportData(
            Car car,
            List<EvLogEntity> sessions,
            BigDecimal totalKwh,
            BigDecimal totalCostEur,
            BigDecimal tariffPerKwh,
            boolean isPauschale,
            LocalDateTime from,
            LocalDateTime to) {
    }

    public TaxExportData buildExportData(UUID carId, UUID userId, LocalDateTime from, LocalDateTime to,
            boolean usePauschale, BigDecimal customTariff) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }
        if (!car.isBusinessCar()) {
            throw new IllegalArgumentException("Car is not marked as a business car");
        }
        if (!usePauschale && (customTariff == null || customTariff.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalArgumentException("Custom tariff must be greater than 0");
        }

        List<EvLogEntity> sessions = jpaEvLogRepository.findHomeChargingSessionsForExport(carId, from, to);

        BigDecimal tariff = usePauschale ? BMF_PAUSCHALE_2026 : customTariff;
        BigDecimal totalKwh = sessions.stream()
                .map(EvLogEntity::getKwhCharged)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = totalKwh.multiply(tariff).setScale(2, RoundingMode.HALF_UP);

        return new TaxExportData(car, sessions, totalKwh, totalCost, tariff, usePauschale, from, to);
    }

    public byte[] generateCsv(TaxExportData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("Datum;Uhrzeit;kWh;Kosten (EUR);Kennzeichen;Datenquelle;Messtyp\n");

        for (EvLogEntity s : data.sessions()) {
            BigDecimal cost = s.getKwhCharged().multiply(data.tariffPerKwh()).setScale(2, RoundingMode.HALF_UP);
            sb.append(s.getLoggedAt().format(DATE_FMT)).append(";");
            sb.append(s.getLoggedAt().format(TIME_FMT)).append(";");
            sb.append(s.getKwhCharged().setScale(3, RoundingMode.HALF_UP)).append(";");
            sb.append(cost).append(";");
            sb.append(sanitizeCsvCell(nullSafe(data.car().getLicensePlate()))).append(";");
            sb.append(sanitizeCsvCell(s.getDataSource())).append(";");
            sb.append(s.getMeasurementType() != null ? s.getMeasurementType() : "").append("\n");
        }

        sb.append("GESAMT;;");
        sb.append(data.totalKwh().setScale(3, RoundingMode.HALF_UP)).append(";");
        sb.append(data.totalCostEur()).append(";;;\n");
        sb.append("\n");
        sb.append("Abrechnungsmodell;");
        sb.append(data.isPauschale()
                ? "BMF-Pauschale 2026: " + data.tariffPerKwh() + " EUR/kWh"
                : "Eigener Tarif: " + data.tariffPerKwh() + " EUR/kWh");
        sb.append("\n");

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] generatePdf(TaxExportData data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 54, 54);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY);

            // Header
            doc.add(new Paragraph("Nachweis Heimladekosten E-Dienstwagen", titleFont));
            doc.add(Chunk.NEWLINE);

            // Meta-Info
            PdfPTable meta = new PdfPTable(2);
            meta.setWidthPercentage(100);
            meta.setWidths(new float[]{1, 2});
            addMetaRow(meta, "Fahrzeug", data.car().getModel().getBrand().getDisplayString()
                    + " " + data.car().getModel().getDisplayName(), headerFont, bodyFont);
            addMetaRow(meta, "Kennzeichen", nullSafe(data.car().getLicensePlate()), headerFont, bodyFont);
            addMetaRow(meta, "Zeitraum",
                    data.from().format(DATE_FMT) + " - " + data.to().minusDays(1).format(DATE_FMT),
                    headerFont, bodyFont);
            addMetaRow(meta, "Abrechnungsmodell",
                    data.isPauschale()
                            ? "BMF-Pauschale 2026: " + data.tariffPerKwh() + " EUR/kWh"
                            : "Eigener Tarif: " + data.tariffPerKwh() + " EUR/kWh",
                    headerFont, bodyFont);
            doc.add(meta);
            doc.add(Chunk.NEWLINE);

            // Sessions table
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.5f, 1f, 1f, 1f, 1.5f, 2f});

            String[] headers = {"Datum", "Uhrzeit", "kWh", "Kosten (EUR)", "Kennzeichen", "Datenquelle"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new Color(230, 230, 230));
                cell.setPadding(4);
                table.addCell(cell);
            }

            for (EvLogEntity s : data.sessions()) {
                BigDecimal cost = s.getKwhCharged().multiply(data.tariffPerKwh()).setScale(2, RoundingMode.HALF_UP);
                addTableCell(table, s.getLoggedAt().format(DATE_FMT), bodyFont);
                addTableCell(table, s.getLoggedAt().format(TIME_FMT), bodyFont);
                addTableCell(table, s.getKwhCharged().setScale(3, RoundingMode.HALF_UP).toPlainString(), bodyFont);
                addTableCell(table, cost.toPlainString(), bodyFont);
                addTableCell(table, nullSafe(data.car().getLicensePlate()), bodyFont);
                addTableCell(table, formatSource(s.getDataSource()), bodyFont);
            }

            // Totals row
            PdfPCell totalLabel = new PdfPCell(new Phrase("Gesamt", headerFont));
            totalLabel.setColspan(2);
            totalLabel.setPadding(4);
            totalLabel.setBackgroundColor(new Color(245, 245, 245));
            table.addCell(totalLabel);
            addTableCell(table, data.totalKwh().setScale(3, RoundingMode.HALF_UP).toPlainString(), headerFont);
            addTableCell(table, data.totalCostEur().toPlainString(), headerFont);
            addTableCell(table, "", bodyFont);
            addTableCell(table, "", bodyFont);

            doc.add(table);
            doc.add(Chunk.NEWLINE);

            // Disclaimer
            String disclaimer = "Dieser Export wurde maschinell erstellt und dient als Nachweis-Unterstuetzung "
                    + "gemaess BMF-Schreiben vom 11.11.2025. Die steuerliche Korrektheit und Vollstaendigkeit "
                    + "liegt in der Verantwortung des Nutzers. Datenquelle \"Fahrzeug\" (AT_VEHICLE) basiert auf "
                    + "fahrzeuginternen Messwerten; fuer eichrechtskonforme Nachweise empfiehlt sich eine "
                    + "MID-zertifizierte Wallbox. Erstellt mit EV Monitor (ev-monitor.net).";
            doc.add(new Paragraph(disclaimer, smallFont));

        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    private void addMetaRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(3);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(3);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addTableCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        table.addCell(cell);
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    /** Prevents CSV/formula injection by prefixing dangerous characters with a single quote. */
    private String sanitizeCsvCell(String value) {
        if (value == null || value.isEmpty()) return "";
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }

    private String formatSource(String dataSource) {
        if (dataSource == null) return "";
        return switch (dataSource) {
            case "WALLBOX_GOE" -> "Wallbox (go-e)";
            case "WALLBOX_OCPP" -> "Wallbox (OCPP)";
            case "SMARTCAR_LIVE" -> "Fahrzeug (Smartcar)";
            case "TESLA_LIVE", "TESLA_FLEET_IMPORT" -> "Fahrzeug (Tesla)";
            case "API_UPLOAD" -> "Import (API/CSV)";
            case "USER_LOGGED" -> "Manuell erfasst";
            default -> dataSource;
        };
    }
}
