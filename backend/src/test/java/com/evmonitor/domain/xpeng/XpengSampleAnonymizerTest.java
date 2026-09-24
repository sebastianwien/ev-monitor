package com.evmonitor.domain.xpeng;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class XpengSampleAnonymizerTest {

    private static final String VIN = "L1NSPEAX8RA012345";
    private static final String ID = "DA20260901075053DDD4F4CE";
    private static final String OP = "vin,vmodel,timer,ds,esp_vehspd,ldcu_currentgearlev,cdcu_totalodometer";
    private static final String PE = "vin,vmodel,timer,ds,bms_battvolt,bms_battcurr,ldcu_chrgpwr,ldcu_bms_soc_disp,ldcu_dstbatdisp_dynamic";
    private static final String ST = "vin,vmodel,timer,ds,ldcu_tpmsprfl";

    @TempDir
    Path tmp;

    private final XpengSampleAnonymizer anonymizer = new XpengSampleAnonymizer(10_000_000);

    @Test
    void replacesTheVinInEveryCsv_andKeepsEveryOtherColumn() throws Exception {
        Path zip = writeZip(export());

        Map<String, String> out = unzip(anonymizer.anonymize(zip, ID + ".zip").orElseThrow().zip());

        assertThat(String.join("\n", out.values())).doesNotContain(VIN);
        for (String csv : out.values()) {
            String[] lines = csv.split("\n");
            for (int i = 1; i < lines.length; i++) {
                String[] cols = lines[i].split(",", -1);
                assertThat(cols[0]).startsWith("L1N").hasSize(17).isNotEqualTo(VIN);
            }
        }
        String op = out.get("DA2026090107505300000000_dwd_opp_gdpr_veh_driving_operation_di.csv");
        assertThat(op).startsWith("﻿" + OP).contains(",F57a,1001,20260901,0.0,4,12345.0");
    }

    @Test
    void replacesTheRequestIdInFileNames_andKeepsThePartFiles() throws Exception {
        XpengSampleAnonymizer.Result r = anonymizer.anonymize(writeZip(export()), ID + ".zip").orElseThrow();

        assertThat(r.fileName()).isEqualTo("DA2026090107505300000000.zip");
        assertThat(unzip(r.zip()).keySet()).contains(
                "DA2026090107505300000000_dwd_opp_gdpr_veh_driving_operation_di.csv",
                "DA2026090107505300000000_dwd_opp_gdpr_veh_driving_operation_di_part1.csv");
    }

    @Test
    void parserReadsTheSameRowsBeforeAndAfter() throws Exception {
        Path original = writeZip(export());
        Path anonymized = tmp.resolve("anon.zip");
        Files.write(anonymized, anonymizer.anonymize(original, ID + ".zip").orElseThrow().zip());

        assertThat(rows(anonymized)).isNotEmpty().isEqualTo(rows(original));
    }

    @Test
    void skipsUploadsThatAreNoCsvExport() throws Exception {
        Path xlsx = writeZip(Map.of("[Content_Types].xml", "<Types/>", "xl/workbook.xml", "<workbook/>"));
        Path garbage = tmp.resolve("x.bin");
        Files.write(garbage, "not a zip".getBytes(StandardCharsets.UTF_8));

        assertThat(anonymizer.anonymize(xlsx, "export.xlsx")).isEmpty();
        assertThat(anonymizer.anonymize(garbage, "x.zip")).isEmpty();
    }

    @Test
    void givesUpWhenTheCopyWouldExceedTheLimit() throws Exception {
        Optional<XpengSampleAnonymizer.Result> r = new XpengSampleAnonymizer(100).anonymize(writeZip(export()), ID + ".zip");

        assertThat(r).isEmpty();
    }

    private Map<String, String> export() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(ID + "_dwd_opp_gdpr_veh_driving_operation_di.csv", "﻿" + OP + "\n"
                + VIN + ",F57a,1000,20260901,42.5,1,12345.0\n"
                + VIN + ",F57a,1001,20260901,0.0,4,12345.0\n");
        m.put(ID + "_dwd_opp_gdpr_veh_driving_operation_di_part1.csv", OP + "\n"
                + VIN + ",F57a,1002,20260901,0.0,4,12346.0\n");
        m.put(ID + "_dwd_opp_gdpr_veh_driving_power_energy_di.csv", PE + "\n"
                + VIN + ",F57a,1000,20260901,360.0,-15.0,0.0,80.0,431.0\n"
                + VIN + ",F57a,1001,20260901,361.0,5.0,7.2,79.0,430.0\n"
                + VIN + ",F57a,1002,20260901,361.0,5.0,7.2,79.0,430.0\n");
        m.put(ID + "_dwd_opp_gdpr_veh_driving_status_di.csv", ST + "\n"
                + VIN + ",F57a,1000,20260901,261.25\n"
                + VIN + ",F57a,1001,20260901,261.0\n"
                + VIN + ",F57a,1002,20260901,260.75\n");
        return m;
    }

    private List<XpengTelematicsRow> rows(Path zip) throws Exception {
        List<XpengTelematicsRow> rows = new ArrayList<>();
        new XpengCsvExportParser().parse(zip, rows::add);
        return rows;
    }

    private Path writeZip(Map<String, String> entries) throws IOException {
        Path p = tmp.resolve("in-" + System.nanoTime() + ".zip");
        try (OutputStream os = Files.newOutputStream(p); ZipOutputStream zos = new ZipOutputStream(os)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return p;
    }

    private static Map<String, String> unzip(byte[] zip) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) m.put(e.getName(), new String(z.readAllBytes(), StandardCharsets.UTF_8));
        }
        return m;
    }
}
