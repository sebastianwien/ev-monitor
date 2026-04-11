package com.evmonitor.application.publicapi;

import com.evmonitor.domain.ApiKey;
import com.evmonitor.domain.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String KEY_PREFIX = "evm_";
    private static final int KEY_RANDOM_BYTES = 24; // 24 bytes → 32 Base64url chars
    private static final int MAX_KEYS_PER_USER = 10;

    private final ApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Erstellt einen neuen API Key. Der Plaintext-Key wird nur hier einmalig zurückgegeben
     * und danach NIEMALS wieder angezeigt oder gespeichert.
     */
    @Transactional
    public ApiKeyCreatedResponse createKey(UUID userId, String name) {
        if (apiKeyRepository.countByUserId(userId) >= MAX_KEYS_PER_USER) {
            throw new IllegalStateException("Maximale Anzahl API Keys erreicht (" + MAX_KEYS_PER_USER + "). Bitte zuerst einen vorhandenen Key löschen.");
        }
        byte[] randomBytes = new byte[KEY_RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String plaintext = KEY_PREFIX + randomPart;

        String hash = sha256Hex(plaintext);
        // Prefix für UI: "evm_" + erste 4 Zeichen des Zufallsteils
        String uiPrefix = KEY_PREFIX + randomPart.substring(0, 4);

        ApiKey apiKey = ApiKey.createNew(userId, hash, uiPrefix, name);
        ApiKey saved = apiKeyRepository.save(apiKey);

        return new ApiKeyCreatedResponse(saved.getId(), uiPrefix, name, saved.getCreatedAt(), plaintext, saved.isMergeSessions());
    }

    public List<ApiKeyResponse> listKeys(UUID userId) {
        return apiKeyRepository.findAllByUserId(userId)
                .stream()
                .map(k -> new ApiKeyResponse(k.getId(), k.getKeyPrefix(), k.getName(),
                        k.getLastUsedAt(), k.getCreatedAt(), k.isMergeSessions()))
                .toList();
    }

    @Transactional
    public ApiKeyResponse updateMergeSessions(UUID userId, UUID keyId, boolean mergeSessions) {
        ApiKey updated = apiKeyRepository.updateMergeSessions(keyId, userId, mergeSessions);
        return new ApiKeyResponse(updated.getId(), updated.getKeyPrefix(), updated.getName(),
                updated.getLastUsedAt(), updated.getCreatedAt(), updated.isMergeSessions());
    }

    @Transactional
    public void deleteKey(UUID userId, UUID keyId) {
        apiKeyRepository.deleteByIdAndUserId(keyId, userId);
    }

    /**
     * Validiert einen Plaintext-Key timing-attack-sicher via SHA-256 → DB-Lookup.
     * Gibt den zugehörigen ApiKey zurück oder empty wenn ungültig.
     */
    public Optional<ApiKey> validateKey(String plaintext) {
        if (plaintext == null || !plaintext.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        String hash = sha256Hex(plaintext);
        // SHA-256 auf 192-Bit-Entropie-Input: Brute-Force-Angriff auf den Hash nicht praktikabel.
        // Der DB-Lookup via UNIQUE index ist schnell genug, dass Timing-Angriffe auf den Hash-Raum
        // keinen praktischen Nutzen haben (2^256 Preimage-Suchraum).
        Optional<ApiKey> apiKey = apiKeyRepository.findByKeyHash(hash);
        apiKey.ifPresent(k -> apiKeyRepository.updateLastUsedAt(k.getId()));
        return apiKey;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
