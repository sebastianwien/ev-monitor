package com.evmonitor.domain.xpeng;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Streaming parser for XPeng EU-Data-Act XLSX exports.
 *
 * Reads:
 *   - BASIC_VEHICLE_DATA → {@link XpengVehicleInfo} (VIN, model, etc.)
 *   - TELEMATICS_DATA    → row-by-row {@link XpengTelematicsRow} via callback
 *
 * Memory profile: SAX-streaming, ~50 MB working set for a 75 MB unencrypted xlsx.
 *
 * Security:
 *   - ZipSecureFile limits zip-bomb factors
 *   - POI's XMLHelper disables XXE / DTD by default
 *   - Encrypted files are decrypted to a fresh tempfile before parsing
 */
@Slf4j
public class XpengExcelStreamingParser {

    private static final long MAX_DECRYPTED_BYTES = 200L * 1024 * 1024; // 200 MB
    private static final int MAX_TELEMATICS_ROWS = 2_000_000;

    static {
        // Bound zip-bomb expansion. Apply once at class load.
        ZipSecureFile.setMaxFileCount(2_000);
        ZipSecureFile.setMaxTextSize(500_000_000L);
        ZipSecureFile.setMinInflateRatio(0.001);
    }

    private static final DateTimeFormatter[] TIMER_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    public record ParseResult(XpengVehicleInfo vehicleInfo, long rowsProcessed) {}

    /**
     * Parse the given xlsx file. If a non-null password is supplied, decrypt first.
     * The {@code rowHandler} is invoked once per TELEMATICS_DATA row in document order.
     *
     * @throws XpengParseException on validation failures (missing sheet, bad password, oversized)
     */
    public ParseResult parse(Path xlsxPath, String password, Consumer<XpengTelematicsRow> rowHandler)
            throws Exception {
        Path workingFile = xlsxPath;
        Path decryptedFile = null;
        try {
            if (password != null && !password.isBlank()) {
                decryptedFile = decryptToTempfile(xlsxPath, password);
                workingFile = decryptedFile;
            }

            try (OPCPackage pkg = OPCPackage.open(workingFile.toFile(), PackageAccess.READ)) {
                XSSFReader reader = new XSSFReader(pkg);
                org.apache.poi.xssf.model.SharedStrings sst = reader.getSharedStringsTable();
                StylesTable styles = reader.getStylesTable();

                XpengVehicleInfo info = readVehicleInfo(reader, sst, styles);

                XSSFReader.SheetIterator sheetIter = (XSSFReader.SheetIterator) reader.getSheetsData();
                long rowsProcessed = 0;
                boolean foundTelematics = false;
                while (sheetIter.hasNext()) {
                    InputStream sheetStream = sheetIter.next();
                    String sheetName = sheetIter.getSheetName();
                    try {
                        if ("TELEMATICS_DATA".equals(sheetName)) {
                            foundTelematics = true;
                            rowsProcessed = readTelematicsSheet(sheetStream, sst, styles, rowHandler);
                        }
                    } finally {
                        sheetStream.close();
                    }
                }
                if (!foundTelematics) {
                    throw new XpengParseException("TELEMATICS_DATA sheet not found - is this an XPeng export?");
                }
                return new ParseResult(info, rowsProcessed);
            }
        } finally {
            if (decryptedFile != null) {
                try { Files.deleteIfExists(decryptedFile); } catch (Exception ignored) {}
            }
        }
    }

    private Path decryptToTempfile(Path encrypted, String password) throws Exception {
        Path out = Files.createTempFile("xpeng-decrypted-", ".xlsx");
        try (POIFSFileSystem fs = new POIFSFileSystem(encrypted.toFile(), true)) {
            EncryptionInfo info = new EncryptionInfo(fs);
            Decryptor decryptor = Decryptor.getInstance(info);
            if (!decryptor.verifyPassword(password)) {
                Files.deleteIfExists(out);
                throw new XpengParseException("Wrong password for encrypted xlsx");
            }
            try (InputStream in = decryptor.getDataStream(fs)) {
                long copied = Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                if (copied > MAX_DECRYPTED_BYTES) {
                    Files.deleteIfExists(out);
                    throw new XpengParseException("Decrypted file exceeds size limit (" + copied + " bytes)");
                }
            }
        }
        return out;
    }

