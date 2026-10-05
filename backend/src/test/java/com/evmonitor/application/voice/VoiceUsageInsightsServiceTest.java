package com.evmonitor.application.voice;

import com.evmonitor.infrastructure.persistence.voice.VoiceDraftEntity;
import com.evmonitor.infrastructure.persistence.voice.VoiceDraftRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VoiceUsageInsightsServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T10:00:00Z"), ZONE);
    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();

    private final VoiceDraftRepository repository = mock(VoiceDraftRepository.class);
    private final VoiceUsageInsightsService service = new VoiceUsageInsightsService(repository, CLOCK);

    @Test
    void aggregatesTotalsDaysModelsErrorsAndUsers() {
        when(repository.findAllByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(any())).thenReturn(List.of(
                ok(USER_A, "2026-10-04T08:00", 12.5, 40, 900, 120, "0.004000", 1800, 6, 1),
                ok(USER_A, "2026-10-04T09:00", 7.5, 25, 800, 100, "0.002000", 1200, 5, 0),
                failed(USER_B, "2026-10-05T07:30", "RATE_LIMITED", 3.0, 10, "0.000150", 400),
                failed(USER_B, "2026-10-05T07:31", "EMPTY_TRANSCRIPT", null, null, "0.000000", 300)));
        when(repository.sumCostUsd()).thenReturn(new BigDecimal("1.234567"));

        VoiceUsageInsights r = service.insights(30);

        VoiceUsageInsights.Totals t = r.totals();
        assertEquals(4, t.calls());
        assertEquals(2, t.successCalls());
        assertEquals(2, t.failedCalls());
        assertEquals(2, t.distinctUsers());
        assertEquals(0, new BigDecimal("23.00").compareTo(t.audioSeconds()));
        assertEquals(75, t.transcribeTokens());
        assertEquals(1700, t.extractPromptTokens());
        assertEquals(220, t.extractCompletionTokens());
        assertEquals(0, new BigDecimal("0.006150").compareTo(t.costUsd()));
        assertEquals(925, t.avgLatencyMs());
        assertEquals(5.5, t.avgFieldsFilled(), 0.001);
        assertEquals(0.5, t.avgUncertain(), 0.001);
        // Kosten je erfolgreichem Entwurf: alle Kosten (auch Fehlschlaege) geteilt durch Erfolge
        assertEquals(0, new BigDecimal("0.003075").compareTo(t.costPerSuccessUsd()));
        assertEquals(0, new BigDecimal("1.234567").compareTo(r.allTimeCostUsd()));

        assertEquals(2, r.days().size());
        VoiceUsageInsights.Day d1 = r.days().get(0);
        assertEquals("2026-10-04", d1.date());
        assertEquals(2, d1.calls());
        assertEquals(0, d1.failedCalls());
        assertEquals(0, new BigDecimal("0.006000").compareTo(d1.costUsd()));
        assertEquals(1500, d1.avgLatencyMs());
        VoiceUsageInsights.Day d2 = r.days().get(1);
        assertEquals("2026-10-05", d2.date());
        assertEquals(2, d2.failedCalls());

        assertEquals(2, r.models().size());
        VoiceUsageInsights.Model transcribe = r.models().stream().filter(m -> m.kind().equals("transcribe")).findFirst().orElseThrow();
        assertEquals("voxtral-mini-latest", transcribe.name());
        assertEquals(3, transcribe.calls());
        assertEquals(75, transcribe.tokens());
        VoiceUsageInsights.Model extract = r.models().stream().filter(m -> m.kind().equals("extract")).findFirst().orElseThrow();
        assertEquals(2, extract.calls());
        assertEquals(1920, extract.tokens());

        assertEquals(List.of(new VoiceUsageInsights.Error("EMPTY_TRANSCRIPT", 1), new VoiceUsageInsights.Error("RATE_LIMITED", 1)),
                r.errors());

        assertEquals(USER_A, r.topUsers().get(0).userId());
        assertEquals(2, r.topUsers().get(0).calls());
        assertEquals(0, new BigDecimal("0.006000").compareTo(r.topUsers().get(0).costUsd()));
    }

    @Test
    void emptyPeriodYieldsZerosNotNulls() {
        when(repository.findAllByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(repository.sumCostUsd()).thenReturn(BigDecimal.ZERO);

        VoiceUsageInsights r = service.insights(7);

        assertEquals(0, r.totals().calls());
        assertEquals(0, r.totals().avgLatencyMs());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.totals().costPerSuccessUsd()));
        assertTrue(r.days().isEmpty());
        assertTrue(r.models().isEmpty());
    }

    @Test
    void daysAreClampedToSaneRange() {
        when(repository.findAllByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(repository.sumCostUsd()).thenReturn(BigDecimal.ZERO);

        assertEquals(1, service.insights(0).periodDays());
        assertEquals(730, service.insights(9999).periodDays());
    }

    private static VoiceDraftEntity ok(UUID user, String at, double audioSeconds, int transcribeTokens, int promptTokens,
                                       int completionTokens, String cost, int latency, int fields, int uncertain) {
        return VoiceDraftEntity.builder().userId(user).createdAt(LocalDateTime.parse(at)).success(true)
                .audioSeconds(BigDecimal.valueOf(audioSeconds)).transcribeModel("voxtral-mini-latest")
                .transcribeTokens(transcribeTokens).extractModel("voxtral-small-latest")
                .extractPromptTokens(promptTokens).extractCompletionTokens(completionTokens)
                .costUsd(new BigDecimal(cost)).latencyMs(latency).fieldsFilled(fields).uncertainCount(uncertain).build();
    }

    private static VoiceDraftEntity failed(UUID user, String at, String code, Double audioSeconds, Integer transcribeTokens,
                                           String cost, int latency) {
        return VoiceDraftEntity.builder().userId(user).createdAt(LocalDateTime.parse(at)).success(false).errorCode(code)
                .audioSeconds(audioSeconds == null ? null : BigDecimal.valueOf(audioSeconds))
                .transcribeModel(audioSeconds == null ? null : "voxtral-mini-latest").transcribeTokens(transcribeTokens)
                .costUsd(new BigDecimal(cost)).latencyMs(latency).build();
    }
}
