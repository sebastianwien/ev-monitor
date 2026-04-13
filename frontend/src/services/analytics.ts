/**
 * DSGVO-compliant Analytics Service
 *
 * Uses Plausible.io (privacy-first, no cookies, no consent banner needed)
 *
 * Setup:
 * 1. Create account at https://plausible.io/
 * 2. Add domain (ev-monitor.net)
 * 3. Add script to index.html
 * 4. Events are automatically tracked
 */

declare global {
  interface Window {
    plausible?: (event: string, options?: { props?: Record<string, string | number | boolean> }) => void
  }
}

class AnalyticsService {
  /**
   * Track a custom event
   *
   * @param event Event name (e.g., 'onboarding_started')
   * @param props Optional properties (max 30 props, each max 300 chars)
   */
  track(event: string, props?: Record<string, string | number | boolean>): void {
    if (typeof window === 'undefined') return

    // Check if Plausible is loaded
    if (typeof window.plausible === 'function') {
      window.plausible(event, props ? { props } : undefined)
      console.log('📊 Analytics:', event, props)
    } else {
      // Fallback: Log to console in development
      if (import.meta.env.DEV) {
        console.log('📊 Analytics (dev):', event, props)
      }
    }
  }

  // Onboarding Events
  trackOnboardingStarted() {
    this.track('onboarding_started')
  }

  trackOnboardingStepViewed(step: number) {
    this.track('onboarding_step_viewed', { step })
  }

  trackOnboardingCompleted(completedSteps: number) {
    this.track('onboarding_completed', { completed_steps: completedSteps })
  }

  trackOnboardingSkipped(step: number) {
    this.track('onboarding_skipped', { step })
  }

  // User Journey Events
  trackFirstCarAdded() {
    this.track('first_car_added')
  }

  trackFirstLogCreated(source: 'manual' | 'ocr') {
    this.track('first_log_created', { source })
  }

  trackOcrScanStarted() {
    this.track('ocr_scan_started')
  }

  trackOcrScanCompleted(confidence: number, fieldsExtracted: number) {
    this.track('ocr_scan_completed', {
      confidence,
      fields_extracted: fieldsExtracted
    })
  }

  // Feature Usage
  trackFeatureUsed(feature: string) {
    this.track('feature_used', { feature })
  }

  trackDemoLoginClicked(source: 'hero' | 'models_section' | 'lp_v2_weiche' | 'lp_v2_owner') {
    this.track('demo_login_clicked', { source })
  }

  trackDemoSessionStarted(source: string) {
    this.track('demo_session_started', { source })
  }

  /** Persist demo source for cross-page tracking. Call after successful demo login. */
  setDemoContext(source: string) {
    sessionStorage.setItem('ev_demo_source', source)
  }

  /** Returns demo source if this is an active demo session, null otherwise. */
  getDemoContext(): string | null {
    return sessionStorage.getItem('ev_demo_source')
  }

  clearDemoContext() {
    sessionStorage.removeItem('ev_demo_source')
  }

  trackCtaRegisterClicked(source: string) {
    this.track('cta_register_clicked', { source })
  }

  trackCtaModelsClicked(source: string) {
    this.track('cta_models_clicked', { source })
  }

  trackLoginFailed(reason: string) {
    this.track('login_failed', { reason })
  }

  trackRegistrationFailed(reason: string) {
    this.track('registration_failed', { reason })
  }

  trackImportTabClicked(tab: string) {
    this.track('import_tab_clicked', { tab })
  }

  trackApiKeyCreated() {
    this.track('api_key_created')
  }

  trackApiKeyDeleted() {
    this.track('api_key_deleted')
  }

  // Auth Events
  trackLogin() {
    this.track('login')
  }

  trackRegistrationCompleted() {
    this.track('registration_completed')
  }

  trackEmailVerified() {
    this.track('email_verified')
  }

  trackPasswordResetRequested() {
    this.track('password_reset_requested')
  }

  // Car & Log Events
  trackCarAdded(isFirst: boolean) {
    this.track('car_added', { first_car: isFirst })
  }

  trackLogCreated(source: 'manual' | 'ocr', isFirst: boolean) {
    this.track('log_created', { source, first_log: isFirst })
  }

  // Premium Events
  trackUpgradePageViewed() {
    this.track('upgrade_page_viewed')
  }

  trackCheckoutStarted(plan: 'monthly' | 'yearly') {
    this.track('checkout_started', { plan })
  }

  trackCheckoutCompleted() {
    this.track('checkout_completed')
  }

  // Affiliate Events
  trackAffiliateBannerClicked(banner: 'thg' | 'enbw') {
    this.track('affiliate_banner_clicked', { banner })
  }
}

export const analytics = new AnalyticsService()
