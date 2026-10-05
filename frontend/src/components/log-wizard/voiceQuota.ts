import type { VoiceUsage } from './wizardLogic'
import type { VoiceProblem } from './voiceProblem'

/** Kontingent des Sprachlogs, Stand von GET /logs/voice-quota. free 5 (erster Monat 10), paid 30, admin still 60. */
export type VoicePlan = 'free' | 'paid' | 'admin'
export interface VoiceQuota { plan: VoicePlan; limit: number | null; remaining: number | null; exhausted: boolean; resetsOn: string }

/** Hinweis unter dem Mikrofon. Upgrade nur für Free; vorher Ruhe, damit die ersten Aufnahmen ungestört bleiben. */
export type QuotaNotice =
  | { kind: 'low'; remaining: number; limit: number; upsell: boolean }
  | { kind: 'out'; limit: number | null; resetsOn: string; upsell: boolean }

/** Ab hier zeigt die Box, wie viele noch übrig sind. Bei Supporter erst ab 25 von 30. */
const LOW_AT: Record<VoicePlan, number> = { free: 2, paid: 5, admin: 0 }

export function quotaNotice(q: VoiceQuota | null): QuotaNotice | null {
  if (!q) return null
  const upsell = q.plan === 'free'
  if (q.exhausted) return { kind: 'out', limit: q.limit, resetsOn: q.resetsOn, upsell }
  if (q.limit == null || q.remaining == null || q.remaining > LOW_AT[q.plan]) return null
  return { kind: 'low', remaining: q.remaining, limit: q.limit, upsell }
}

/** Stand nach einer Aufnahme aus der Antwort von POST /logs/voice-draft. */
export function quotaFromUsage(u: VoiceUsage): VoiceQuota {
  return { ...u, exhausted: u.remaining === 0 }
}

/** Antwort 429 VOICE_LIMIT_REACHED: aufgebraucht, auch wenn der Stand vorher anders aussah. */
export function quotaFromLimit(p: Extract<VoiceProblem, { kind: 'limit' }>): VoiceQuota {
  return { plan: p.plan, limit: p.limit, remaining: p.limit == null ? null : 0, exhausted: true, resetsOn: p.resetsOn }
}
