package com.evmonitor.application.voice;

import com.evmonitor.application.user.UserChargingProviderResponse;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.infrastructure.persistence.voice.VoiceDraftEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.evmonitor.application.voice.VoiceContextBuilderTest.site;
import static com.evmonitor.application.voice.VoiceContextBuilderTest.station;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VoiceLogServiceTest {

    static final UUID USER = UUID.randomUUID();
    static final UUID CAR = UUID.randomUUID();
    static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    static final UUID OCTOPUS = UUID.randomUUID();

    private final CarRepository carRepository = mock(CarRepository.class);
    private final VoiceContextBuilder contextBuilder = mock(VoiceContextBuilder.class);
    private final VoiceQuotaService quotaService = mock(VoiceQuotaService.class);
    private final SpeechTranscriber transcriber = mock(SpeechTranscriber.class);
    private final LogDraftExtractor extractor = mock(LogDraftExtractor.class);
    private final VoiceLogService service = new VoiceLogService(carRepository, contextBuilder, quotaService, transcriber, extractor);
    private final Car car = mock(Car.class);

    private final VoiceContext context = new VoiceContext(
            List.of(new PlaceCandidate.Home(), new PlaceCandidate.Site(site("Lidl", "u0wt8b2")),
                    new PlaceCandidate.Station(station("EnBW", "u0wt8b1"))),
            List.of(new UserChargingProviderResponse(OCTOPUS, "Octopus", null, null, null, null, null, null,
                    LocalDate.of(2025, 1, 1), null, false)),
            List.of("IONITY"), 31_207, "2026-10-03 (Samstag), Zeitzone Europe/Berlin", "Nutzbare Akkukapazität 77 kWh");

    static final VoiceUsage TRANSCRIBE_USAGE = new VoiceUsage("voxtral-mini-latest", 11.0, 9, 44, new BigDecimal("0.000550"));
    static final VoiceUsage EXTRACT_USAGE = new VoiceUsage("voxtral-small-latest", null, 1450, 80, new BigDecimal("0.000169"));

    @BeforeEach
    void setUp() {
        when(carRepository.findById(CAR)).thenReturn(Optional.of(car));
        when(car.isOwnedBy(USER)).thenReturn(true);
        when(quotaService.quota(USER, BERLIN)).thenReturn(new VoiceQuota(5, 3, false, LocalDate.of(2026, 11, 1)));
        when(contextBuilder.build(eq(USER), eq(car), any(), any(), eq(BERLIN))).thenReturn(context);
        when(transcriber.transcribe(any(), anyString(), anyList()))
                .thenReturn(new Transcript("Geladen bei EnBW, 32 Kilowattstunden", TRANSCRIBE_USAGE));
    }

    private VoiceDraftCommand command() {
        return new VoiceDraftCommand(USER, CAR, new byte[]{1, 2}, "audio/webm", 48.1, 11.5, BERLIN);
    }

    private void extracted(DraftFields fields) {
        when(extractor.extract(anyString(), any())).thenReturn(new Extraction(fields, EXTRACT_USAGE));
    }

    private VoiceDraftEntity recordedRow() {
        ArgumentCaptor<VoiceDraftEntity> row = ArgumentCaptor.forClass(VoiceDraftEntity.class);
        verify(quotaService).record(row.capture());
        return row.getValue();
    }

    @Test
    void foreignCarIsRejectedBeforeAnyCall() {
        when(car.isOwnedBy(USER)).thenReturn(false);

        assertThatThrownBy(() -> service.draft(command())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transcriber, extractor);
        verify(quotaService, never()).record(any());
    }

    @Test
    void exhaustedQuotaStopsBeforeMistral() {
        when(quotaService.quota(USER, BERLIN)).thenReturn(new VoiceQuota(5, 0, true, LocalDate.of(2026, 11, 1)));

        assertThatThrownBy(() -> service.draft(command()))
                .isInstanceOfSatisfying(VoiceQuotaExceededException.class,
                        e -> assertThat(e.quota().resetsOn()).isEqualTo(LocalDate.of(2026, 11, 1)));
        verifyNoInteractions(transcriber, extractor);
    }

    @Test
    void biasTermsAndNumberedContextReachMistral() {
        extracted(DraftFields.builder().build());

        service.draft(command());

        verify(transcriber).transcribe(any(), eq("audio/webm"), eq(context.biasTerms()));
        verify(extractor).extract("Geladen bei EnBW, 32 Kilowattstunden", context.toExtractionContext());
    }

    @Test
    void placeIndexMapsToTheCandidateType() {
        extracted(DraftFields.builder().placeIndex(2).placeKind("station").build());
        assertThat(service.draft(command()).place().kind()).isEqualTo("station");
        assertThat(service.draft(command()).place().station().name()).isEqualTo("EnBW");

        extracted(DraftFields.builder().placeIndex(1).placeKind("station").build());
        VoiceDraftResult site = service.draft(command());
        assertThat(site.place().kind()).isEqualTo("site");
        assertThat(site.place().site().site().name()).isEqualTo("Lidl");
    }

    @Test
    void homeWithOrWithoutIndexIsHome() {
        extracted(DraftFields.builder().placeIndex(0).placeKind("home").build());
        assertThat(service.draft(command()).place().kind()).isEqualTo("home");

        extracted(DraftFields.builder().placeKind("home").build());
        assertThat(service.draft(command()).place().kind()).isEqualTo("home");
    }

    @Test
    void noMatchingCandidateIsOtherWithTheSpokenOperator() {
        extracted(DraftFields.builder().placeKind("other").spokenOperator("Mer").build());

        PlaceDraft place = service.draft(command()).place();

        assertThat(place.kind()).isEqualTo("other");
        assertThat(place.cpoName()).isEqualTo("Mer");
    }

    @Test
    void outOfRangeIndicesAreDroppedAndMarked() {
        extracted(DraftFields.builder().placeIndex(7).placeKind("station").spokenOperator("Aral").tariffIndex(3).build());

        VoiceDraftResult result = service.draft(command());

        assertThat(result.place().kind()).isEqualTo("other");
        assertThat(result.place().cpoName()).isEqualTo("Aral");
        assertThat(result.chargingProviderId()).isNull();
        assertThat(result.fields().uncertain()).contains("placeIndex", "tariffIndex");
    }

    @Test
    void noPlaceSpokenMeansNoPlace() {
        extracted(DraftFields.builder().kwhCharged(30.0).build());

        assertThat(service.draft(command()).place()).isNull();
    }

    @Test
    void tariffIndexBecomesTheProviderId() {
        extracted(DraftFields.builder().tariffIndex(0).build());

        assertThat(service.draft(command()).chargingProviderId()).isEqualTo(OCTOPUS);
    }

    @Test
    void rulesAndPlausibilityAreAppliedAgainstTheLastOdometer() {
        extracted(DraftFields.builder().kwhCharged(12.0).maxChargingPowerKw(12.0).odometerKm(31_000).pricePerKwh(0.5).build());

        DraftFields fields = service.draft(command()).fields();

        assertThat(fields.maxChargingPowerKw()).isNull();
        assertThat(fields.costEur()).isEqualTo(6.0);
        assertThat(fields.odometerKm()).isEqualTo(31_000);
        assertThat(fields.uncertain()).containsExactly("odometerKm");
    }

    @Test
    void successIsRecordedWithUsageAndFrozenCostButNoContent() {
        extracted(DraftFields.builder().kwhCharged(32.0).socAfter(80).chargingType(ChargingType.DC).uncertain(List.of("socAfter")).build());

        VoiceDraftResult result = service.draft(command());

        VoiceDraftEntity row = recordedRow();
        assertThat(row.isSuccess()).isTrue();
        assertThat(row.getUserId()).isEqualTo(USER);
        assertThat(row.getCarId()).isEqualTo(CAR);
        assertThat(row.getAudioSeconds()).isEqualByComparingTo("11");
        assertThat(row.getTranscribeModel()).isEqualTo("voxtral-mini-latest");
        assertThat(row.getExtractModel()).isEqualTo("voxtral-small-latest");
        assertThat(row.getTranscribeTokens()).isEqualTo(44);
        assertThat(row.getExtractPromptTokens()).isEqualTo(1450);
        assertThat(row.getExtractCompletionTokens()).isEqualTo(80);
        assertThat(row.getCostUsd()).isEqualByComparingTo("0.000719");
        assertThat(row.getFieldsFilled()).isEqualTo(3);
        assertThat(row.getUncertainCount()).isEqualTo(1);
        assertThat(row.getLatencyMs()).isNotNull();
        assertThat(result.quota().remaining()).isEqualTo(2);
    }

    @Test
    void providerFailureIsRecordedAsFailedWithCostSoFarAndRethrown() {
        when(extractor.extract(anyString(), any()))
                .thenThrow(new VoiceProviderException(VoiceProviderException.Reason.RATE_LIMITED, "Mistral 429"));

        assertThatThrownBy(() -> service.draft(command())).isInstanceOf(VoiceProviderException.class);

        VoiceDraftEntity row = recordedRow();
        assertThat(row.isSuccess()).isFalse();
        assertThat(row.getErrorCode()).isEqualTo("RATE_LIMITED");
        assertThat(row.getCostUsd()).isEqualByComparingTo("0.000550");
    }

    @Test
    void emptyTranscriptFailsWithoutExtraction() {
        when(transcriber.transcribe(any(), anyString(), anyList())).thenReturn(new Transcript("  ", TRANSCRIBE_USAGE));

        assertThatThrownBy(() -> service.draft(command()))
                .isInstanceOfSatisfying(VoiceProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(VoiceProviderException.Reason.EMPTY_TRANSCRIPT));
        verifyNoInteractions(extractor);
        assertThat(recordedRow().getErrorCode()).isEqualTo("EMPTY_TRANSCRIPT");
    }
}
