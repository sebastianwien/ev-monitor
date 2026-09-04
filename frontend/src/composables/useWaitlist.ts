import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import waitlistService from '../api/waitlistService'

/**
 * Per-User Opt-in auf die Warteliste eines Features. Kapselt Status-Laden,
 * Ein- und Austragen inkl. Lade-/Fehlerzustand fuer eine schlanke Teaser-UI.
 */
export function useWaitlist(feature: string) {
  const { t } = useI18n()

  const onWaitlist = ref(false)
  const since = ref<string | null>(null)
  const loaded = ref(false)
  const busy = ref(false)
  const error = ref<string | null>(null)

  async function load() {
    try {
      const s = await waitlistService.getStatus(feature)
      onWaitlist.value = s.onWaitlist
      since.value = s.since
    } catch {
      // Laden ist unkritisch: bei Fehler bleibt der Teaser im Default (nicht eingetragen).
    } finally {
      loaded.value = true
    }
  }

  async function join() {
    busy.value = true
    error.value = null
    try {
      const s = await waitlistService.join(feature)
      onWaitlist.value = s.onWaitlist
      since.value = s.since
    } catch {
      error.value = t('waitlist.error')
    } finally {
      busy.value = false
    }
  }

  async function leave() {
    busy.value = true
    error.value = null
    try {
      await waitlistService.leave(feature)
      onWaitlist.value = false
      since.value = null
    } catch {
      error.value = t('waitlist.error')
    } finally {
      busy.value = false
    }
  }

  return { onWaitlist, since, loaded, busy, error, load, join, leave }
}
