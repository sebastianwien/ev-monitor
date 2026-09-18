package com.evmonitor.infrastructure.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Verschluesselung fuer Tokens und Passwoerter in der DB.
 * <p>
 * Neue Werte: AES-256-GCM mit zufaelliger IV, gekennzeichnet durch das Praefix {@code v2:}.
 * Der Schluessel wird per SHA-256 aus dem konfigurierten Key abgeleitet, damit jede Key-Laenge
 * volle 256 Bit ergibt. Werte ohne Praefix stammen aus dem frueheren AES-ECB-Format und werden
 * weiterhin gelesen; beim naechsten Schreiben landen sie automatisch im neuen Format.
 */
public final class AesEncryption {

    private static final String GCM_PREFIX = "v2:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AesEncryption() {}

    public static String encrypt(String value, String keyString) {
        if (value == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, gcmKey(keyString), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
            return GCM_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public static String decrypt(String value, String keyString) {
        if (value == null) return null;
        try {
            return value.startsWith(GCM_PREFIX)
                    ? decryptGcm(value.substring(GCM_PREFIX.length()), keyString)
                    : decryptLegacyEcb(value, keyString);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private static String decryptGcm(String base64, String keyString) throws Exception {
        byte[] raw = Base64.getDecoder().decode(base64);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, gcmKey(keyString), spec);
        return new String(cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES), StandardCharsets.UTF_8);
    }

    /** Altbestand: AES/ECB mit den ersten 16 Zeichen des Keys. Nur noch lesen, nie schreiben. */
    private static String decryptLegacyEcb(String base64, String keyString) throws Exception {
        SecretKeySpec key = new SecretKeySpec(
                keyString.substring(0, 16).getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return new String(cipher.doFinal(Base64.getDecoder().decode(base64)), StandardCharsets.UTF_8);
    }

    private static SecretKeySpec gcmKey(String keyString) throws Exception {
        byte[] key = MessageDigest.getInstance("SHA-256").digest(keyString.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }
}
