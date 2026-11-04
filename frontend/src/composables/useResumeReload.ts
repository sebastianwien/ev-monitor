import { onMounted, onUnmounted } from 'vue'
import { runSoftReload } from './usePullToRefresh'

/** Ab dieser Zeit im Hintergrund lädt die offene Seite beim Zurückkommen neu. */
export const RESUME_RELOAD_MIN_MS = 15 * 60 * 1000

/** True nach langer Pause oder wenn seit dem Verlassen ein neuer Kalendertag begonnen hat. */
export function shouldReloadOnResume(hiddenAt: number | null, now: number): boolean {
  if (hiddenAt == null) return false
  if (now - hiddenAt >= RESUME_RELOAD_MIN_MS) return true
  return new Date(hiddenAt).toDateString() !== new Date(now).toDateString()
}

/**
 * Kommt die App aus dem Hintergrund zurück (iOS/Android halten die WebView, KeepAlive die Seite),
 * lädt die angemeldete Seite ihre Daten weich neu. Ohne Anmeldung passiert nichts, kein Seiten-Reload.
 */
export function useResumeReload() {
  let hiddenAt: number | null = null

  function onVisibilityChange() {
    if (document.visibilityState === 'hidden') {
      hiddenAt = Date.now()
      return
    }
    const reload = shouldReloadOnResume(hiddenAt, Date.now())
    hiddenAt = null
    if (reload) void runSoftReload().catch(() => {})
  }

  onMounted(() => document.addEventListener('visibilitychange', onVisibilityChange))
  onUnmounted(() => document.removeEventListener('visibilitychange', onVisibilityChange))
}
