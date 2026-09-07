package com.evmonitor.infrastructure.external.xpeng;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Request-Signatur fuer die XPeng EU-Data-Act Open API.
 *
 * <p>Laut Integration Guide:
 * <pre>
 *   stringA = "body" + &lt;body&gt; + "nonce" + &lt;nonce&gt;   (Keys aufsteigend sortiert)
 *   stringB = appId + stringA + appSecret
 *   sign    = SHA1(stringB).toLowerCase()   (hex)
 * </pre>
 * Der {@code appSecret} geht ausschliesslich in die Signatur ein, nie in den Request.
 */
public final class XpengApiSigner {

    private XpengApiSigner() {
    }

    public static String sign(String appId, String appSecret, String body, String nonce) {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId darf nicht leer sein");
        }
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("appSecret darf nicht leer sein");
        }
        String stringB = appId + "body" + body + "nonce" + nonce + appSecret;
        return sha1Hex(stringB);
    }

    private static String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 ist in jeder JVM vorhanden - kann praktisch nicht passieren.
            throw new IllegalStateException("SHA-1 nicht verfuegbar", e);
        }
    }
}
