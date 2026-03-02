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

  // Conversion Events
  trackRegistrationCompleted() {
    this.track('registration_completed')
  }

  trackEmailVerified() {
    this.track('email_verified')
  }
}

export const analytics = new AnalyticsService()
