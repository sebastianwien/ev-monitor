package com.evmonitor.application;

import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.TripStory;
import com.evmonitor.domain.TripStoryRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Trip-Stories: user-authored public travel reports with embedded trip widgets.
 *
 * Core invariant: trip-widget stats are snapshotted server-side from the DB at save time.
 * Client-supplied numbers are never accepted, and the public read path serves only the
 * stored JSON - it never joins back into ev_trip. See docs/features/trip-stories.md.
 */
@Service
@RequiredArgsConstructor
public class TripStoryService {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("de", "en", "nb", "sv");
    private static final String DEFAULT_LANGUAGE = "de";
    private static final int MAX_MARKDOWN_LENGTH = 20_000;
    private static final int MAX_LABEL_LENGTH = 120;
    private static final int MAX_BLOCKS = 100;
    private static final int MAX_SLUG_BASE_LENGTH = 80;
    private static final int PUBLIC_LIST_SIZE = 20;
    private static final String SLUG_SUFFIX_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TripStoryRepository storyRepository;
    private final EvTripRepository tripRepository;
    private final UserRepository userRepository;
    private final TripService tripService;
    private final ObjectMapper objectMapper;

    @Transactional
    public TripStoryResponse createStory(User user, SaveTripStoryRequest request) {
        String title = requireTitle(request.title());
        TripStory story = TripStory.builder()
                .userId(user.getId())
                .title(title)
                .slug(generateUniqueSlug(title))
                .summary(normalizeSummary(request.summary()))
                .language(normalizeLanguage(request.language()))
                .status(TripStory.STATUS_DRAFT)
                .blocks(buildBlocksJson(user, request.blocks()))
                .build();
        return TripStoryResponse.fromDomain(storyRepository.save(story), objectMapper);
    }

    @Transactional(readOnly = true)
    public List<TripStoryResponse> getMyStories(User user) {
        return storyRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(story -> TripStoryResponse.fromDomain(story, objectMapper))
                .toList();
    }

    @Transactional(readOnly = true)
    public TripStoryResponse getStoryForEdit(UUID id, User user) {
        return TripStoryResponse.fromDomain(loadOwnedStory(id, user), objectMapper);
    }

    @Transactional
    public TripStoryResponse updateStory(UUID id, User user, SaveTripStoryRequest request) {
        TripStory story = loadOwnedStory(id, user);
        story.setTitle(requireTitle(request.title()));
        story.setSummary(normalizeSummary(request.summary()));
        story.setLanguage(normalizeLanguage(request.language()));
        story.setBlocks(buildBlocksJson(user, request.blocks()));
        story.setUpdatedAt(OffsetDateTime.now());
        return TripStoryResponse.fromDomain(storyRepository.save(story), objectMapper);
    }

    @Transactional
    public TripStoryResponse publish(UUID id, User user) {
        TripStory story = loadOwnedStory(id, user);
        if (parseBlocks(story.getBlocks(), objectMapper).isEmpty()) {
            throw new IllegalArgumentException("Cannot publish a story without content");
        }
        story.setStatus(TripStory.STATUS_PUBLISHED);
        if (story.getPublishedAt() == null) {
            story.setPublishedAt(OffsetDateTime.now());
        }
        return TripStoryResponse.fromDomain(storyRepository.save(story), objectMapper);
    }

    @Transactional
    public TripStoryResponse unpublish(UUID id, User user) {
        TripStory story = loadOwnedStory(id, user);
        story.setStatus(TripStory.STATUS_DRAFT);
        return TripStoryResponse.fromDomain(storyRepository.save(story), objectMapper);
    }

    @Transactional
    public void deleteStory(UUID id, User user) {
        storyRepository.delete(loadOwnedStory(id, user));
    }

    @Transactional(readOnly = true)
    public Optional<PublicTripStoryResponse> getPublicStory(String slug) {
        return storyRepository.findBySlugAndStatus(slug, TripStory.STATUS_PUBLISHED)
                .map(story -> PublicTripStoryResponse.builder()
                        .title(story.getTitle())
                        .slug(story.getSlug())
                        .summary(story.getSummary())
                        .language(story.getLanguage())
                        .authorUsername(usernameOf(story.getUserId()))
                        .publishedAt(story.getPublishedAt())
                        .blocks(parseBlocks(story.getBlocks(), objectMapper))
                        .build());
    }

