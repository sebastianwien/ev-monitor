package com.evmonitor.infrastructure.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tokens und kuenftig Passwoerter liegen verschluesselt in der DB. Neue Werte werden mit
 * AES-256-GCM geschrieben (zufaellige IV, Integritaetsschutz); Altbestand im ECB-Format
 * muss weiter lesbar bleiben, bis er beim naechsten Schreiben migriert ist.
 */
class AesEncryptionTest {

    private static final String KEY = "test-encryption-key-32chars!!!!!";

    @Test
    void roundTrip() {
        String cipher = AesEncryption.encrypt("geheim äöü", KEY);
        assertEquals("geheim äöü", AesEncryption.decrypt(cipher, KEY));
    }

    @Test
    void newCiphertextsAreVersionedGcm() {
        assertTrue(AesEncryption.encrypt("x", KEY).startsWith("v2:"));
    }

    @Test
    void samePlaintextGivesDifferentCiphertexts() {
        // ECB war deterministisch - gleiche Passwoerter haetten gleiche Ciphertexte
        assertNotEquals(AesEncryption.encrypt("pw", KEY), AesEncryption.encrypt("pw", KEY));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String cipher = AesEncryption.encrypt("pw", KEY);
        byte[] raw = Base64.getDecoder().decode(cipher.substring(3));
        raw[raw.length - 1] ^= 0x01;
        String tampered = "v2:" + Base64.getEncoder().encodeToString(raw);
        assertThrows(RuntimeException.class, () -> AesEncryption.decrypt(tampered, KEY));
    }

    @Test
    void wrongKeyIsRejected() {
        String cipher = AesEncryption.encrypt("pw", KEY);
        assertThrows(RuntimeException.class, () -> AesEncryption.decrypt(cipher, "other-encryption-key-32chars!!!!"));
    }

    @Test
    void legacyEcbCiphertextStillDecrypts() throws Exception {
        // So wurden Tokens bisher geschrieben: AES/ECB mit den ersten 16 Zeichen des Keys
        SecretKeySpec key = new SecretKeySpec(KEY.substring(0, 16).getBytes(StandardCharsets.UTF_8), "AES");
        Cipher ecb = Cipher.getInstance("AES/ECB/PKCS5Padding");
        ecb.init(Cipher.ENCRYPT_MODE, key);
        String legacy = Base64.getEncoder().encodeToString(ecb.doFinal("alt".getBytes(StandardCharsets.UTF_8)));

        assertEquals("alt", AesEncryption.decrypt(legacy, KEY));
    }

    @Test
    void nullPassesThrough() {
        assertNull(AesEncryption.encrypt(null, KEY));
        assertNull(AesEncryption.decrypt(null, KEY));
    }

    @Test
    void keyOfAnyLengthWorks() {
        String cipher = AesEncryption.encrypt("pw", "short-key");
        assertEquals("pw", AesEncryption.decrypt(cipher, "short-key"));
    }
}