    /**
     * Reads BASIC_VEHICLE_DATA. Small sheet (2 rows), DOM read is acceptable and simpler.
     */
    private XpengVehicleInfo readVehicleInfo(XSSFReader reader,
                                              org.apache.poi.xssf.model.SharedStrings sst,
                                              StylesTable styles) throws Exception {
        XSSFReader.SheetIterator it = (XSSFReader.SheetIterator) reader.getSheetsData();
        while (it.hasNext()) {
            InputStream stream = it.next();
            String sheetName = it.getSheetName();
            try {
                if ("BASIC_VEHICLE_DATA".equals(sheetName)) {
                    return readVehicleInfoSheet(stream, sst, styles);
                }
            } finally {
                stream.close();
            }
        }
        throw new XpengParseException("BASIC_VEHICLE_DATA sheet not found");
    }

    private XpengVehicleInfo readVehicleInfoSheet(InputStream stream,
                                                   org.apache.poi.xssf.model.SharedStrings sst,
                                                   StylesTable styles) throws Exception {
        // SAX-parse a 2-row sheet: header row + data row.
        Map<String, String> dataByHeader = new HashMap<>();
        VehicleInfoHandler handler = new VehicleInfoHandler(dataByHeader);
        XMLReader xmlReader = XMLHelper.newXMLReader();
        xmlReader.setContentHandler(new XSSFSheetXMLHandler(styles, sst, handler, false));
        xmlReader.parse(new InputSource(stream));
        return new XpengVehicleInfo(
                dataByHeader.get("VIN"),
                dataByHeader.get("Model"),
                dataByHeader.get("Color"),
                dataByHeader.get("Production Date"),
                dataByHeader.get("OTA Version"));
    }

    private long readTelematicsSheet(InputStream stream,
                                      org.apache.poi.xssf.model.SharedStrings sst,
                                      StylesTable styles,
                                      Consumer<XpengTelematicsRow> rowHandler) throws Exception {
        TelematicsHandler handler = new TelematicsHandler(rowHandler);
        XMLReader xmlReader = XMLHelper.newXMLReader();
        xmlReader.setContentHandler(new XSSFSheetXMLHandler(styles, sst, handler, false));
        xmlReader.parse(new InputSource(stream));
        return handler.rowsEmitted;
    }

    // -- Handlers --

    private static class VehicleInfoHandler implements SheetContentsHandler {
        private final Map<String, String> dataByHeader;
        private final Map<Integer, String> headers = new HashMap<>();
        private int currentRow = -1;

        VehicleInfoHandler(Map<String, String> dataByHeader) {
            this.dataByHeader = dataByHeader;
        }

