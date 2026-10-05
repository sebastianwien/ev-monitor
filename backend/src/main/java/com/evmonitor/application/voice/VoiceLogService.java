package com.evmonitor.application.voice;

import com.evmonitor.application.user.UserChargingProviderResponse;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.infrastructure.persistence.voice.VoiceDraftEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Sprachlog: Aufnahme zu einem Entwurf fuer die Pruefansicht. Zwei Stufen ueber die Ports
 * {@link SpeechTranscriber} und {@link LogDraftExtractor}, danach deterministische Nachregeln.
 *
 * <p>Gespeichert wird nichts ausser einer Zeile Nutzungsmetadaten in {@code voice_draft}: kein Audio,
 * kein Transkript, keine Feldwerte. Das Transkript wird nie geloggt. Keine Coins fuer Sprache.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceLogService {

    private final CarRepository carRepository;
    private final VoiceContextBuilder contextBuilder;
    private final VoiceQuotaService quotaService;
    private final SpeechTranscriber transcriber;
    private final LogDraftExtractor extractor;

    public VoiceDraftResult draft(VoiceDraftCommand cmd) {
        Car car = carRepository.findById(cmd.carId())
                .filter(c -> c.isOwnedBy(cmd.userId()))
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        VoiceQuota quota = quotaService.quota(cmd.userId(), cmd.zone());
        if (quota.exhausted()) throw new VoiceQuotaExceededException(quota);

        VoiceContext context = contextBuilder.build(cmd.userId(), car, cmd.lat(), cmd.lon(), cmd.zone());
        long started = System.nanoTime();
        Transcript transcript = null;
        Extraction extraction;
        try {
            transcript = transcriber.transcribe(cmd.audio(), cmd.mimeType(), context.biasTerms());
            if (transcript.text() == null || transcript.text().isBlank()) {
                throw new VoiceProviderException(VoiceProviderException.Reason.EMPTY_TRANSCRIPT, "nichts verstanden");
            }
            extraction = extractor.extract(transcript.text(), context.toExtractionContext());
        } catch (VoiceProviderException e) {
            quotaService.record(row(cmd, started, transcript, null).success(false).errorCode(e.reason().name()).build());
            log.info("Sprachlog fehlgeschlagen: {} nach {} ms", e.reason(), millisSince(started));
            throw e;
        }

        Resolved resolved = resolve(extraction.fields(), context, transcript.text());
        DraftFields fields = VoiceDraftRules.apply(resolved.fields(), context.lastOdometerKm());
        quotaService.record(row(cmd, started, transcript, extraction)
                .success(true).fieldsFilled(fields.filledCount()).uncertainCount(fields.uncertain().size()).build());
        log.info("Sprachlog: {} Felder, {} unsicher, Transkript {} Zeichen, {} ms",
                fields.filledCount(), fields.uncertain().size(), transcript.text().length(), millisSince(started));
        return new VoiceDraftResult(transcript.text(), fields, resolved.place(), resolved.providerId(), quota.afterUse());
    }

    private record Resolved(DraftFields fields, PlaceDraft place, UUID providerId) {}

    /** Indizes gelten nur fuer diesen Request: ausserhalb der Liste fallen sie weg und werden markiert. */
    private static Resolved resolve(DraftFields f, VoiceContext context, String transcript) {
        List<String> uncertain = new ArrayList<>(f.uncertain());
        DraftFields.DraftFieldsBuilder out = f.toBuilder();

        PlaceDraft place;
        List<PlaceCandidate> candidates = context.candidates();
        if (f.placeIndex() != null && f.placeIndex() >= 0 && f.placeIndex() < candidates.size()) {
            place = PlaceDraft.of(candidates.get(f.placeIndex()));
        } else {
            if (f.placeIndex() != null) {
                uncertain.add("placeIndex");
                out.placeIndex(null);
            }
            if ("home".equals(f.placeKind())) place = PlaceDraft.home();
            else if (f.placeKind() != null || f.placeIndex() != null) place = PlaceDraft.other(f.spokenOperator());
            else place = null;
        }

        // Mit gesprochener Adresse ist ein anderer Ort als die aktuelle Position gemeint: "hier privat" faellt
        // weg, der Client sucht die Adresse. Eine Saeule aus der Liste bleibt nur, wenn ihr Name im Transkript
        // vorkommt - im Eval vom 05.10.2026 waehlte das Modell zur Adresse eine nie genannte Saeule.
        boolean addressSpoken = f.spokenAddress() != null && !f.spokenAddress().isBlank();
        if (addressSpoken && place != null && "home".equals(place.kind())) place = null;
        else if (addressSpoken && place != null && !"other".equals(place.kind()) && !mentioned(f.placeIndex(), candidates, transcript)) {
            uncertain.add("placeIndex");
            out.placeIndex(null);
            place = null;
        }

        UUID providerId = null;
        List<UserChargingProviderResponse> tariffs = context.tariffs();
        if (f.tariffIndex() != null) {
            if (f.tariffIndex() >= 0 && f.tariffIndex() < tariffs.size()) {
                providerId = tariffs.get(f.tariffIndex()).id();
            } else {
                uncertain.add("tariffIndex");
                out.tariffIndex(null);
            }
        }
        return new Resolved(out.uncertain(uncertain).build(), place, providerId);
    }

    /** Ob der Name des gewaehlten Kandidaten (ein Wort ab 3 Zeichen) im Transkript steht. */
    private static boolean mentioned(Integer index, List<PlaceCandidate> candidates, String transcript) {
        if (index == null || index < 0 || index >= candidates.size()) return false;
        PlaceCandidate c = candidates.get(index);
        String text = normalize(transcript);
        return Arrays.stream(c.name().split("\\s+")).map(VoiceLogService::normalize)
                .anyMatch(w -> w.length() >= 3 && text.contains(w));
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static VoiceDraftEntity.VoiceDraftEntityBuilder row(VoiceDraftCommand cmd, long started,
                                                                Transcript transcript, Extraction extraction) {
        VoiceUsage t = transcript == null ? null : transcript.usage();
        VoiceUsage e = extraction == null ? null : extraction.usage();
        BigDecimal cost = BigDecimal.ZERO;
        if (t != null && t.costUsd() != null) cost = cost.add(t.costUsd());
        if (e != null && e.costUsd() != null) cost = cost.add(e.costUsd());
        return VoiceDraftEntity.builder()
                .userId(cmd.userId())
                .carId(cmd.carId())
                .createdAt(LocalDateTime.now())
                .latencyMs((int) millisSince(started))
                .audioSeconds(t == null || t.audioSeconds() == null ? null : BigDecimal.valueOf(t.audioSeconds()))
                .transcribeModel(t == null ? null : t.model())
                .transcribeTokens(t == null ? null : t.completionTokens())
                .extractModel(e == null ? null : e.model())
                .extractPromptTokens(e == null ? null : e.promptTokens())
                .extractCompletionTokens(e == null ? null : e.completionTokens())
                .costUsd(cost);
    }

    private static long millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
