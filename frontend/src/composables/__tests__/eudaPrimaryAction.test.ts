import { describe, it, expect } from 'vitest'
import { deriveEudaPrimaryAction, isEudaHistoryOpen } from '../useEudaPrimaryAction'

const base = { status: 'ACTIVE' as const, health: null, hasComplaint: true, historyOpen: true }

describe('deriveEudaPrimaryAction', () => {
  it('pausiert: Upgrade, egal was sonst ist', () => {
    expect(deriveEudaPrimaryAction({ ...base, status: 'EXPIRED', health: 'NO_CONTENT' })).toBe('upgrade')
  })

  it('Anmeldung abgelaufen: neu anmelden', () => {
    expect(deriveEudaPrimaryAction({ ...base, status: 'AUTH_FAILED', health: 'AUTH_FAILED' })).toBe('relogin')
  })

  it('Hersteller in der Pflicht: Beschwerde, auch wenn die Historie noch offen ist', () => {
    for (const health of ['NO_CONTENT', 'STALE', 'NO_REQUEST'] as const) {
      expect(deriveEudaPrimaryAction({ ...base, health })).toBe('complaint')
    }
  })

  it('Hersteller in der Pflicht, aber kein Mailtext (Protokoll fehlt): faellt auf Historie zurueck', () => {
    expect(deriveEudaPrimaryAction({ ...base, health: 'NO_CONTENT', hasComplaint: false })).toBe('history')
  })

  it('sonst Historie holen, solange sie offen ist', () => {
    expect(deriveEudaPrimaryAction({ ...base, health: 'HEALTHY' })).toBe('history')
    expect(deriveEudaPrimaryAction({ ...base, health: 'HISTORY_FAILED' })).toBe('history')
    expect(deriveEudaPrimaryAction({ ...base, health: null })).toBe('history')
  })

  it('alles erledigt: keine primaere Handlung', () => {
    expect(deriveEudaPrimaryAction({ ...base, health: 'HEALTHY', historyOpen: false })).toBeNull()
  })
})

describe('isEudaHistoryOpen', () => {
  const h = (o: Partial<{ requestedAt: string | null; importedAt: string | null; running: boolean }>) =>
    ({ requestedAt: null, importedAt: null, running: false, attempts: 0, attemptsExhausted: false, error: null, ...o })

  it('offen, wenn nie geholt', () => {
    expect(isEudaHistoryOpen(null, null, false)).toBe(true)
  })
  it('zu, sobald importiert (Protokoll oder Status-DTO)', () => {
    expect(isEudaHistoryOpen(h({ importedAt: '2026-09-20T10:00:00Z' }), null, false)).toBe(false)
    expect(isEudaHistoryOpen(null, '2026-09-20T10:00:00Z', false)).toBe(false)
  })
  it('zu, waehrend sie laeuft oder gerade angefordert wurde', () => {
    expect(isEudaHistoryOpen(h({ running: true }), null, false)).toBe(false)
    expect(isEudaHistoryOpen(null, null, true)).toBe(false)
  })
  it('offen nach gescheiterten Versuchen, damit der Nutzer neu holen kann', () => {
    expect(isEudaHistoryOpen(h({ requestedAt: '2026-09-19T10:00:00Z' }), null, false)).toBe(true)
  })
})
