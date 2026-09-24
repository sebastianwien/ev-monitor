package com.evmonitor.application.imports.sample;

import com.evmonitor.infrastructure.persistence.sample.ImportSample;
import com.evmonitor.infrastructure.persistence.sample.ImportSampleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Legt pseudonymisierte Kopien hochgeladener Herstellerdateien ab (Tabelle {@code import_sample}),
 * damit Decoder und Detektoren gegen echte Dateien getestet werden koennen - gerade gegen solche,
 * die nicht erkannt wurden. Formatfrei: die Pseudonymisierung liefert der Aufrufer, der das Format kennt.
 *
 * <p>Best Effort: nichts hier darf einen Import abbrechen. Die Ablage laeuft deshalb in einer eigenen
 * Transaktion (REQUIRES_NEW), jeder Fehler wird geschluckt und nur mit Klassennamen geloggt, nie mit
 * Inhalt. Dieselbe Datei (Vorschau, dann Import) wird ueber den Hash des Originals nur einmal abgelegt.
 */
@Service
@Slf4j
public class ImportSampleService {

    public enum Provider { VW_EUDA, XPENG }

    public enum Channel { UPLOAD, HISTORY }

    public enum Outcome { OK, NO_SESSIONS, UNREADABLE, FAILED }

    public record SampleMeta(UUID userId, Provider provider, Channel channel, String carModel, String originalSha256,
                             Outcome outcome, Integer sessionsDetected, Integer tripsDetected, String error) {}

    public record Anonymized(String fileName, byte[] zip) {}

    @FunctionalInterface
    public interface Anonymizer {
        Optional<Anonymized> anonymize() throws IOException;
    }

    static final int MAX_ERROR_LENGTH = 500;

    private final ImportSampleRepository repository;
    private final TransactionTemplate ownTransaction;
    private final boolean enabled;
    private final long maxBytes;

    public ImportSampleService(ImportSampleRepository repository,
                               PlatformTransactionManager transactionManager,
                               @Value("${import-samples.enabled:false}") boolean enabled,
                               @Value("${import-samples.max-bytes:26214400}") long maxBytes) {
        this.repository = repository;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.enabled = enabled;
        this.maxBytes = maxBytes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long maxBytes() {
        return maxBytes;
    }

    /** Pseudonymisiert (nur wenn noetig) und legt ab. Wirft nie. */
    public void record(SampleMeta meta, Anonymizer anonymizer) {
        if (!enabled) return;
        try {
            if (repository.existsByProviderAndContentSha256(meta.provider().name(), meta.originalSha256())) return;
            Optional<Anonymized> copy = anonymizer.anonymize();
            if (copy.isEmpty() || copy.get().zip().length > maxBytes) return;
            ImportSample sample = toEntity(meta, copy.get());
            ownTransaction.executeWithoutResult(status -> repository.save(sample));
        } catch (Exception e) {
            log.warn("Import-Sample {}/{} nicht abgelegt: {}", meta.provider(), meta.channel(), e.getClass().getSimpleName());
        }
    }

    private static ImportSample toEntity(SampleMeta meta, Anonymized copy) {
        String error = meta.error();
        if (error != null && error.length() > MAX_ERROR_LENGTH) error = error.substring(0, MAX_ERROR_LENGTH);
        return ImportSample.builder()
                .id(UUID.randomUUID())
                .userId(meta.userId())
                .provider(meta.provider().name())
                .channel(meta.channel().name())
                .carModel(meta.carModel())
                .contentSha256(meta.originalSha256())
                .fileName(copy.fileName())
                .sizeBytes(copy.zip().length)
                .content(copy.zip())
                .outcome(meta.outcome().name())
                .sessionsDetected(meta.sessionsDetected())
                .tripsDetected(meta.tripsDetected())
                .error(error)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /** SHA-256 als Hex, liest den Stream vollstaendig und schliesst ihn. */
    public static String sha256(InputStream in) throws IOException {
        try (DigestInputStream d = new DigestInputStream(in, MessageDigest.getInstance("SHA-256"))) {
            d.transferTo(java.io.OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(d.getMessageDigest().digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
