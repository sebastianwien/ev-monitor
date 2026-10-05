import { describe, it, expect } from 'vitest'
import { voiceProblem } from '../voiceProblem'

const httpError = (status: number, data?: unknown) => ({ response: { status, data } })

describe('voiceProblem', () => {
  it('ordnet die Fehlercodes des Backends zu', () => {
    expect(voiceProblem(httpError(422, { code: 'VOICE_NOT_UNDERSTOOD' }))).toEqual({ kind: 'not_understood' })
    expect(voiceProblem(httpError(413, { code: 'VOICE_TOO_LARGE' }))).toEqual({ kind: 'too_large' })
    expect(voiceProblem(httpError(415, { code: 'VOICE_UNSUPPORTED_TYPE' }))).toEqual({ kind: 'unsupported' })
    expect(voiceProblem(httpError(429, { code: 'RATE_LIMITED' }))).toEqual({ kind: 'rate_limited' })
    expect(voiceProblem(httpError(502, { code: 'VOICE_FAILED' }))).toEqual({ kind: 'failed' })
    expect(voiceProblem(httpError(503, { code: 'VOICE_UNAVAILABLE' }))).toEqual({ kind: 'unavailable' })
  })

  it('Deckel erreicht: mit Modell und Rückstellungsdatum, Limit nur wenn mitgeliefert (fehlt bei Admins)', () => {
    expect(voiceProblem(httpError(429, { code: 'VOICE_LIMIT_REACHED', plan: 'free', limit: 5, resetsOn: '2026-11-01' })))
      .toEqual({ kind: 'limit', plan: 'free', limit: 5, resetsOn: '2026-11-01' })
    expect(voiceProblem(httpError(429, { code: 'VOICE_LIMIT_REACHED', plan: 'admin', resetsOn: '2026-11-01' })))
      .toEqual({ kind: 'limit', plan: 'admin', limit: null, resetsOn: '2026-11-01' })
  })

  it('Unbekanntes, 404 und Netzwerkfehler sind "hat nicht geklappt"', () => {
    expect(voiceProblem(httpError(404))).toEqual({ kind: 'failed' })
    expect(voiceProblem(httpError(500, 'oops'))).toEqual({ kind: 'failed' })
    expect(voiceProblem(new Error('Network Error'))).toEqual({ kind: 'failed' })
  })
})
