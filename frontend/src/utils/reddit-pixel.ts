/**
 * Reddit Pixel Integration
 *
 * Client-Side Tracking für Reddit Ads mit DSGVO-konformem Consent-Management.
 * Pixel wird nur geladen wenn User über Reddit Paid Ad kommt UND consent gibt.
 */

// Global rdt function declaration
declare global {
  interface Window {
    rdt?: {
      (command: string, ...args: any[]): void;
      sendEvent?: (...args: any[]) => void;
      callQueue?: any[];
    };
  }
}

const REDDIT_PIXEL_ID = import.meta.env.VITE_REDDIT_PIXEL_ID || ''
const CONSENT_KEY = 'reddit-pixel-consent'
const UTM_PARAMS_KEY = 'utm-params'
const REFERRER_KEY = 'referrer-source'
const LANDING_PATH_KEY = 'landing-path'
/** Spaltenlänge app_user.landing_path. */
const LANDING_PATH_MAX = 200

function lsGet(key: string): string | null {
  try { return localStorage.getItem(key) } catch { return null }
}

function lsSet(key: string, value: string): void {
  try { localStorage.setItem(key, value) } catch { /* localStorage blocked */ }
}

function lsRemove(key: string): void {
  try { localStorage.removeItem(key) } catch { /* localStorage blocked */ }
}

/**
 * Speichert document.referrer (nur Hostname) beim ersten Seitenaufruf in localStorage.
 * Wird nur gesetzt wenn noch kein Wert vorhanden ist (first-touch attribution).
 * Muss beim ersten PageLoad aufgerufen werden (z.B. in App.vue).
 */
export function captureReferrer() {
  if (lsGet(REFERRER_KEY)) return // already captured
  const referrer = document.referrer
  if (!referrer) return
  try {
    const hostname = new URL(referrer).hostname
    if (hostname) {
      lsSet(REFERRER_KEY, hostname)
    }
  } catch {
    // invalid URL - ignore
  }
}

export function getStoredReferrer(): string | null {
  return lsGet(REFERRER_KEY)
}

export function clearStoredReferrer() {
  lsRemove(REFERRER_KEY)
}

/**
 * Merkt die erste besuchte Seite (nur Pfad, ohne Query und Hash) für die Attribution der
 * Registrierung. Nur beim ersten Seitenaufruf gesetzt (first touch), wie der Referrer.
 */
export function captureLandingPath() {
  if (lsGet(LANDING_PATH_KEY)) return
  const path = window.location.pathname
  if (!path) return
  lsSet(LANDING_PATH_KEY, path.slice(0, LANDING_PATH_MAX))
}

export function getStoredLandingPath(): string | null {
  return lsGet(LANDING_PATH_KEY)
}

export function clearStoredLandingPath() {
  lsRemove(LANDING_PATH_KEY)
}

/**
 * Herkunft für eine neue Registrierung: Referrer-Host und erste Seite aus dem ersten Besuch.
 * Leere Werte fehlen im Objekt, damit der Request sie nicht als "" sendet.
 */
export function firstTouchAttribution(): { referrerSource?: string; landingPath?: string } {
  return {
    referrerSource: getStoredReferrer() || undefined,
    landingPath: getStoredLandingPath() || undefined,
  }
}

/**
 * Speichert UTM-Parameter in localStorage wenn vorhanden.
 * Muss beim ersten PageLoad aufgerufen werden (z.B. in App.vue).
 */
export function captureUtmParams() {
  const params = new URLSearchParams(window.location.search)
  const utmSource = params.get('utm_source')
  const utmMedium = params.get('utm_medium')
  const utmCampaign = params.get('utm_campaign')

  if (utmSource || utmMedium || utmCampaign) {
    const utmData = {
      source: utmSource || '',
      medium: utmMedium || '',
      campaign: utmCampaign || ''
    }
    lsSet(UTM_PARAMS_KEY, JSON.stringify(utmData))
  }
}

/**
 * Holt gespeicherte UTM-Parameter aus localStorage.
 */
export function getStoredUtmParams(): { source: string; medium: string; campaign: string } | null {
  const stored = lsGet(UTM_PARAMS_KEY)
  if (!stored) return null
  try {
    return JSON.parse(stored)
  } catch {
    return null
  }
}

/**
 * UTM-Parameter für eine Anmeldung: die aktuelle URL gewinnt (neuester Kontakt), sonst die beim
 * ersten Besuch gespeicherten. null ohne Kampagne.
 */
export function currentUtmParams(): { source: string; medium: string; campaign: string } | null {
  const params = new URLSearchParams(window.location.search)
  const fromUrl = {
    source: params.get('utm_source') ?? '',
    medium: params.get('utm_medium') ?? '',
    campaign: params.get('utm_campaign') ?? '',
  }
  if (fromUrl.source || fromUrl.medium || fromUrl.campaign) return fromUrl
  return getStoredUtmParams()
}

/**
 * Löscht gespeicherte UTM-Parameter (nach erfolgreicher Registrierung).
 */
export function clearStoredUtmParams() {
  lsRemove(UTM_PARAMS_KEY)
}

/**
 * Prüft ob Cookie-Banner angezeigt werden soll.
 * Nur wenn User von Reddit Paid Ad kommt (utm_source=reddit, utm_medium=cpc).
 */
export function shouldShowCookieBanner(): boolean {
  const params = new URLSearchParams(window.location.search)

  // Nur bei Reddit Paid Ads (nicht bei organischen Reddit-Links)
  const isRedditAd = params.get('utm_source') === 'reddit'
                  && params.get('utm_medium') === 'cpc'

  // UTM-Parameter speichern für spätere Registrierung
  if (isRedditAd) {
    captureUtmParams()
  }

  return isRedditAd && !hasConsentDecision()
}

/**
 * Prüft ob User bereits eine Consent-Entscheidung getroffen hat.
 */
function hasConsentDecision(): boolean {
  const consent = lsGet(CONSENT_KEY)
  return consent === 'true' || consent === 'false'
}

/**
 * Prüft ob User Consent gegeben hat.
 */
export function hasRedditConsent(): boolean {
  return lsGet(CONSENT_KEY) === 'true'
}

/**
 * Setzt Consent und initialisiert ggf. Pixel.
 */
export function setRedditConsent(accepted: boolean) {
  lsSet(CONSENT_KEY, String(accepted))

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
  ;(function(w: any, d: Document) {
    if (!w.rdt) {
      var p: any = w.rdt = function() {
        p.sendEvent ? p.sendEvent.apply(p, arguments) : p.callQueue.push(arguments)
      }
      p.callQueue = []
      var t = d.createElement("script")
      t.src = "https://www.redditstatic.com/ads/pixel.js"
      t.async = true
      var s = d.getElementsByTagName("script")[0]
      s.parentNode!.insertBefore(t, s)
    }
  })(window, document)

  // Initialize pixel
  window.rdt!('init', REDDIT_PIXEL_ID, {
    optOut: false,
    useDecimalCurrencyValues: true
  })

  console.log('[Reddit Pixel] Initialized with ID:', REDDIT_PIXEL_ID)

  // Track initial page visit
  window.rdt!('track', 'PageVisit')
  console.log('[Reddit Pixel] PageVisit tracked')
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
