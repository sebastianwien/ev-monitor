package com.evmonitor.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class User {
    private final UUID id;
    private final String email;
    private final String username;
    private final String passwordHash;
    private final AuthProvider authProvider;
    private final String role;
    private final boolean emailVerified;
    private final boolean seedData;
    private final boolean emailNotificationsEnabled;
    private final boolean premium;
    private final SubscriptionTier subscriptionTier;
    private final boolean referralRewardGiven;
    private final String referralCode;
    private final UUID referredByUserId;
    private final String stripeCustomerId;
    private final String utmSource;
    private final String utmMedium;
    private final String utmCampaign;
    private final String referrerSource;
    private final String registrationLocale;
    private final String country;
    private final Instant subscriptionPeriodEnd;
    private final Instant autosyncStartedAt;
    private final boolean trialUsed;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    @Builder(toBuilder = true)
    private User(UUID id, String email, String username, String passwordHash, AuthProvider authProvider, String role,
            boolean emailVerified, boolean seedData, boolean emailNotificationsEnabled, boolean premium,
            SubscriptionTier subscriptionTier,
            boolean referralRewardGiven, String referralCode, UUID referredByUserId, String stripeCustomerId,
            String utmSource, String utmMedium, String utmCampaign, String referrerSource,
            String registrationLocale, String country, Instant subscriptionPeriodEnd, Instant autosyncStartedAt,
            boolean trialUsed, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null)
            throw new IllegalArgumentException("User ID cannot be null");
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email cannot be empty");
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Username cannot be empty");
        if (authProvider == null)
            throw new IllegalArgumentException("Auth Provider cannot be null");

        // Reconcile legacy `premium` flag with new `subscriptionTier`. Tier is the
        // source of truth going forward; if only `premium` was set (older callers,
        // existing rows pre-V112), assume AUTOSYNC. If neither is set, default NONE.
        // toBuilder() copies tier=NONE from a freshly-built user, so a follow-up
        // .premium(true) would otherwise stay tier=NONE - upgrade it here too.
        if (subscriptionTier == null) {
            subscriptionTier = SubscriptionTier.NONE;
        }
        if (premium && subscriptionTier == SubscriptionTier.NONE) {
            subscriptionTier = SubscriptionTier.AUTOSYNC;
        }

        this.id = id;
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.authProvider = authProvider;
        this.role = role == null ? "USER" : role;
        this.emailVerified = emailVerified;
        this.seedData = seedData;
        this.emailNotificationsEnabled = emailNotificationsEnabled;
        this.subscriptionTier = subscriptionTier;
        // `premium` is the telemetry/AutoSync entitlement, not "is paying": SUPPORTER pays
        // for analytics only and must stay non-telemetry-premium (see canActivateTelemetry).
        this.premium = subscriptionTier.grantsTelemetry();
        this.referralRewardGiven = referralRewardGiven;
        this.referralCode = referralCode;
        this.referredByUserId = referredByUserId;
        this.stripeCustomerId = stripeCustomerId;
        this.utmSource = utmSource;
        this.utmMedium = utmMedium;
        this.utmCampaign = utmCampaign;
        this.referrerSource = referrerSource;
        this.registrationLocale = registrationLocale;
        this.country = country;
        this.subscriptionPeriodEnd = subscriptionPeriodEnd;
        this.autosyncStartedAt = autosyncStartedAt;
        this.trialUsed = trialUsed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Roles allowed to activate Live-Sync (Tesla Telemetry, Smartcar Webhooks) without an AutoSync subscription. */
    private static final java.util.Set<String> TELEMETRY_PRIVILEGED_ROLES =
            java.util.Set.of("ADMIN", "BETA_TESTER", "TESLA_FOUNDER");

    /**
     * Centralised gate for Live-Sync activation. Returns true if the user is allowed to
     * push a Telemetry config to their vehicle - either via a privileged role
     * (TESLA_FOUNDER grandfathering, BETA_TESTER, ADMIN) or via an active AutoSync
     * subscription. This is the paid path used by Smartcar (VW etc.).
     */
    public boolean canActivateTelemetry() {
        return premium || TELEMETRY_PRIVILEGED_ROLES.contains(role);
    }

    /**
     * Source-aware Live-Sync activation gate. Tesla Fleet-Telemetry data collection is
     * free for every Tesla driver, so {@link TelemetrySource#TESLA} activation is always
     * permitted. All other integrations ({@link TelemetrySource#SMARTCAR}) stay on the
     * paid {@link #canActivateTelemetry()} path.
     */
    public boolean canActivateTelemetry(TelemetrySource source) {
        return source == TelemetrySource.TESLA || canActivateTelemetry();
    }

    /**
     * Marken-aware gate for viewing live-detected trips (SMARTCAR_LIVE, TESLA_LIVE,
     * TESLA_INFERRED) in the dashboard. Trip detection is part of the free Tesla tier,
     * so Tesla cars see their live trips regardless of subscription; other brands stay
     * on the paid {@link #canViewLiveTripFeed()} gate. Tessie-imported trips bypass this
     * entirely (handled in TripService by data_source whitelist).
     *
     * <p>Note: the live trips themselves are free for Tesla, but the derived
     * <em>phantom-drain</em> and <em>energy-split</em> analytics stay premium - those are
     * gated separately on the display side via {@link #canViewLiveAnalytics()}.
     */
    public boolean canViewLiveTrips(CarBrand brand) {
        return brand == CarBrand.TESLA || canViewLiveTripFeed();
    }

    /**
     * Premium analytics gate (AUTOSYNC_LIVE subscription, ADMIN or BETA_TESTER). Unlike
     * {@link #canViewLiveTrips(CarBrand)} this has NO Tesla-free path - it gates the paid
     * analytics layer that stays premium for every brand: historical power curves,
     * phantom drain, and the energy-split breakdown.
     */
    /**
     * Gate fuer den Ladeverlauf - den gemessenen Ladestand ueber die Zeit, den
     * Quellen ohne Leistungsmessung (Smartcar) liefern.
     *
     * Bewusst weiter gefasst als {@link #canViewLiveAnalytics()}: der Verlauf ist
     * das, was diesen Nutzern statt einer Ladekurve bleibt, und der Datensatz
     * hinter AUTOSYNC entsteht ohnehin aus ihren eigenen Webhooks. Die
     * Leistungskurve bleibt davon unberuehrt und premium.
     */
    public boolean canViewSocCurve() {
        return subscriptionTier == SubscriptionTier.AUTOSYNC || canViewLiveAnalytics();
    }

    /**
     * True, solange das launch-verankerte Gratis-Fenster von {@code trial} den Nutzer
     * traegt (siehe {@link FeatureTrial}). Ohne bekanntes Registrierungsdatum gibt es kein
     * Trial - dann greift nur der bezahlte Gate der jeweiligen Kachel.
     */
    boolean isWithinTrial(FeatureTrial trial, LocalDate today) {
        LocalDate end = trialEndsAt(trial);
        return end != null && !today.isAfter(end);
    }

    /** Letzter Tag, an dem {@code trial} den Nutzer traegt - null ohne bekanntes Registrierungsdatum. */
    LocalDate trialEndsAt(FeatureTrial trial) {
        return createdAt == null ? null : trial.endFor(createdAt.toLocalDate());
    }

    /**
     * Gate fuer die Heimlade-Ersparnis auf dem Dashboard.
     *
     * Jeder bezahlte Tarif (die AutoSync-Leiter wie der dazu orthogonale SUPPORTER) und
     * die privilegierten Rollen sehen die Kachel dauerhaft. Zusaetzlich ist sie im
     * launch-verankerten Trial fuer alle offen (siehe
     * {@link FeatureTrial#HOME_CHARGING_SAVINGS}) - danach faellt sie auf den bezahlten
     * Gate zurueck. Das Gate ist die Sicherheitsgrenze: die Zahlen entstehen nur, wenn
     * dies true ist.
     */
    public boolean canViewChargingSavings() {
        return canViewChargingSavings(LocalDate.now());
    }

    boolean canViewChargingSavings(LocalDate today) {
        return subscriptionTier.isPaid()
                || TRIP_PUSH_PRIVILEGED_ROLES.contains(role)
                || isWithinTrial(FeatureTrial.HOME_CHARGING_SAVINGS, today);
    }

    /**
     * True, solange der Nutzer die Kachel ausschliesslich ueber das Trial sieht - also
     * weder zahlt noch eine privilegierte Rolle hat. Steuert den Retention-Hinweis; fuer
     * zahlende Nutzer bleibt er aus, sie verlieren die Kachel nicht.
     */
    public boolean isChargingSavingsViaTrial() {
        return isChargingSavingsViaTrial(LocalDate.now());
    }

    boolean isChargingSavingsViaTrial(LocalDate today) {
        return !subscriptionTier.isPaid()
                && !TRIP_PUSH_PRIVILEGED_ROLES.contains(role)
                && isWithinTrial(FeatureTrial.HOME_CHARGING_SAVINGS, today);
    }

    /** Letzter Tag, an dem das Ersparnis-Trial die Kachel traegt - null ohne Registrierungsdatum. */
    public LocalDate savingsTrialEndsAt() {
        return trialEndsAt(FeatureTrial.HOME_CHARGING_SAVINGS);
    }

    /**
     * Paid analytics layer (historical power curves / Ladekurven, phantom drain,
     * energy-split), for every brand - kein Tesla-Gratis-Pfad. Im Zwei-Tier-Zielbild
     * bekommt jeder bezahlte Tarif die Auswertungen: AUTOSYNC (Nicht-Tesla-Datenquelle)
     * und SUPPORTER (Tesla, Daten laufen gratis) gleichermassen, dazu AUTOSYNC_LIVE und
     * ADMIN/BETA_TESTER.
     *
     * <p>Bewusst breiter als {@link #canViewLiveTripFeed()}: die Auswertung vorhandener
     * Daten haengt an diesem Gate, der Live-Trip-Feed (Trip-Push) am schmaleren - plain
     * AUTOSYNC schaltet die Analytics frei, aber nicht den Trip-Push (dessen Tarif-Zuordnung
     * mit der AUTOSYNC_LIVE-Abloesung noch offen ist).
     */
    public boolean canViewLiveAnalytics() {
        return subscriptionTier == SubscriptionTier.AUTOSYNC
                || canViewLiveTripFeed();
    }

    /**
     * Schmalerer Gate fuer den telemetrie-gespeisten Live-Trip-Feed (Trip-Push). Bewusst
     * OHNE plain AUTOSYNC: der bekommt die Auswertungen ({@link #canViewLiveAnalytics()}),
     * aber nicht den Trip-Push, solange dessen Zuordnung nach dem AUTOSYNC_LIVE-Wegfall
     * nicht entschieden ist. Entspricht dem Verhalten vor dem AUTOSYNC-Analytics-Ausbau.
     */
    private boolean canViewLiveTripFeed() {
        return subscriptionTier == SubscriptionTier.AUTOSYNC_LIVE
                || subscriptionTier == SubscriptionTier.SUPPORTER
                || TRIP_PUSH_PRIVILEGED_ROLES.contains(role);
    }

    /**
     * Gate for the dashboard Live-Charging card (current power, SoC, ETA, history).
     * Marken-aware: free for Tesla drivers (data collection and the live card are part
     * of the free Tesla tier), otherwise reserved for paying AUTOSYNC_LIVE subscribers
     * and ADMINs. BETA_TESTERs without Live tier are excluded for non-Tesla cars so the
     * card stays a paid-feature preview there.
     *
     * <p>Ownership ({@code car.userId == user.id}) is a separate concern enforced at the
     * call site BEFORE this entitlement check.
     */
    public boolean canViewLiveCharging(CarBrand brand) {
        return brand == CarBrand.TESLA
                || subscriptionTier == SubscriptionTier.AUTOSYNC_LIVE
                || "ADMIN".equals(role);
    }

    /**
     * True if the user is allowed to see live-detected trips even for car models that
     * are NOT on the live-trip eligibility whitelist (see {@code TripDetectionEligibility}).
     * Granted to ADMIN + BETA_TESTER for data-quality audit via impersonation.
     * AutoSync-Live customers stay bound to the whitelist - if their car model is
     * BLOCKED, live trips are silently filtered out of their UI even though they pay
     * for Tier-2. The data itself is still collected in the DB so eligibility can be
     * flipped retroactively when OEM data quality improves.
     */
    public boolean canBypassEligibilityGate() {
        return ELIGIBILITY_BYPASS_ROLES.contains(role);
    }

    /** Roles that bypass the car-model eligibility filter (audit access only). */
    private static final java.util.Set<String> ELIGIBILITY_BYPASS_ROLES =
            java.util.Set.of("ADMIN", "BETA_TESTER");

    /** Roles that always stream the FULL profile regardless of subscription tier. */
    private static final java.util.Set<String> FULL_PROFILE_PRIVILEGED_ROLES =
            java.util.Set.of("ADMIN", "BETA_TESTER");

    /** Roles that may push trips/phantom-drain regardless of subscription tier. */
    private static final java.util.Set<String> TRIP_PUSH_PRIVILEGED_ROLES =
            java.util.Set.of("ADMIN", "BETA_TESTER");

    /**
     * Telemetry profile this user is entitled to. Drives the connectors-service
     * profile push. Caller MUST have already verified {@link #canActivateTelemetry()}.
     *
     * <p>Tesla Fleet-Telemetry data collection is free for all Tesla drivers, so every
     * Tesla connection streams the {@link TelemetryProfile#FULL} field set regardless of
     * tier or role. The paid AutoSync Live tier no longer gates <em>data ingestion</em> -
     * it gates the <em>analytics views</em> (historical power curves, phantom drain,
     * energy split), while the live-charging card is free for Tesla via
     * {@link #canViewLiveCharging(CarBrand)}.
     */
    public TelemetryProfile preferredTelemetryProfile() {
        return TelemetryProfile.FULL;
    }

    /**
     * True if the user may push live trips and phantom-drain reports. Distinct
     * from {@link #canActivateTelemetry()} which only gates "may stream charging
     * data at all". TESLA_FOUNDER grandfathering does NOT cover trip-push.
     */
    public boolean canUseTripPush() {
        return subscriptionTier == SubscriptionTier.AUTOSYNC_LIVE
                || TRIP_PUSH_PRIVILEGED_ROLES.contains(role);
    }

    private static String generateReferralCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    public static User createNewLocalUser(String email, String username, String passwordHash) {
        return newLocalUserBuilder(email, username, passwordHash).build();
    }

    public static User createNewLocalUserWithReferrer(String email, String username, String passwordHash, UUID referredByUserId) {
        return newLocalUserBuilder(email, username, passwordHash)
                .referredByUserId(referredByUserId)
                .build();
    }

    public static User createNewLocalUserWithCampaign(String email, String username, String passwordHash,
            UUID referredByUserId, String utmSource, String utmMedium, String utmCampaign, String referrerSource) {
        return newLocalUserBuilder(email, username, passwordHash)
                .referredByUserId(referredByUserId)
                .utmSource(utmSource).utmMedium(utmMedium).utmCampaign(utmCampaign).referrerSource(referrerSource)
                .build();
    }

    public static User createNewLocalUserWithLocale(String email, String username, String passwordHash,
            UUID referredByUserId, String utmSource, String utmMedium, String utmCampaign, String referrerSource,
            String registrationLocale) {
        return newLocalUserBuilder(email, username, passwordHash)
                .referredByUserId(referredByUserId)
                .utmSource(utmSource).utmMedium(utmMedium).utmCampaign(utmCampaign).referrerSource(referrerSource)
                .registrationLocale(registrationLocale)
                .build();
    }

    public static User createNewLocalUserWithLocaleAndCountry(String email, String username, String passwordHash,
            UUID referredByUserId, String utmSource, String utmMedium, String utmCampaign, String referrerSource,
            String registrationLocale, String country) {
        return newLocalUserBuilder(email, username, passwordHash)
                .referredByUserId(referredByUserId)
                .utmSource(utmSource).utmMedium(utmMedium).utmCampaign(utmCampaign).referrerSource(referrerSource)
                .registrationLocale(registrationLocale).country(country)
                .build();
    }

    public static User createVerifiedLocalUser(String email, String username, String passwordHash) {
        return newLocalUserBuilder(email, username, passwordHash)
                .emailVerified(true)
                .build();
    }

    public static User createNewSsoUser(String email, String username, AuthProvider authProvider) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .id(UUID.randomUUID())
                .email(email).username(username)
                .authProvider(authProvider).role("USER")
                .emailVerified(true).emailNotificationsEnabled(true)
                .referralCode(generateReferralCode())
                .createdAt(now).updatedAt(now)
                .build();
    }

    private static UserBuilder newLocalUserBuilder(String email, String username, String passwordHash) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .id(UUID.randomUUID())
                .email(email).username(username).passwordHash(passwordHash)
                .authProvider(AuthProvider.LOCAL).role("USER")
                .emailNotificationsEnabled(true)
                .referralCode(generateReferralCode())
                .createdAt(now).updatedAt(now);
    }
}
