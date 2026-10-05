package com.evmonitor.application.voice;

import com.evmonitor.infrastructure.persistence.voice.VoiceDraftEntity;
import com.evmonitor.infrastructure.persistence.voice.VoiceDraftRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aggregiert {@code voice_draft} fuer das Admin-Dashboard im Code statt in SQL: das Volumen ist
 * klein (Monatsdeckel je Nutzer) und H2 in den Tests kennt kein {@code date_trunc}.
 */
@Service
public class VoiceUsageInsightsService {

    static final int MIN_DAYS = 1;
    static final int MAX_DAYS = 730;
    private static final int TOP_USERS = 10;

    private final VoiceDraftRepository repository;
    private final Clock clock;

    @Autowired
    public VoiceUsageInsightsService(VoiceDraftRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    /** Tage-Grenze folgt der Serverzeit, wie {@code created_at} selbst. */
    VoiceUsageInsightsService(VoiceDraftRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public VoiceUsageInsights insights(int days) {
        int period = Math.max(MIN_DAYS, Math.min(MAX_DAYS, days));
        LocalDateTime since = LocalDate.now(clock).minusDays(period - 1L).atStartOfDay();
        List<VoiceDraftEntity> rows = repository.findAllByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(since);

        Map<LocalDate, List<VoiceDraftEntity>> byDay = rows.stream()
                .collect(Collectors.groupingBy(r -> r.getCreatedAt().toLocalDate(), TreeMap::new, Collectors.toList()));
        List<VoiceUsageInsights.Day> dayRows = byDay.entrySet().stream()
                .map(e -> day(e.getKey(), e.getValue()))
                .toList();

        Map<UUID, List<VoiceDraftEntity>> byUser = rows.stream().collect(Collectors.groupingBy(VoiceDraftEntity::getUserId));
        List<VoiceUsageInsights.TopUser> topUsers = byUser.entrySet().stream()
                .map(e -> new VoiceUsageInsights.TopUser(e.getKey(), e.getValue().size(),
                        e.getValue().stream().filter(r -> !r.isSuccess()).count(), sumCost(e.getValue())))
                .sorted(Comparator.comparingLong(VoiceUsageInsights.TopUser::calls).reversed()
                        .thenComparing(VoiceUsageInsights.TopUser::costUsd, Comparator.reverseOrder()))
                .limit(TOP_USERS)
                .toList();

        List<VoiceUsageInsights.Error> errors = rows.stream()
                .filter(r -> !r.isSuccess() && r.getErrorCode() != null)
                .collect(Collectors.groupingBy(VoiceDraftEntity::getErrorCode, TreeMap::new, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new VoiceUsageInsights.Error(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(VoiceUsageInsights.Error::count).reversed()
                        .thenComparing(VoiceUsageInsights.Error::code))
                .toList();

        return new VoiceUsageInsights(period, totals(rows), dayRows, models(rows), errors, topUsers, repository.sumCostUsd());
    }

    private static VoiceUsageInsights.Totals totals(List<VoiceDraftEntity> rows) {
        long success = rows.stream().filter(VoiceDraftEntity::isSuccess).count();
        BigDecimal cost = sumCost(rows);
        BigDecimal perSuccess = success == 0 ? BigDecimal.ZERO : cost.divide(BigDecimal.valueOf(success), 6, RoundingMode.HALF_UP);
        return new VoiceUsageInsights.Totals(rows.size(), success, rows.size() - success,
                rows.stream().map(VoiceDraftEntity::getUserId).distinct().count(),
                sumAudio(rows), sumInt(rows, VoiceDraftEntity::getTranscribeTokens),
                sumInt(rows, VoiceDraftEntity::getExtractPromptTokens), sumInt(rows, VoiceDraftEntity::getExtractCompletionTokens),
                cost, avgLatency(rows),
                avgInt(rows.stream().filter(VoiceDraftEntity::isSuccess).toList(), VoiceDraftEntity::getFieldsFilled),
                avgInt(rows.stream().filter(VoiceDraftEntity::isSuccess).toList(), VoiceDraftEntity::getUncertainCount),
                perSuccess);
    }

    private static VoiceUsageInsights.Day day(LocalDate date, List<VoiceDraftEntity> rows) {
        return new VoiceUsageInsights.Day(date.toString(), rows.size(),
                rows.stream().filter(r -> !r.isSuccess()).count(), sumAudio(rows),
                sumInt(rows, VoiceDraftEntity::getTranscribeTokens), sumInt(rows, VoiceDraftEntity::getExtractPromptTokens),
                sumInt(rows, VoiceDraftEntity::getExtractCompletionTokens), sumCost(rows), avgLatency(rows));
    }

    /** Je Stufe und Modellname eine Zeile; Kosten stehen nur je Aufnahme, darum hier Tokens und Audio. */
    private static List<VoiceUsageInsights.Model> models(List<VoiceDraftEntity> rows) {
        List<VoiceUsageInsights.Model> out = new ArrayList<>();
        rows.stream().filter(r -> r.getTranscribeModel() != null)
                .collect(Collectors.groupingBy(VoiceDraftEntity::getTranscribeModel, TreeMap::new, Collectors.toList()))
                .forEach((name, group) -> out.add(new VoiceUsageInsights.Model(name, "transcribe", group.size(),
                        sumInt(group, VoiceDraftEntity::getTranscribeTokens), sumAudio(group))));
        rows.stream().filter(r -> r.getExtractModel() != null)
                .collect(Collectors.groupingBy(VoiceDraftEntity::getExtractModel, TreeMap::new, Collectors.toList()))
                .forEach((name, group) -> out.add(new VoiceUsageInsights.Model(name, "extract", group.size(),
                        sumInt(group, VoiceDraftEntity::getExtractPromptTokens) + sumInt(group, VoiceDraftEntity::getExtractCompletionTokens),
                        BigDecimal.ZERO)));
        return out;
    }

    private static BigDecimal sumCost(List<VoiceDraftEntity> rows) {
        return rows.stream().map(VoiceDraftEntity::getCostUsd).filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumAudio(List<VoiceDraftEntity> rows) {
        return rows.stream().map(VoiceDraftEntity::getAudioSeconds).filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static long sumInt(List<VoiceDraftEntity> rows, Function<VoiceDraftEntity, Integer> field) {
        return rows.stream().map(field).filter(v -> v != null).mapToLong(Integer::longValue).sum();
    }

    private static long avgLatency(List<VoiceDraftEntity> rows) {
        return Math.round(rows.stream().map(VoiceDraftEntity::getLatencyMs).filter(v -> v != null)
                .mapToInt(Integer::intValue).average().orElse(0));
    }

    private static double avgInt(List<VoiceDraftEntity> rows, Function<VoiceDraftEntity, Integer> field) {
        return rows.stream().map(field).filter(v -> v != null).mapToInt(Integer::intValue).average().orElse(0);
    }
}
