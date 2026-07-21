package com.evmonitor.domain.xpeng;

import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link XpengExcelStreamingParser#canDecrypt(Path, String)}.
 *
 * Background: XPeng liefert die verschluesselte XLSX und das Passwort in getrennten Mails.
 * Kommt die XLSX vor dem Passwort an, darf der Poller sie NICHT mit einem veralteten Passwort
 * importieren - canDecrypt() ist der synchrone Vorab-Check, der genau das verhindert.
 */
class XpengCanDecryptTest {

    private static Path writePlainXlsx() throws Exception {
        Path f = Files.createTempFile("xpeng-plain-", ".xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("TELEMATICS_DATA");
            try (OutputStream os = Files.newOutputStream(f)) {
                wb.write(os);
            }
        }
        return f;
    }

    private static Path writeEncryptedXlsx(String password) throws Exception {
        byte[] plain;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("TELEMATICS_DATA");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            plain = bos.toByteArray();
        }
        Path f = Files.createTempFile("xpeng-enc-", ".xlsx");
        try (POIFSFileSystem fs = new POIFSFileSystem()) {
            EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile);
            Encryptor enc = info.getEncryptor();
            enc.confirmPassword(password);
            try (OutputStream os = enc.getDataStream(fs)) {
                os.write(plain);
            }
            try (OutputStream os = Files.newOutputStream(f)) {
                fs.writeFilesystem(os);
            }
        }
        return f;
    }

    @Test
    void plainXlsxIsAlwaysDecryptable() throws Exception {
        Path f = writePlainXlsx();
        try {
            assertTrue(XpengExcelStreamingParser.canDecrypt(f, null));
            assertTrue(XpengExcelStreamingParser.canDecrypt(f, "irgendwas"));
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void encryptedXlsxWithCorrectPasswordIsDecryptable() throws Exception {
        Path f = writeEncryptedXlsx("202607120137");
        try {
            assertTrue(XpengExcelStreamingParser.canDecrypt(f, "202607120137"));
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void encryptedXlsxWithWrongPasswordIsNotDecryptable() throws Exception {
        Path f = writeEncryptedXlsx("202607120137");
        try {
            // veraltetes Passwort aus vorheriger Runde
            assertFalse(XpengExcelStreamingParser.canDecrypt(f, "202606280137"));
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void encryptedXlsxWithoutPasswordIsNotDecryptable() throws Exception {
        Path f = writeEncryptedXlsx("202607120137");
        try {
            assertFalse(XpengExcelStreamingParser.canDecrypt(f, null));
            assertFalse(XpengExcelStreamingParser.canDecrypt(f, "  "));
        } finally {
            Files.deleteIfExists(f);
        }
    }
}
