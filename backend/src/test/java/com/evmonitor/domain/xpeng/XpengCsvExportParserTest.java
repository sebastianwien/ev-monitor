package com.evmonitor.domain.xpeng;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parser fuer das neue XPeng-EU-Data-Act-Format: ZIP mit 3 CSVs (operation /
 * power_energy / status), gejoint ueber {@code timer} (Epoch-Sekunden). Die 3
 * Cluster tragen unterschiedliche Signale - erst zusammen ergeben sie eine
 * vollstaendige Telematik-Zeile.
 */
class XpengCsvExportParserTest {

    @TempDir
    Path tmp;

    private static final String OP_HEADER =
            "vin,vmodel,timer,ds,esp_vehspd,ldcu_currentgearlev,cdcu_totalodometer";
    private static final String PE_HEADER =
            "vin,vmodel,timer,ds,bms_battvolt,bms_battcurr,ldcu_chrgpwr,ldcu_bms_soc_disp,ldcu_dstbatdisp_dynamic";
    private static final String ST_HEADER =
            "vin,vmodel,timer,ds,ldcu_tpmsprfl";

    @Test
    void jointDreiCsvUeberTimerInklLueckeUndSentinel() throws Exception {
        // operation: 3 Zeitpunkte
        String op = "﻿" + OP_HEADER + "\n"
                + "L1NTEST,F57a,1000,20260901,42.5,1,12345.0\n"
                + "L1NTEST,F57a,1001,20260901,0.0,4,12345.0\n"
                + "L1NTEST,F57a,1002,20260901,0.0,4,12346.0\n";
        // power_energy: t=1001 fehlt (Luecke), t=1002 hat Sentinel-Ladeleistung
        String pe = PE_HEADER + "\n"
                + "L1NTEST,F57a,1000,20260901,360.0,-15.0,0.0,80.0,431.0\n"
                + "L1NTEST,F57a,1002,20260901,361.0,5.0,1638.3,79.0,430.0\n";
        String st = ST_HEADER + "\n"
                + "L1NTEST,F57a,1000,20260901,261.25\n"
                + "L1NTEST,F57a,1001,20260901,261.0\n"
                + "L1NTEST,F57a,1002,20260901,260.75\n";

        Path zip = writeZip(Map.of(
                "DA_x_dwd_opp_gdpr_veh_driving_operation_di.csv", op,
                "DA_x_dwd_opp_gdpr_veh_driving_power_energy_di.csv", pe,
                "DA_x_dwd_opp_gdpr_veh_driving_status_di.csv", st));

        List<XpengTelematicsRow> rows = new ArrayList<>();
        XpengCsvExportParser.ParseResult result =
                new XpengCsvExportParser().parse(zip, rows::add);

        assertEquals(3, result.rowsProcessed());
        assertEquals("L1NTEST", result.vehicleInfo().vin(), "VIN trotz BOM im Header");
        assertEquals("F57a", result.vehicleInfo().model());

        // aufsteigend nach timer sortiert
        assertEquals(LocalDateTime.ofEpochSecond(1000, 0, ZoneOffset.UTC), rows.get(0).timer());
        assertEquals(LocalDateTime.ofEpochSecond(1001, 0, ZoneOffset.UTC), rows.get(1).timer());
        assertEquals(LocalDateTime.ofEpochSecond(1002, 0, ZoneOffset.UTC), rows.get(2).timer());

        // t=1000: beide Cluster -> vollstaendig
        assertEquals(0, new BigDecimal("42.5").compareTo(rows.get(0).vehSpeedKmh()));
        assertEquals(1, rows.get(0).gearLev());
        assertEquals(0, new BigDecimal("80.0").compareTo(rows.get(0).socDisplay()));
        assertEquals(0, new BigDecimal("431.0").compareTo(rows.get(0).extra(XpengExtraKeys.BMS_RANGE_KM)));

        // t=1001: nur operation -> SoC/Ladeleistung fehlen (null), Gear da
        assertEquals(4, rows.get(1).gearLev());
        assertNull(rows.get(1).socDisplay(), "power_energy-Luecke -> SoC null");
        assertNull(rows.get(1).chargePowerKw());

        // t=1002: Sentinel-Ladeleistung 1638.3 -> vom Record entschaerft
        assertEquals(0, new BigDecimal("79.0").compareTo(rows.get(2).socDisplay()));
        assertNull(rows.get(2).chargePowerKw(), "unplausible Ladeleistung -> null");
    }

    @Test
    void meldetSchemaDriftWennPflichtsignaleFehlen() throws Exception {
        // nur operation-Cluster: SoC + Ladeleistung fehlen komplett
        String op = OP_HEADER + "\n"
                + "L1NTEST,F57a,1000,20260901,42.5,1,12345.0\n";
        Path zip = writeZip(Map.of("only_operation_di.csv", op));

        XpengParseException ex = assertThrows(XpengParseException.class,
                () -> new XpengCsvExportParser().parse(zip, r -> {}));
        assertTrue(ex.getMessage().toLowerCase().contains("soc")
                        || ex.getMessage().toLowerCase().contains("charge")
                        || ex.getMessage().toLowerCase().contains("pflicht"),
                "Meldung soll die fehlenden Pflichtsignale nennen: " + ex.getMessage());
    }

    @Test
    void peekVinLiestVinOhneVollparse() throws Exception {
        String op = "﻿" + OP_HEADER + "\n"
                + "L1NPEEK,F57a,1000,20260901,0.0,4,12345.0\n";
        Path zip = writeZip(Map.of("driving_operation_di.csv", op));

        assertEquals("L1NPEEK", XpengCsvExportParser.peekVin(zip));
    }

    @Test
    void peekVinNullWennKeineVinSpalte() throws Exception {
        String noVin = "timer,ds,esp_vehspd\n1000,20260901,0.0\n";
        Path zip = writeZip(Map.of("x_di.csv", noVin));

        assertNull(XpengCsvExportParser.peekVin(zip));
    }

    // -- helper: baut ein ZIP mit den gegebenen Entry-Name->Inhalt Paaren --
    private Path writeZip(Map<String, String> entries) throws Exception {
        Path zip = tmp.resolve("export.zip");
        try (OutputStream os = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            // stabile Reihenfolge fuer den Test
            Map<String, String> ordered = new LinkedHashMap<>(entries);
            for (Map.Entry<String, String> e : ordered.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return zip;
    }
}
