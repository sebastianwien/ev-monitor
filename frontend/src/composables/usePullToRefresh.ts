import { ref, onMounted, onUnmounted } from 'vue'
import { Capacitor } from '@capacitor/core'

/** Gedaempfte Distanz, ab der ein Reload ausgeloest wird. */
export const PULL_THRESHOLD_PX = 70

/** Gummiband-Daempfung: nur Abwaertsziehen zaehlt, und nie 1:1. */
const DAMPING = 0.5

/** Gibt die anzuzeigende (gedaempfte) Pull-Distanz zurueck. Aufwaerts/Null => 0. */
export function dampPull(rawDeltaPx: number): number {
  if (rawDeltaPx <= 0) return 0
  return rawDeltaPx * DAMPING
}

/** True, wenn weit genug gezogen wurde, um neu zu laden. */
export function shouldTriggerRefresh(dampedPx: number): boolean {
  return dampedPx >= PULL_THRESHOLD_PX
}

/**
 * Klassisches Pull-to-Refresh: am oberen Seitenanfang nach unten ziehen => Reload.
 *
 * Nur in der nativen App aktiv - die WKWebView hat im Gegensatz zum mobilen Browser
 * kein eigenes Pull-to-Refresh, hier wuerde es sonst fehlen. Auf Web/PWA uebernimmt
 * der Browser; dort bleibt das Composable inaktiv (keine Listener, kein Leak).
 *
 * @param reload Aktion zum Neuladen (Default: harter Seiten-Reload).
 */
export function usePullToRefresh(reload: () => void = () => window.location.reload()) {
  /** Gedaempfte Pull-Distanz in px fuer den Indikator (0 = nicht sichtbar). */
  const pull = ref(0)
  /** True waehrend des Reloads (Spinner). */
  const refreshing = ref(false)
  /** True sobald die Ausloese-Schwelle erreicht ist (UI-Feedback). */
  const armed = ref(false)

  let startY = 0
  let tracking = false

  function onStart(e: TouchEvent) {
    if (refreshing.value || window.scrollY > 0 || e.touches.length !== 1) {
      tracking = false
      return
    }
    tracking = true
    startY = e.touches[0].clientY
  }

  function onMove(e: TouchEvent) {
    if (!tracking || refreshing.value) return
    // Beim ersten Wegscrollen vom Top abbrechen.
    if (window.scrollY > 0) {
      tracking = false
      pull.value = 0
      armed.value = false
      return
    }
    const damped = dampPull(e.touches[0].clientY - startY)
    pull.value = damped
    armed.value = shouldTriggerRefresh(damped)
    // Native Gummiband unterdruecken, solange wir aktiv nach unten ziehen.
    if (damped > 0 && e.cancelable) e.preventDefault()
  }

  function onEnd() {
    if (!tracking) return
    tracking = false
    if (shouldTriggerRefresh(pull.value)) {
      refreshing.value = true
      // Kurz halten, damit der Spinner sichtbar ist, dann neu laden.
      window.setTimeout(reload, 300)
    } else {
      pull.value = 0
      armed.value = false
    }
  }

  onMounted(() => {
    if (!Capacitor.isNativePlatform()) return
    window.addEventListener('touchstart', onStart, { passive: true })
    window.addEventListener('touchmove', onMove, { passive: false })
    window.addEventListener('touchend', onEnd, { passive: true })
    window.addEventListener('touchcancel', onEnd, { passive: true })
  })

  onUnmounted(() => {
    window.removeEventListener('touchstart', onStart)
    window.removeEventListener('touchmove', onMove)
    window.removeEventListener('touchend', onEnd)
    window.removeEventListener('touchcancel', onEnd)
  })

  return { pull, refreshing, armed }
}
