package com.evmonitor.testutil;

import com.evmonitor.domain.*;
import com.evmonitor.application.LiveChargingService;
import com.evmonitor.application.NearbyCpoService;
import com.evmonitor.application.tariff.crawl.TariffCrawlScheduler;
import com.evmonitor.application.tariff.crawl.TariffExtractor;
import com.evmonitor.application.tariff.crawl.TariffPageFetcher;
import com.evmonitor.application.voice.LogDraftExtractor;
import com.evmonitor.application.voice.SpeechTranscriber;
import com.evmonitor.domain.weather.TemperatureEnricher;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.external.SpritMonitorClient;
import com.evmonitor.infrastructure.external.StripeReportClient;
import com.evmonitor.infrastructure.external.audit.SiteCrawler;
import com.evmonitor.infrastructure.external.gsc.GscClient;
import com.evmonitor.infrastructure.external.plausible.PlausibleClient;
import com.evmonitor.infrastructure.security.JwtService;
import com.evmonitor.infrastructure.security.RateLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Base class for integration tests.
 * Provides common setup and utility methods for testing with Spring Boot context.
 *
 * Key Features:
 * - Full Spring Boot context with RANDOM_PORT
 * - TestRestTemplate for HTTP requests
 * - All repositories available
 * - @Transactional for automatic rollback
 * - Helper methods for authenticated requests
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestAsyncConfig.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected CarRepository carRepository;

    @Autowired
    protected EvLogRepository evLogRepository;

    @Autowired
    protected VehicleSpecificationRepository vehicleSpecificationRepository;

    @Autowired
    protected CoinLogRepository coinLogRepository;

    @Autowired
    protected JwtService jwtService;

    // Externe Dienste zentral gemockt: Tests rufen nie echte APIs, und alle Unterklassen
    // teilen sich damit denselben Spring-Context. Eigene @MockitoBean-Felder in Unterklassen
    // erzeugen je einen zusätzlichen Context, TestContextGuardTest verhindert das.
    // Regel: externes I/O (HTTP, Mail, LLM) wird Mock, interne Dienste werden Spy, damit sie
    // ohne Stubbing das echte Verhalten behalten. Spring setzt beide nach jedem Test zurück.
    // Spies stubben mit doReturn(..).when(spy).methode(..), when(spy.methode()) ruft das Original.

    /** Mock: Spritmonitor-HTTP-Import. */
    @MockitoBean
    protected SpritMonitorClient spritMonitorClient;

    /** Mock: Stripe-Reporting-API. */
    @MockitoBean
    protected StripeReportClient stripeReportClient;

    /** Mock: Sprach-Transkription (Mistral). */
    @MockitoBean
    protected SpeechTranscriber speechTranscriber;

    /** Mock: Log-Entwurf aus Text (LLM). */
    @MockitoBean
    protected LogDraftExtractor logDraftExtractor;

    /** Mock: Mailversand, nie echte Mail im Test; prüfbar per verify(emailService, ...). */
    @MockitoBean
    protected EmailService emailService;

    /** Mock: Wetter-Anreicherung ruft Open-Meteo, in Tests ein No-op; nur Aufrufe sind prüfbar. */
    @MockitoBean
    protected TemperatureEnricher temperatureEnricher;

    /** Mock: externer Ladesäulen-Lookup. */
    @MockitoBean
    protected NearbyCpoService nearbyCpoService;

    /** Mock: Google Search Console. */
    @MockitoBean
    protected GscClient gscClient;

    /** Mock: Plausible-Analytics-API. */
    @MockitoBean
    protected PlausibleClient plausibleClient;

    /** Mock: SEO-Audit crawlt per HTTP. */
    @MockitoBean
    protected SiteCrawler siteCrawler;

    /** Mock: Tarifseiten per HTTP laden. */
    @MockitoBean
    protected TariffPageFetcher tariffPageFetcher;

    /** Mock: Tarifextraktion per LLM. */
    @MockitoBean
    protected TariffExtractor tariffExtractor;

    /** Mock: Scheduler löst sonst echte Crawls aus. */
    @MockitoBean
    protected TariffCrawlScheduler tariffCrawlScheduler;

    /** Spy: Rate-Limiting ist im Test-Profil aus, echtes Verhalten bleibt, einzelne Tests stubben. */
    @MockitoSpyBean
    protected RateLimitService rateLimitService;

    /** Mock: HTTP-Proxy zum Connectors-Dienst für Live-Laden. */
    @MockitoBean
    protected LiveChargingService liveChargingService;

    /**
     * Create HTTP headers with JWT Bearer token for authenticated requests.
     */
    protected HttpHeaders createAuthHeaders(UUID userId, String email) {
        String token = JwtTestUtils.generateValidToken(userId, email, jwtService);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    /**
     * Create HTTP entity with body and auth headers.
     */
    protected <T> HttpEntity<T> createAuthRequest(T body, UUID userId, String email) {
        return new HttpEntity<>(body, createAuthHeaders(userId, email));
    }

    /**
     * Create HTTP entity with only auth headers (for GET requests).
     */
    protected HttpEntity<Void> createAuthRequest(UUID userId, String email) {
        return new HttpEntity<>(createAuthHeaders(userId, email));
    }

    /**
     * Create a test user and save to database.
     */
    protected User createAndSaveUser(String email) {
        User user = TestDataBuilder.createTestUser(email);
        return userRepository.save(user);
    }

    protected User createUser(String email, String country) {
        User user = TestDataBuilder.createTestUser(email).toBuilder().country(country).build();
        return userRepository.save(user);
    }

    protected User createAndSavePremiumUser(String email) {
        User user = TestDataBuilder.createTestUser(email).toBuilder().premium(true).build();
        return userRepository.save(user);
    }

    /** Create a user on the AutoSync (Tier-1) plan - Sync ja, Premium-Analytics nein. */
    protected User createAndSaveAutoSyncUser(String email) {
        User user = TestDataBuilder.createTestUser(email).toBuilder()
                .subscriptionTier(com.evmonitor.domain.SubscriptionTier.AUTOSYNC)
                .build();
        return userRepository.save(user);
    }

    /**
     * Create a user on the AutoSync Live (Tier-2) plan - the entitlement that unlocks
     * trip CRUD on live-detected trips and manual trip creation.
     */
    protected User createAndSaveAutoSyncLiveUser(String email) {
        User user = TestDataBuilder.createTestUser(email).toBuilder()
                .subscriptionTier(com.evmonitor.domain.SubscriptionTier.AUTOSYNC_LIVE)
                .build();
        return userRepository.save(user);
    }

    /**
     * Create an ADMIN user - bypasses the car-model eligibility filter so live trips
     * of BLOCKED models can be audited via impersonation.
     */
    protected User createAndSaveAdminUser(String email) {
        User user = TestDataBuilder.createTestUser(email).toBuilder().role("ADMIN").build();
        return userRepository.save(user);
    }

    /**
     * Create a test car for a user and save to database.
     */
    protected Car createAndSaveCar(UUID userId, CarBrand.CarModel model) {
        Car car = TestDataBuilder.createTestCar(userId, model, BigDecimal.valueOf(75.0));
        return carRepository.save(car);
    }

    /** Spec-Zeile zum Modell; seit A2 braucht jedes neue Auto eine. Variante eindeutig je Aufruf. */
    protected VehicleSpecification createAndSaveSpec(CarBrand.CarModel model) {
        return vehicleSpecificationRepository.save(VehicleSpecification.createNew(
                model.getBrand().name(), model.name(), BigDecimal.valueOf(79.0),
                BigDecimal.valueOf(75.0), BigDecimal.valueOf(500), BigDecimal.valueOf(16.0),
                VehicleSpecification.WltpType.COMBINED,
                VehicleSpecification.RatingSource.WLTP, "Test " + System.nanoTime()));
    }
}
