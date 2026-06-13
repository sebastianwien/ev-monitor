package com.evmonitor.application;

import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.TripStory;
import com.evmonitor.domain.TripStoryRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TripStoryService - core invariants:
 * trip-widget stats are snapshotted server-side from the DB (client numbers are never trusted),
 * ownership and live-trip visibility gates apply, and only published stories are publicly readable.
 */
@ExtendWith(MockitoExtension.class)
class TripStoryServiceTest {

    @Mock
    private TripStoryRepository storyRepository;
    @Mock
    private EvTripRepository tripRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TripService tripService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TripStoryService service;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new TripStoryService(storyRepository, tripRepository, userRepository, tripService, objectMapper);
        userId = UUID.randomUUID();
        user = mock(User.class);
        lenient().when(user.getId()).thenReturn(userId);
        lenient().when(storyRepository.save(any(TripStory.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(storyRepository.existsBySlug(any())).thenReturn(false);
    }

    private TripStory ownedDraft() {
        return TripStory.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title("Mein Trip")
                .slug("mein-trip-abc123")
                .language("de")
                .status(TripStory.STATUS_DRAFT)
                .blocks("[]")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private EvTrip ownedTrip(UUID tripId) {
        return EvTrip.builder()
                .id(tripId)
                .userId(userId)
                .carId(UUID.randomUUID())
                .dataSource("TESSIE")
                .status("COMPLETED")
                .tripStartedAt(OffsetDateTime.parse("2026-06-01T08:00:00Z"))
                .tripEndedAt(OffsetDateTime.parse("2026-06-01T16:30:00Z"))
                .distanceKm(new BigDecimal("837.4"))
                .estimatedConsumedKwh(new BigDecimal("121.30"))
                .avgSpeedKmh(new BigDecimal("98.10"))
                .maxSpeedKmh(new BigDecimal("132.00"))
                .socStart(new BigDecimal("100.00"))
                .socEnd(new BigDecimal("18.00"))
                .outsideTempCelsius(new BigDecimal("-4.0"))
                .build();
    }

    // -------------------------------------------------------------------------
    // createStory - slug generation
    // -------------------------------------------------------------------------

    @Test
    void createStory_generatesDraftWithSluggedTitle() {
        var response = service.createStory(user,
                new SaveTripStoryRequest("5.000 km im Inster: Helsinki & Nordkap!", null, "de", List.of()));

        assertThat(response.status()).isEqualTo(TripStory.STATUS_DRAFT);
        assertThat(response.slug()).matches("^5-000-km-im-inster-helsinki-nordkap-[a-z0-9]{6}$");
    }

    @Test
    void createStory_transliteratesGermanUmlauts() {
        var response = service.createStory(user,
                new SaveTripStoryRequest("Über die Älpen, größer als Öl", null, "de", List.of()));

        assertThat(response.slug()).matches("^ueber-die-aelpen-groesser-als-oel-[a-z0-9]{6}$");
    }

    @Test
    void createStory_rejectsBlankTitle() {
        assertThatThrownBy(() -> service.createStory(user,
                new SaveTripStoryRequest("   ", null, "de", List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createStory_fallsBackToDefaultLanguageForUnknownLocale() {
        var response = service.createStory(user,
                new SaveTripStoryRequest("Titel", null, "xx", List.of()));

        assertThat(response.language()).isEqualTo("de");
    }

    // -------------------------------------------------------------------------
    // updateStory - ownership + block validation
    // -------------------------------------------------------------------------

    @Test
    void updateStory_rejectsForeignStory() {
        TripStory story = ownedDraft();
        story.setUserId(UUID.randomUUID());
        when(storyRepository.findById(story.getId())).thenReturn(Optional.of(story));

        assertThatThrownBy(() -> service.updateStory(story.getId(), user,
                new SaveTripStoryRequest("Neu", null, "de", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Story not found");
    }

    @Test
    void updateStory_snapshotsTripStatsFromDatabase() throws Exception {
        TripStory story = ownedDraft();
        UUID tripId = UUID.randomUUID();
        when(storyRepository.findById(story.getId())).thenReturn(Optional.of(story));
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(ownedTrip(tripId)));
        when(tripService.userCanSeeTrip(any(), any())).thenReturn(true);

        service.updateStory(story.getId(), user, new SaveTripStoryRequest("Neu", null, "de", List.of(
                new TripStoryBlockRequest("text", "Tag 1: Abfahrt.", null, null),
                new TripStoryBlockRequest("tripStats", null, tripId, "Helsinki → Rovaniemi"))));

        ArgumentCaptor<TripStory> captor = ArgumentCaptor.forClass(TripStory.class);
        org.mockito.Mockito.verify(storyRepository).save(captor.capture());
        JsonNode blocks = objectMapper.readTree(captor.getValue().getBlocks());

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).get("markdown").asText()).isEqualTo("Tag 1: Abfahrt.");
        JsonNode stats = blocks.get(1).get("stats");
        assertThat(blocks.get(1).get("label").asText()).isEqualTo("Helsinki → Rovaniemi");
        assertThat(stats.get("distanceKm").decimalValue()).isEqualByComparingTo("837.4");
        assertThat(stats.get("consumedKwh").decimalValue()).isEqualByComparingTo("121.30");
        assertThat(stats.get("durationMinutes").asInt()).isEqualTo(510);
        assertThat(stats.get("socStart").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(stats.get("outsideTempCelsius").decimalValue()).isEqualByComparingTo("-4.0");
    }

    @Test
    void updateStory_rejectsForeignTrip() {
        TripStory story = ownedDraft();
        UUID tripId = UUID.randomUUID();
        EvTrip foreignTrip = ownedTrip(tripId);
        foreignTrip.setUserId(UUID.randomUUID());
        when(storyRepository.findById(story.getId())).thenReturn(Optional.of(story));
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(foreignTrip));

        assertThatThrownBy(() -> service.updateStory(story.getId(), user,
                new SaveTripStoryRequest("Neu", null, "de", List.of(
                        new TripStoryBlockRequest("tripStats", null, tripId, null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trip not found");
    }

    @Test
    void updateStory_rejectsTripTheUserCannotSee() {
        TripStory story = ownedDraft();
        UUID tripId = UUID.randomUUID();
        when(storyRepository.findById(story.getId())).thenReturn(Optional.of(story));
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(ownedTrip(tripId)));
        when(tripService.userCanSeeTrip(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.updateStory(story.getId(), user,
                new SaveTripStoryRequest("Neu", null, "de", List.of(
                        new TripStoryBlockRequest("tripStats", null, tripId, null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trip not found");
    }

    @Test
    void updateStory_rejectsDeletedTrip() {
        TripStory story = ownedDraft();
        UUID tripId = UUID.randomUUID();
        EvTrip deleted = ownedTrip(tripId);
        deleted.setDeletedAt(OffsetDateTime.now());
        when(storyRepository.findById(story.getId())).thenReturn(Optional.of(story));
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.updateStory(story.getId(), user,
                new SaveTripStoryRequest("Neu", null, "de", List.of(
                        new TripStoryBlockRequest("tripStats", null, tripId, null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trip not found");
    }

    @Test
    void updateStory_rejectsUnknownBlockType() {
        TripStory story = ownedDraft();
        when(storyRepository.findById(story.getId())).thenReturn(Optional.of(story));

        assertThatThrownBy(() -> service.updateStory(story.getId(), user,
                new SaveTripStoryRequest("Neu", null, "de", List.of(
                        new TripStoryBlockRequest("iframe", "<iframe>", null, null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateStory_rejectsOversizedMarkdownBlock() {
        TripStory story = ownedDraft();
        when(storyRepository.findById(story.getId())).thenReturn(Optional.of(story));

        assertThatThrownBy(() -> service.updateStory(story.getId(), user,
                new SaveTripStoryRequest("Neu", null, "de", List.of(
                        new TripStoryBlockRequest("text", "x".repeat(20_001), null, null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // publish / unpublish / delete
    // -------------------------------------------------------------------------

    @Test
    void publish_setsStatusAndPublishedAt() {
        TripStory story = ownedDraft();
        story.setBlocks("[{\"type\":\"text\",\"markdown\":\"Hallo\"}]");
        when(storyRepository.findById(story.getId())).thenReturn(Optional.of(story));

        var response = service.publish(story.getId(), user);

        assertThat(response.status()).isEqualTo(TripStory.STATUS_PUBLISHED);
        assertThat(response.publishedAt()).isNotNull();
    }

    @Test
    void publish_rejectsStoryWithoutBlocks() {
        TripStory story = ownedDraft();
        when(storyRepository.findById(story.getId())).thenReturn(Optional.of(story));

        assertThatThrownBy(() -> service.publish(story.getId(), user))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unpublish_revertsToDraft() {
        TripStory story = ownedDraft();
        story.setStatus(TripStory.STATUS_PUBLISHED);
        story.setPublishedAt(OffsetDateTime.now());
        story.setBlocks("[{\"type\":\"text\",\"markdown\":\"Hallo\"}]");
        when(storyRepository.findById(story.getId())).thenReturn(Optional.of(story));

        var response = service.unpublish(story.getId(), user);

        assertThat(response.status()).isEqualTo(TripStory.STATUS_DRAFT);
    }

    @Test
    void deleteStory_rejectsForeignStory() {
        TripStory story = ownedDraft();
        story.setUserId(UUID.randomUUID());
        when(storyRepository.findById(story.getId())).thenReturn(Optional.of(story));

        assertThatThrownBy(() -> service.deleteStory(story.getId(), user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Story not found");
    }

    // -------------------------------------------------------------------------
    // public read
    // -------------------------------------------------------------------------

    @Test
    void getPublicStory_mapsAuthorUsername() {
        TripStory story = ownedDraft();
        story.setStatus(TripStory.STATUS_PUBLISHED);
        story.setPublishedAt(OffsetDateTime.now());
        when(storyRepository.findBySlugAndStatus(story.getSlug(), TripStory.STATUS_PUBLISHED))
                .thenReturn(Optional.of(story));
        when(user.getUsername()).thenReturn("Ihle");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        var response = service.getPublicStory(story.getSlug());

        assertThat(response).isPresent();
        assertThat(response.get().authorUsername()).isEqualTo("Ihle");
        assertThat(response.get().slug()).isEqualTo(story.getSlug());
    }

    @Test
    void getPublicStory_returnsEmptyForUnknownSlug() {
        when(storyRepository.findBySlugAndStatus("nope", TripStory.STATUS_PUBLISHED))
                .thenReturn(Optional.empty());

        assertThat(service.getPublicStory("nope")).isEmpty();
    }
}