    @Transactional(readOnly = true)
    public List<PublicTripStorySummaryResponse> getLatestPublicStories() {
        List<TripStory> stories = storyRepository.findByStatusOrderByPublishedAtDesc(
                TripStory.STATUS_PUBLISHED, PageRequest.of(0, PUBLIC_LIST_SIZE));
        Map<UUID, String> usernames = userRepository
                .findAllByIds(stories.stream().map(TripStory::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        return stories.stream()
                .map(story -> PublicTripStorySummaryResponse.builder()
                        .title(story.getTitle())
                        .slug(story.getSlug())
                        .summary(story.getSummary())
                        .language(story.getLanguage())
                        .authorUsername(usernames.getOrDefault(story.getUserId(), ""))
                        .publishedAt(story.getPublishedAt())
                        .build())
                .toList();
    }

    // -------------------------------------------------------------------------
    // Block pipeline
    // -------------------------------------------------------------------------

    private String buildBlocksJson(User user, List<TripStoryBlockRequest> blocks) {
        ArrayNode result = objectMapper.createArrayNode();
        if (blocks == null) return result.toString();
        if (blocks.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("Too many blocks (max " + MAX_BLOCKS + ")");
        }
        for (TripStoryBlockRequest block : blocks) {
            String type = block.type();
            if ("text".equals(type)) {
                result.add(buildTextBlock(block));
            } else if ("tripStats".equals(type)) {
                result.add(buildTripStatsBlock(user, block));
            } else {
                throw new IllegalArgumentException("Unknown block type: " + type);
            }
        }
        return result.toString();
    }

    private ObjectNode buildTextBlock(TripStoryBlockRequest block) {
        String markdown = block.markdown();
        if (markdown == null || markdown.length() > MAX_MARKDOWN_LENGTH) {
            throw new IllegalArgumentException("Text block must have markdown of at most "
                    + MAX_MARKDOWN_LENGTH + " characters");
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "text");
        node.put("markdown", markdown);
        return node;
    }

    /**
     * Privacy by construction: the snapshot contains numbers and timestamps only -
     * geohashes / coordinates from ev_trip never enter the story JSON. Location naming
     * is the user-supplied free-text label.
     */
    private ObjectNode buildTripStatsBlock(User user, TripStoryBlockRequest block) {
        if (block.tripId() == null) {
            throw new IllegalArgumentException("tripStats block requires a tripId");
        }
        EvTrip trip = tripRepository.findById(block.tripId())
                .filter(t -> t.getUserId().equals(user.getId()))
                .filter(t -> t.getDeletedAt() == null)
                .filter(t -> tripService.userCanSeeTrip(t, user))
                .orElseThrow(() -> new IllegalArgumentException("Trip not found"));

        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "tripStats");
        node.put("tripId", trip.getId().toString());
        String label = block.label() == null ? null : block.label().trim();
        if (label != null && !label.isEmpty()) {
            if (label.length() > MAX_LABEL_LENGTH) {
                throw new IllegalArgumentException("Label too long (max " + MAX_LABEL_LENGTH + ")");
            }
            node.put("label", label);
        }

        ObjectNode stats = node.putObject("stats");
        if (trip.getDistanceKm() != null) stats.put("distanceKm", trip.getDistanceKm());
        if (trip.getTripStartedAt() != null && trip.getTripEndedAt() != null) {
            stats.put("durationMinutes",
                    Duration.between(trip.getTripStartedAt(), trip.getTripEndedAt()).toMinutes());
        }
        if (trip.getEstimatedConsumedKwh() != null) stats.put("consumedKwh", trip.getEstimatedConsumedKwh());
        if (trip.getAvgSpeedKmh() != null) stats.put("avgSpeedKmh", trip.getAvgSpeedKmh());
        if (trip.getMaxSpeedKmh() != null) stats.put("maxSpeedKmh", trip.getMaxSpeedKmh());
        if (trip.getSocStart() != null) stats.put("socStart", trip.getSocStart());
        if (trip.getSocEnd() != null) stats.put("socEnd", trip.getSocEnd());
        if (trip.getOutsideTempCelsius() != null) stats.put("outsideTempCelsius", trip.getOutsideTempCelsius());
        if (trip.getTripStartedAt() != null) stats.put("startedAt", trip.getTripStartedAt().toString());
        if (trip.getTripEndedAt() != null) stats.put("endedAt", trip.getTripEndedAt().toString());
        return node;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TripStory loadOwnedStory(UUID id, User user) {
        return storyRepository.findById(id)
                .filter(story -> story.getUserId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Story not found"));
    }

    private String usernameOf(UUID userId) {
        return userRepository.findById(userId).map(User::getUsername).orElse("");
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title must not be blank");
        }
        String trimmed = title.trim();
        if (trimmed.length() > 160) {
            throw new IllegalArgumentException("Title too long (max 160)");
        }
        return trimmed;
    }

    private static String normalizeSummary(String summary) {
        if (summary == null || summary.isBlank()) return null;
        String trimmed = summary.trim();
        if (trimmed.length() > 300) {
            throw new IllegalArgumentException("Summary too long (max 300)");
        }
        return trimmed;
    }

    private static String normalizeLanguage(String language) {
        return language != null && SUPPORTED_LANGUAGES.contains(language) ? language : DEFAULT_LANGUAGE;
    }

    private String generateUniqueSlug(String title) {
        String base = slugify(title);
        for (int attempt = 0; attempt < 10; attempt++) {
            String slug = base.isEmpty() ? "story-" + randomSuffix() : base + "-" + randomSuffix();
            if (!storyRepository.existsBySlug(slug)) {
                return slug;
            }
        }
        throw new IllegalStateException("Could not generate a unique story slug");
    }

    static String slugify(String title) {
        String s = title.toLowerCase()
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return s.length() > MAX_SLUG_BASE_LENGTH ? s.substring(0, MAX_SLUG_BASE_LENGTH).replaceAll("-+$", "") : s;
    }

    private static String randomSuffix() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(SLUG_SUFFIX_CHARS.charAt(RANDOM.nextInt(SLUG_SUFFIX_CHARS.length())));
        }
        return sb.toString();
    }

    static JsonNode parseBlocks(String blocksJson, ObjectMapper objectMapper) {
        try {
            return objectMapper.readTree(blocksJson == null ? "[]" : blocksJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt story blocks JSON", e);
        }
    }
}
