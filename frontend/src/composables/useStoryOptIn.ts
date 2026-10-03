import { ref } from 'vue'
import { analytics } from '../services/analytics'

/**
 * Schalter "Neues Design" fuer das Story-Dashboard.
 *
 * Bewusst nur im localStorage: die Seite ist ein Experiment, der Schalter eine
 * Geraete-Einstellung. Wird daraus ein Standard, wandert er in die Dashboard-Preferences.
 */
export const STORY_OPT_IN_KEY = 'story_dashboard_opt_in'
export const STORY_TEASER_DISMISSED_KEY = 'story_teaser_dismissed'

function read(key: string): boolean {
  try { return localStorage.getItem(key) === 'true' } catch { return false }
}

function write(key: string, value: boolean) {
  try { localStorage.setItem(key, String(value)) } catch { /* privater Modus, egal */ }
}

const enabled = ref(read(STORY_OPT_IN_KEY))
const teaserDismissed = ref(read(STORY_TEASER_DISMISSED_KEY))

export function isStoryOptIn(): boolean {
  return read(STORY_OPT_IN_KEY)
}

export function useStoryOptIn() {
  // Beim Einhaengen frisch lesen: ein anderer Tab kann den Schalter geaendert haben.
  enabled.value = read(STORY_OPT_IN_KEY)
  teaserDismissed.value = read(STORY_TEASER_DISMISSED_KEY)
  function set(value: boolean) {
    enabled.value = value
    write(STORY_OPT_IN_KEY, value)
    analytics.track('story_opt_in', { enabled: value })
  }
  function dismissTeaser() {
    teaserDismissed.value = true
    write(STORY_TEASER_DISMISSED_KEY, true)
  }
  return { enabled, teaserDismissed, set, dismissTeaser }
}
