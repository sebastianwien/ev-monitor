/**
 * Reddit Pixel Integration
 *
 * Client-Side Tracking für Reddit Ads mit DSGVO-konformem Consent-Management.
 * Pixel wird nur geladen wenn User über Reddit Paid Ad kommt UND consent gibt.
 */

// Global rdt function declaration
interface RedditPixelFunction {
  (command: string, ...args: any[]): void;
  sendEvent?: (command: string, ...args: any[]) => void;
  callQueue: any[];
}

declare global {
  interface Window {
    rdt?: RedditPixelFunction;
  }
}

const REDDIT_PIXEL_ID = import.meta.env.VITE_REDDIT_PIXEL_ID || ''
const CONSENT_KEY = 'reddit-pixel-consent'

/**
 * Prüft ob Cookie-Banner angezeigt werden soll.
 * Nur wenn User von Reddit Paid Ad kommt (utm_source=reddit, utm_medium=cpc).
 */
export function shouldShowCookieBanner(): boolean {
  const params = new URLSearchParams(window.location.search)

  // Nur bei Reddit Paid Ads (nicht bei organischen Reddit-Links)
  const isRedditAd = params.get('utm_source') === 'reddit'
                  && params.get('utm_medium') === 'cpc'

  return isRedditAd && !hasConsentDecision()
}

/**
 * Prüft ob User bereits eine Consent-Entscheidung getroffen hat.
 */
function hasConsentDecision(): boolean {
  const consent = localStorage.getItem(CONSENT_KEY)
  return consent === 'true' || consent === 'false'
}

/**
 * Prüft ob User Consent gegeben hat.
 */
export function hasRedditConsent(): boolean {
  return localStorage.getItem(CONSENT_KEY) === 'true'
}

/**
 * Setzt Consent und initialisiert ggf. Pixel.
 */
export function setRedditConsent(accepted: boolean) {
  localStorage.setItem(CONSENT_KEY, String(accepted))

  if (accepted) {
    initRedditPixel()
  }
}

/**
 * Initialisiert Reddit Pixel (nur mit Consent!).
 */
export function initRedditPixel() {
  if (!hasRedditConsent()) {
    console.log('[Reddit Pixel] Skipping initialization - no consent')
    return
  }

  if (!REDDIT_PIXEL_ID) {
    console.warn('[Reddit Pixel] VITE_REDDIT_PIXEL_ID not configured')
    return
  }

  if (window.rdt) {
    console.log('[Reddit Pixel] Already initialized')
    return
  }

  // Reddit Pixel Loader (official code from Reddit Ads Manager)
  !function(w: any, d: Document) {
    if (!w.rdt) {
      var p: any = w.rdt = function() {
        p.sendEvent ? p.sendEvent.apply(p, arguments) : p.callQueue.push(arguments)
      } as RedditPixelFunction
      p.callQueue = []
      var t = d.createElement("script")
      t.src = "https://www.redditstatic.com/ads/pixel.js"
      t.async = true
      var s = d.getElementsByTagName("script")[0]
      s.parentNode!.insertBefore(t, s)
    }
  }(window, document)

  // Initialize pixel
  window.rdt!('init', REDDIT_PIXEL_ID, {
    optOut: false,
    useDecimalCurrencyValues: true
  })

  // Track initial page visit
  window.rdt!('track', 'PageVisit')

  console.log('[Reddit Pixel] Initialized with ID:', REDDIT_PIXEL_ID)
}

/**
 * Trackt SignUp Conversion (nur mit Consent).
 */
export function trackRedditSignup() {
  if (!hasRedditConsent() || !window.rdt) {
    return
  }

  window.rdt!('track', 'SignUp')
  console.log('[Reddit Pixel] SignUp conversion tracked')
}

/**
 * Trackt Custom Event (nur mit Consent).
 */
export function trackRedditEvent(eventName: string, customData?: Record<string, any>) {
  if (!hasRedditConsent() || !window.rdt) {
    return
  }

  window.rdt!('track', eventName, customData)
  console.log('[Reddit Pixel] Custom event tracked:', eventName, customData)
}
