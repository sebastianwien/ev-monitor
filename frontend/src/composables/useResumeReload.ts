import { onMounted, onUnmounted } from 'vue'
import { runSoftReload, softReloadLoadedAt } from './usePullToRefresh'

/** Ab diesem Alter der Daten lädt die offene Seite beim Zurückkommen neu. */
export const RESUME_RELOAD_MIN_MS = 15 * 60 * 1000

/** True, wenn die Daten mindestens 15 Minuten alt oder von einem früheren Kalendertag sind. */
export function shouldReloadOnResume(loadedAt: number | null, now: number): boolean {
  if (loadedAt == null) return false
  if (now - loadedAt >= RESUME_RELOAD_MIN_MS) return true
  return new Date(loadedAt).toDateString() !== new Date(now).toDateString()
}

/**
 * Kommt die App aus dem Hintergrund zurück (iOS/Android halten die WebView, KeepAlive die Seite),
 * lädt die angemeldete Seite ihre Daten weich neu. Ohne Anmeldung passiert nichts, kein Seiten-Reload.
 */
export function useResumeReload() {
  function onVisibilityChange() {
    if (document.visibilityState !== 'visible') return
    if (shouldReloadOnResume(softReloadLoadedAt(), Date.now())) void runSoftReload().catch(() => {})
  }

  onMounted(() => document.addEventListener('visibilitychange', onVisibilityChange))
  onUnmounted(() => document.removeEventListener('visibilitychange', onVisibilityChange))
}
