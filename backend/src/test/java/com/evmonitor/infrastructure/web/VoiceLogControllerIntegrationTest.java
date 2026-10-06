package com.evmonitor.infrastructure.web;

import com.evmonitor.application.voice.DraftFields;
import com.evmonitor.application.voice.Extraction;
import com.evmonitor.application.voice.Transcript;
import com.evmonitor.application.voice.VoiceProviderException;
import com.evmonitor.application.voice.VoiceUsage;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.persistence.voice.VoiceDraftRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sprachlog-Endpoint mit echtem Service und echter Tabelle, nur die Mistral-Ports sind gemockt. */
class VoiceLogControllerIntegrationTest extends AbstractIntegrationTest {

    static final String URL = "/api/logs/voice-draft";
    static final VoiceUsage T_USAGE = new VoiceUsage("voxtral-mini-latest", 11.0, 9, 44, new BigDecimal("0.000550"));
    static final VoiceUsage E_USAGE = new VoiceUsage("voxtral-small-latest", null, 1450, 80, new BigDecimal("0.000169"));

    @Autowired
    private VoiceDraftRepository voiceDraftRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        // Admin: stilles Limit statt Zähler, so laufen die 60 Aufnahmen im Deckel-Test ohne Free-Grenze
        user = createAndSaveAdminUser("voice-" + UUID.randomUUID() + "@test.local");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        when(speechTranscriber.transcribe(any(), anyString(), anyList()))
                .thenReturn(new Transcript("Zuhause geladen, 32 Kilowattstunden, Tacho 48210, auf 80 Prozent, 9 Euro 60", T_USAGE));
        when(logDraftExtractor.extract(anyString(), any())).thenReturn(new Extraction(DraftFields.builder()
                .placeIndex(0).placeKind("home").kwhCharged(32.0).odometerKm(48_210).socAfter(80).costEur(9.6).build(), E_USAGE));
    }

    private HttpEntity<MultiValueMap<String, Object>> request(User who, UUID carId, byte[] audio, String mime, String zone) {
        HttpHeaders headers = who == null ? new HttpHeaders() : createAuthHeaders(who.getId(), who.getEmail());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(mime));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("audio", new HttpEntity<>(new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return "aufnahme";
            }
        }, partHeaders));
        form.add("carId", carId.toString());
        if (zone != null) form.add("timeZone", zone);
        return new HttpEntity<>(form, headers);
    }

    private ResponseEntity<String> post(HttpEntity<MultiValueMap<String, Object>> entity) {
        return restTemplate.postForEntity(URL, entity, String.class);
    }

    @Test
    void requiresAuthentication() {
        assertThat(post(request(null, car.getId(), new byte[]{1}, "audio/webm", "Europe/Berlin")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void draftsHomeChargeAndRecordsUsageWithoutContent() throws Exception {
        ResponseEntity<String> response = post(request(user, car.getId(), new byte[]{1, 2, 3}, "audio/webm;codecs=opus", "Europe/Berlin"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("transcript").asText()).startsWith("Zuhause geladen");
        assertThat(body.path("place").path("kind").asText()).isEqualTo("home");
        assertThat(body.path("fields").path("odometerKm").asInt()).isEqualTo(48_210);
        // Admins haben nur das stille Fair-Use-Limit, keinen sichtbaren Zaehler
        assertThat(body.path("usage").path("limit").isNull()).isTrue();
        assertThat(body.path("usage").path("plan").asText()).isEqualTo("admin");
        verify(speechTranscriber).transcribe(any(), eq("audio/webm"), anyList());

        assertThat(voiceDraftRepository.countSuccessfulSince(user.getId(), LocalDateTime.now().minusDays(1))).isEqualTo(1);
        var row = voiceDraftRepository.findAll().stream().filter(r -> r.getUserId().equals(user.getId())).findFirst().orElseThrow();
        assertThat(row.getCostUsd()).isEqualByComparingTo("0.000719");
    }

    @Test
    void failedDraftIsRecordedButDoesNotCountAgainstTheQuota() {
        when(speechTranscriber.transcribe(any(), anyString(), anyList()))
                .thenThrow(new VoiceProviderException(VoiceProviderException.Reason.UNAVAILABLE, "Mistral 503"));

        ResponseEntity<String> response = post(request(user, car.getId(), new byte[]{1}, "audio/mp4", "Europe/Berlin"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("VOICE_UNAVAILABLE");
        assertThat(voiceDraftRepository.countSuccessfulSince(user.getId(), LocalDateTime.now().minusDays(1))).isZero();
        assertThat(voiceDraftRepository.findAll()).anyMatch(r -> r.getUserId().equals(user.getId()) && !r.isSuccess());
    }

    @Test
    void emptyTranscriptIsUnprocessable() {
        when(speechTranscriber.transcribe(any(), anyString(), anyList())).thenReturn(new Transcript("", T_USAGE));

        ResponseEntity<String> response = post(request(user, car.getId(), new byte[]{1}, "audio/mp4", "Europe/Berlin"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("VOICE_NOT_UNDERSTOOD");
    }

    @Test
    void foreignCarIsRejected() {
        User other = createAndSaveAdminUser("voice-other-" + UUID.randomUUID() + "@test.local");

        ResponseEntity<String> response = post(request(other, car.getId(), new byte[]{1}, "audio/webm", "Europe/Berlin"));

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        verify(speechTranscriber, never()).transcribe(any(), anyString(), anyList());
    }

    @Test
    void unsupportedMimeTypeIsRejected() {
        ResponseEntity<String> response = post(request(user, car.getId(), new byte[]{1}, "video/mp4", "Europe/Berlin"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        verify(speechTranscriber, never()).transcribe(any(), anyString(), anyList());
    }

    @Test
    void audioAboveTwoMegabytesIsRejected() {
        ResponseEntity<String> response = post(request(user, car.getId(), new byte[2 * 1024 * 1024 + 1], "audio/webm", "Europe/Berlin"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        verify(speechTranscriber, never()).transcribe(any(), anyString(), anyList());
    }

    @Test
    void missingOrInvalidTimeZoneIsABadRequest() {
        assertThat(post(request(user, car.getId(), new byte[]{1}, "audio/webm", null)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(request(user, car.getId(), new byte[]{1}, "audio/webm", "Mars/Olympus")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void regularUsersRecordWithinTheFreeQuota() throws Exception {
        User regular = createAndSaveUser("voice-regular-" + UUID.randomUUID() + "@test.local");
        Car own = createAndSaveCar(regular.getId(), CarBrand.CarModel.MODEL_3);

        ResponseEntity<String> response = post(request(regular, own.getId(), new byte[]{1}, "audio/webm", "Europe/Berlin"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode usage = objectMapper.readTree(response.getBody()).path("usage");
        // Erster Monat: 10, eine davon ist verbraucht
        assertThat(usage.path("plan").asText()).isEqualTo("free");
        assertThat(usage.path("limit").asInt()).isEqualTo(10);
        assertThat(usage.path("remaining").asInt()).isEqualTo(9);
    }

    @Test
    void exhaustedQuotaIsTooManyRequestsWithResetDate() {
        for (int i = 0; i < 60; i++) {
            assertThat(post(request(user, car.getId(), new byte[]{1}, "audio/webm", "Europe/Berlin")).getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<String> response = post(request(user, car.getId(), new byte[]{1}, "audio/webm", "Europe/Berlin"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).contains("VOICE_LIMIT_REACHED", "resetsOn", "\"plan\":\"admin\"").doesNotContain("\"limit\"");
    }

    private ResponseEntity<String> getQuota(User who, String zone) {
        HttpHeaders headers = who == null ? new HttpHeaders() : createAuthHeaders(who.getId(), who.getEmail());
        return restTemplate.exchange("/api/logs/voice-quota?timeZone=" + zone, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void quotaShowsTheStandBeforeTheFirstRecording() throws Exception {
        post(request(user, car.getId(), new byte[]{1}, "audio/webm", "Europe/Berlin"));

        ResponseEntity<String> response = getQuota(user, "Europe/Berlin");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("plan").asText()).isEqualTo("admin");
        assertThat(body.path("limit").isNull()).isTrue();
        assertThat(body.path("exhausted").asBoolean()).isFalse();
        assertThat(body.path("resetsOn").asText()).isNotBlank();
    }

    @Test
    void quotaNeedsLoginAndAValidZone() throws Exception {
        User regular = createAndSaveUser("voice-quota-" + UUID.randomUUID() + "@test.local");

        assertThat(getQuota(null, "Europe/Berlin").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getQuota(user, "Mars/Olympus").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<String> free = getQuota(regular, "Europe/Berlin");
        assertThat(free.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(free.getBody()).path("plan").asText()).isEqualTo("free");
    }
}
