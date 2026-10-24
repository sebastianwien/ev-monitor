import { ref } from 'vue'
import { DAY, type MomentSeen } from './storyMoments'

/**
 * Welche Moment-Heros schon gezeigt oder weggeklickt wurden. Nur auf dem Geraet, nichts geht an
 * den Server. Gezeigt bleibt ein Jahr (der Frost-Moment gilt je Saison), Wegklicken bis zum Ablauf.
 */
export const MOMENTS_SEEN_KEY = 'story_moments_seen'
const KEEP_SHOWN_MS = 365 * DAY

function prune(raw: unknown, now: number): MomentSeen {
  const out: MomentSeen = { shown: {}, dismissed: {} }
  const r = (raw ?? {}) as Partial<MomentSeen>
  for (const [k, t] of Object.entries(r.shown ?? {})) if (typeof t === 'number' && now - t <= KEEP_SHOWN_MS) out.shown[k] = t
  for (const [k, t] of Object.entries(r.dismissed ?? {})) if (typeof t === 'number' && t > now) out.dismissed[k] = t
  return out
}

function read(now: number): MomentSeen {
  try { return prune(JSON.parse(localStorage.getItem(MOMENTS_SEEN_KEY) || '{}'), now) } catch { return { shown: {}, dismissed: {} } }
}

function write(v: MomentSeen) {
  try { localStorage.setItem(MOMENTS_SEEN_KEY, JSON.stringify(v)) } catch { /* privater Modus, egal */ }
}

const seen = ref<MomentSeen>({ shown: {}, dismissed: {} })

export function useMomentSeen(now = Date.now()) {
  // Beim Einhaengen frisch lesen: ein anderer Tab kann etwas weggeklickt haben.
  seen.value = read(now)
  function markShown(key: string, at = Date.now()) {
    if (seen.value.shown[key] != null) return
    seen.value = { ...seen.value, shown: { ...seen.value.shown, [key]: at } }
    write(seen.value)
  }
  function dismiss(key: string, at: number, forMs: number) {
    seen.value = { ...seen.value, dismissed: { ...seen.value.dismissed, [key]: at + forMs } }
    write(seen.value)
  }
  return { seen, markShown, dismiss }
}