        @Override public void startRow(int rowNum) { currentRow = rowNum; }
        @Override public void endRow(int rowNum) {}
        @Override public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int col = columnIndex(cellReference);
            if (currentRow == 0) {
                headers.put(col, formattedValue);
            } else if (currentRow == 1) {
                dataByHeader.put(headers.get(col), formattedValue);
            }
        }
    }

    private static class TelematicsHandler implements SheetContentsHandler {
        private final Consumer<XpengTelematicsRow> rowHandler;
        private final Map<Integer, String> headers = new HashMap<>();
        private Map<Integer, String> currentRow = new HashMap<>();
        private int currentRowNum = -1;
        long rowsEmitted = 0;

        TelematicsHandler(Consumer<XpengTelematicsRow> rowHandler) {
            this.rowHandler = rowHandler;
        }

        @Override public void startRow(int rowNum) {
            currentRowNum = rowNum;
            currentRow.clear();
        }

        @Override public void endRow(int rowNum) {
            if (rowNum == 0) return; // header
            if (rowsEmitted >= MAX_TELEMATICS_ROWS) {
                throw new RuntimeException(
                        new XpengParseException("Too many telematics rows (" + rowsEmitted + ")"));
            }
            XpengTelematicsRow row = mapRow();
            if (row != null) {
                rowHandler.accept(row);
                rowsEmitted++;
            }
        }

        @Override public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int col = columnIndex(cellReference);
            if (currentRowNum == 0) {
                headers.put(col, formattedValue);
            } else {
                currentRow.put(col, formattedValue);
            }
        }

        private XpengTelematicsRow mapRow() {
            LocalDateTime timer = parseTimer(byHeader("timer"));
            if (timer == null) return null;
            java.util.Map<String, BigDecimal> extras = new java.util.HashMap<>();
            putIfNotNull(extras, XpengExtraKeys.CELL_TEMP_MAX_C, parseDecimal(byHeader("bms_celltempmaxnum_gb")));
            putIfNotNull(extras, XpengExtraKeys.CELL_TEMP_MIN_C, parseDecimal(byHeader("bms_celltempminnum_gb")));
            putIfNotNull(extras, XpengExtraKeys.LONG_ACCEL_G,    parseDecimal(byHeader("esp_vehlongaccel")));
            putIfNotNull(extras, XpengExtraKeys.LAT_ACCEL_G,     parseDecimal(byHeader("esp_vehlateralaccel")));
            putIfNotNull(extras, XpengExtraKeys.ACCEL_PEDAL_PCT, parseDecimal(byHeader("ldcu_accpedalsig")));
            putIfNotNull(extras, XpengExtraKeys.FRONT_TORQUE_NM, parseDecimal(byHeader("ipuf_acttorq")));
            putIfNotNull(extras, XpengExtraKeys.REAR_TORQUE_NM,  parseDecimal(byHeader("ipur_acttorq")));
            putIfNotNull(extras, XpengExtraKeys.FRONT_RPM,       parseDecimal(byHeader("ipuf_actrotspd")));
            putIfNotNull(extras, XpengExtraKeys.REAR_RPM,        parseDecimal(byHeader("ipur_actrotspd")));
            putIfNotNull(extras, XpengExtraKeys.BMS_RANGE_KM,    parseDecimal(byHeader("ldcu_dstbatdisp_dynamic")));
            return new XpengTelematicsRow(
                    timer,
                    parseDecimal(byHeader("esp_vehspd")),
                    parseInt(byHeader("ldcu_currentgearlev")),
                    parseDecimal(byHeader("cdcu_totalodometer")),
                    parseDecimal(byHeader("ldcu_bms_soc_disp")),
                    parseDecimal(byHeader("bms_battvolt")),
                    parseDecimal(byHeader("bms_battcurr")),
                    parseDecimal(byHeader("ldcu_chrgpwr")),
                    parseDecimal(byHeader("bms_batttempmax_gb")),
                    parseDecimal(byHeader("bms_batttempmin_gb")),
                    extras);
        }

        private static void putIfNotNull(java.util.Map<String, BigDecimal> map, String key, BigDecimal v) {
            if (v != null) map.put(key, v);
        }

        private String byHeader(String name) {
            for (Map.Entry<Integer, String> e : headers.entrySet()) {
                if (name.equalsIgnoreCase(e.getValue())) {
                    return currentRow.get(e.getKey());
                }
            }
            return null;
        }
    }

    private static int columnIndex(String cellReference) {
        int col = 0;
        for (int i = 0; i < cellReference.length(); i++) {
            char c = cellReference.charAt(i);
            if (Character.isLetter(c)) {
                col = col * 26 + (Character.toUpperCase(c) - 'A' + 1);
            } else {
                break;
            }
        }
        return col - 1;
    }

    private static LocalDateTime parseTimer(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        for (DateTimeFormatter fmt : TIMER_FORMATS) {
            try {
                return LocalDateTime.parse(s, fmt);
            } catch (Exception ignored) {}
        }
        // POI may have already rendered Excel-dates as 'M/d/yy h:mm' depending on locale
        try {
            return LocalDateTime.parse(s.replace('/', '-').replace('T', ' '),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return (int) Double.parseDouble(raw.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
