package com.evmonitor.domain.xpeng;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fehlerdiagnose des Parsers: die Meldung muss unterscheiden zwischen
 * "kein Sheet mit den Pflichtspalten" (Schema-Drift bei XPeng) und
 * "Sheet erkannt, aber keine Zeile verwertbar" (z.B. neues Zeitstempel-Format).
 */
class XpengExcelStreamingParserTest {

    private static final List<String> TELEMATICS_HEADERS = List.of(
            "timer(GMT+1)", "ESP_VehSpd", "LDCU_CurrentGearLev",
            "CDCU_TotalOdometer", "LDCU_BMS_SOC_Disp", "LDCU_ChrgPwr");

    @TempDir
    Path tmp;

    @Test
    void meldetSchemaDriftWennKeinSheetDiePflichtspaltenHat() throws Exception {
        Path file = writeWorkbook("SOME_OTHER_SHEET",
                List.of("timer", "irgendwas", "noch_was"),
                List.of(List.of("2026-07-20 10:00:00", "1", "2")));

        XpengParseException ex = assertThrows(XpengParseException.class,
                () -> new XpengExcelStreamingParser().parse(file, null, row -> {}));

        assertTrue(ex.getMessage().contains("Kein TELEMATICS-Sheet gefunden"),
                "unerwartete Meldung: " + ex.getMessage());
    }

    @Test
    void meldetUnlesbareZeilenWennSheetErkanntAberKeineZeileVerwertbarIst() throws Exception {
        Path file = writeWorkbook("TELEMATICS_DATA", TELEMATICS_HEADERS, List.of(
                List.of("20.07.2026 10:00", "50", "4", "12345", "80", "0"),
                List.of("20.07.2026 10:00:05", "51", "4", "12345", "80", "0"),
                List.of("20.07.2026 10:00:10", "52", "4", "12346", "79", "0")));

        XpengParseException ex = assertThrows(XpengParseException.class,
                () -> new XpengExcelStreamingParser().parse(file, null, row -> {}));

        String msg = ex.getMessage();
        assertFalse(msg.contains("Kein TELEMATICS-Sheet gefunden"),
                "Sheet wurde erkannt - die Schema-Drift-Meldung fuehrt in die Irre: " + msg);
        assertTrue(msg.contains("TELEMATICS_DATA"), "Sheet-Name fehlt: " + msg);
        assertTrue(msg.contains("3"), "Anzahl der Datenzeilen fehlt: " + msg);
        assertTrue(msg.toLowerCase().contains("zeitstempel"),
                "Hinweis auf das Zeitstempel-Format fehlt: " + msg);
    }

    @Test
    void parstGueltigesSheet() throws Exception {
        Path file = writeWorkbook("TELEMATICS_DATA", TELEMATICS_HEADERS, List.of(
                List.of("2026-07-20 10:00:00", "50", "4", "12345", "80", "0"),
                List.of("2026-07-20 10:00:05", "51", "4", "12346", "79", "0")));

        List<XpengTelematicsRow> rows = new ArrayList<>();
        XpengExcelStreamingParser.ParseResult result =
                new XpengExcelStreamingParser().parse(file, null, rows::add);

        assertEquals(2, result.rowsProcessed());
        assertEquals(2, rows.size());
        assertEquals("LMTXPENGTEST00001", result.vehicleInfo().vin());
    }

    /** Schreibt ein Workbook mit BASIC_VEHICLE_DATA + einem Daten-Sheet. */
    private Path writeWorkbook(String sheetName, List<String> headers, List<List<String>> dataRows)
            throws Exception {
        Path file = tmp.resolve("xpeng.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet basic = wb.createSheet("BASIC_VEHICLE_DATA");
            writeRow(basic, 0, List.of("VIN", "Model", "Color", "Production Date", "OTA Version"));
            writeRow(basic, 1, List.of("LMTXPENGTEST00001", "G6", "Silber", "2025-01-01", "1.0"));

            Sheet data = wb.createSheet(sheetName);
            writeRow(data, 0, headers);
            for (int i = 0; i < dataRows.size(); i++) {
                writeRow(data, i + 1, dataRows.get(i));
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }
        return file;
    }

    private static void writeRow(Sheet sheet, int rowNum, List<String> values) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < values.size(); i++) {
            row.createCell(i).setCellValue(values.get(i));
        }
    }
}
