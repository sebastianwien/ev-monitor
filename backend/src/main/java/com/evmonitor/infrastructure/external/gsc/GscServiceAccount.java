package com.evmonitor.infrastructure.external.gsc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** Die drei Felder aus dem Google-Service-Account-JSON, die der Token-Tausch braucht. */
public record GscServiceAccount(String clientEmail, PrivateKey privateKey, String tokenUri) {

    /** Akzeptiert rohes JSON (beginnt mit {) oder Base64 davon (eine Zeile in .env). */
    public static GscServiceAccount parse(String raw, ObjectMapper objectMapper) {
        String json = raw.trim().startsWith("{")
                ? raw.trim()
                : new String(Base64.getMimeDecoder().decode(raw.trim()), StandardCharsets.UTF_8);
        try {
            JsonNode node = objectMapper.readTree(json);
            String email = node.path("client_email").asText(null);
            String pem = node.path("private_key").asText(null);
            String tokenUri = node.path("token_uri").asText("https://oauth2.googleapis.com/token");
            if (email == null || pem == null) {
                throw new IllegalArgumentException("Service-Account-JSON ohne client_email oder private_key");
            }
            return new GscServiceAccount(email, parsePrivateKey(pem), tokenUri);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // Fehlertext bewusst ohne Inhalt des Keys
            throw new IllegalArgumentException("Service-Account-JSON nicht lesbar: " + e.getClass().getSimpleName());
        }
    }

    private static PrivateKey parsePrivateKey(String pem) throws Exception {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    @Override
    public String toString() {
        return "GscServiceAccount[" + clientEmail + "]";
    }
}
