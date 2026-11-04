import { describe, it, expect } from 'vitest'
import { shouldReloadOnResume, RESUME_RELOAD_MIN_MS } from './useResumeReload'

const at = (iso: string) => new Date(iso).getTime()

describe('shouldReloadOnResume', () => {
  it('lädt nicht nach kurzem App-Wechsel am selben Tag', () => {
    expect(shouldReloadOnResume(at('2026-11-04T10:00:00'), at('2026-11-04T10:14:59'))).toBe(false)
  })

  it('lädt nach 15 Minuten im Hintergrund', () => {
    expect(shouldReloadOnResume(at('2026-11-04T10:00:00'), at('2026-11-04T10:00:00') + RESUME_RELOAD_MIN_MS)).toBe(true)
  })

  it('lädt nach Mitternacht auch nach kurzer Pause', () => {
    expect(shouldReloadOnResume(at('2026-11-03T23:58:00'), at('2026-11-04T00:01:00'))).toBe(true)
  })

  it('lädt nicht ohne bekannten Zeitpunkt des Verlassens', () => {
    expect(shouldReloadOnResume(null, at('2026-11-04T10:00:00'))).toBe(false)
  })
})
