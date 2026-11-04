import { describe, it, expect } from 'vitest'
import { shouldReloadOnResume, RESUME_RELOAD_MIN_MS } from './useResumeReload'
import { registerSoftReload, softReloadLoadedAt } from './usePullToRefresh'

const at = (iso: string) => new Date(iso).getTime()

describe('shouldReloadOnResume', () => {
  it('lädt nicht, wenn die Daten jünger als 15 Minuten sind', () => {
    expect(shouldReloadOnResume(at('2026-11-04T10:00:00'), at('2026-11-04T10:14:59'))).toBe(false)
  })

  it('lädt, sobald die Daten 15 Minuten alt sind, egal wie kurz die App weg war', () => {
    expect(shouldReloadOnResume(at('2026-11-04T10:00:00'), at('2026-11-04T10:00:00') + RESUME_RELOAD_MIN_MS)).toBe(true)
  })

  it('lädt, wenn die Daten von gestern sind', () => {
    expect(shouldReloadOnResume(at('2026-11-03T23:58:00'), at('2026-11-04T00:01:00'))).toBe(true)
  })

  it('lädt nicht ohne bekannten Ladezeitpunkt', () => {
    expect(shouldReloadOnResume(null, at('2026-11-04T10:00:00'))).toBe(false)
  })
})

describe('softReloadLoadedAt', () => {
  it('liefert den Ladezeitpunkt der angemeldeten Seite und nach dem Abmelden nichts', () => {
    const unregister = registerSoftReload(() => {}, () => 1234)
    expect(softReloadLoadedAt()).toBe(1234)
    unregister()
    expect(softReloadLoadedAt()).toBeNull()
  })
})
