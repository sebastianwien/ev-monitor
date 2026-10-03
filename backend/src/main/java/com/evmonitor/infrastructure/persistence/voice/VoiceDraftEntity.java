package com.evmonitor.infrastructure.persistence.voice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Eine Sprachaufnahme: Nutzungsmetadaten fuer Deckel und Kosten, nie Audio, Transkript oder Feldwerte. */
@Entity
@Table(name = "voice_draft")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceDraftEntity {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "car_id")
    private UUID carId;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private boolean success;
    @Column(name = "error_code", length = 30)
    private String errorCode;
    @Column(name = "audio_seconds", precision = 7, scale = 2)
    private BigDecimal audioSeconds;
    @Column(name = "transcribe_model", length = 60)
    private String transcribeModel;
    @Column(name = "extract_model", length = 60)
    private String extractModel;
    @Column(name = "transcribe_tokens")
    private Integer transcribeTokens;
    @Column(name = "extract_prompt_tokens")
    private Integer extractPromptTokens;
    @Column(name = "extract_completion_tokens")
    private Integer extractCompletionTokens;
    @Column(name = "cost_usd", nullable = false, precision = 10, scale = 6)
    @Builder.Default
    private BigDecimal costUsd = BigDecimal.ZERO;
    @Column(name = "latency_ms")
    private Integer latencyMs;
    @Column(name = "fields_filled")
    private Integer fieldsFilled;
    @Column(name = "uncertain_count")
    private Integer uncertainCount;
}
