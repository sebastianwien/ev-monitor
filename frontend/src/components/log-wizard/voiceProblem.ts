import type { VoicePlan } from './voiceQuota'

/** Was beim Sprachlog schiefging, in der Sprache der Hinweise unter dem Mikrofon. */
export type VoiceProblem =
  | { kind: 'not_understood' | 'too_large' | 'unsupported' | 'rate_limited' | 'failed' | 'unavailable' }
  | { kind: 'limit'; plan: VoicePlan; limit: number | null; resetsOn: string }

const BY_CODE: Record<string, Exclude<VoiceProblem['kind'], 'limit'>> = {
  VOICE_NOT_UNDERSTOOD: 'not_understood',
  VOICE_TOO_LARGE: 'too_large',
  VOICE_UNSUPPORTED_TYPE: 'unsupported',
  RATE_LIMITED: 'rate_limited',
  VOICE_FAILED: 'failed',
  VOICE_UNAVAILABLE: 'unavailable',
}

/** Fehler von POST /logs/voice-draft (Body immer { code }) auf einen Hinweis abbilden. */
export function voiceProblem(err: unknown): VoiceProblem {
  const data = (err as any)?.response?.data
  const code = typeof data === 'object' && data ? data.code : undefined
  if (code === 'VOICE_LIMIT_REACHED') return { kind: 'limit', plan: data.plan ?? 'free', limit: data.limit ?? null, resetsOn: data.resetsOn }
  return { kind: (code && BY_CODE[code]) || 'failed' }
}
