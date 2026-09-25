import { describe, it, expect } from 'vitest'
import { deriveVwEudaPrimaryAction, isVwEudaHistoryOpen } from '../useVwEudaPrimaryAction'

const base = { status: 'ACTIVE' as const, health: null, hasComplaint: true, historyOpen: true }

describe('deriveVwEudaPrimaryAction', () => {
  it('pausiert: Upgrade, egal was sonst ist', () => {
    expect(deriveVwEudaPrimaryAction({ ...base, status: 'EXPIRED', health: 'NO_CONTENT' })).toBe('upgrade')
  })

  it('Anmeldung abgelaufen: neu anmelden', () => {
    expect(deriveVwEudaPrimaryAction({ ...base, status: 'AUTH_FAILED', health: 'AUTH_FAILED' })).toBe('relogin')
  })

  it('Hersteller in der Pflicht: Beschwerde, auch wenn die Historie noch offen ist', () => {
    for (const health of ['NO_CONTENT', 'STALE', 'NO_REQUEST'] as const) {
      expect(deriveVwEudaPrimaryAction({ ...base, health })).toBe('complaint')
    }
  })

  it('Hersteller in der Pflicht, aber kein Mailtext (Protokoll fehlt): faellt auf Historie zurueck', () => {
    expect(deriveVwEudaPrimaryAction({ ...base, health: 'NO_CONTENT', hasComplaint: false })).toBe('history')
  })

  it('sonst Historie holen, solange sie offen ist', () => {
    expect(deriveVwEudaPrimaryAction({ ...base, health: 'HEALTHY' })).toBe('history')
    expect(deriveVwEudaPrimaryAction({ ...base, health: 'HISTORY_FAILED' })).toBe('history')
    expect(deriveVwEudaPrimaryAction({ ...base, health: null })).toBe('history')
  })

  it('alles erledigt: keine primaere Handlung', () => {
    expect(deriveVwEudaPrimaryAction({ ...base, health: 'HEALTHY', historyOpen: false })).toBeNull()
  })
})

describe('isVwEudaHistoryOpen', () => {
  const h = (o: Partial<{ requestedAt: string | null; importedAt: string | null; running: boolean }>) =>
    ({ requestedAt: null, importedAt: null, running: false, attempts: 0, attemptsExhausted: false, error: null, ...o })

  it('offen, wenn nie geholt', () => {
    expect(isVwEudaHistoryOpen(null, null, false)).toBe(true)
  })
  it('zu, sobald importiert (Protokoll oder Status-DTO)', () => {
    expect(isVwEudaHistoryOpen(h({ importedAt: '2026-09-20T10:00:00Z' }), null, false)).toBe(false)
    expect(isVwEudaHistoryOpen(null, '2026-09-20T10:00:00Z', false)).toBe(false)
  })
  it('zu, waehrend sie laeuft oder gerade angefordert wurde', () => {
    expect(isVwEudaHistoryOpen(h({ running: true }), null, false)).toBe(false)
    expect(isVwEudaHistoryOpen(null, null, true)).toBe(false)
  })
  it('offen nach gescheiterten Versuchen, damit der Nutzer neu holen kann', () => {
    expect(isVwEudaHistoryOpen(h({ requestedAt: '2026-09-19T10:00:00Z' }), null, false)).toBe(true)
  })
})
