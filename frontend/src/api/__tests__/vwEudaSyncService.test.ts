import { describe, it, expect } from 'vitest'
import { withUtcTimestamps } from '../vwEudaSyncService'

// Connectors liefert LocalDateTime in UTC ohne Zonenangabe ("2026-09-29T10:22:16.463344").
// Der Browser liest so einen String als Ortszeit, in Berlin also 2 Stunden zu frueh.
describe('withUtcTimestamps', () => {
  it('markiert zonenlose Zeitstempel als UTC, auch verschachtelt und in Arrays', () => {
    const out = withUtcTimestamps({
      connection: { lastCapturedAt: '2026-09-27T05:02:27', lastDataAt: null, history: { requestedAt: '2026-09-19T15:20:00.123456' } },
      polls: [{ at: '2026-09-29T10:22:16', outcome: 'NO_NEW_DATA' }],
      summary: { deliveriesSeen: 180 },
    })
    expect(out.connection.lastCapturedAt).toBe('2026-09-27T05:02:27Z')
    expect(out.connection.history.requestedAt).toBe('2026-09-19T15:20:00.123456Z')
    expect(out.polls[0].at).toBe('2026-09-29T10:22:16Z')
    expect(new Date(out.connection.lastCapturedAt).toISOString()).toBe('2026-09-27T05:02:27.000Z')
  })

  it('lässt Werte mit Zone, Nullen, Zahlen und Freitext unverändert', () => {
    const out = withUtcTimestamps({
      a: '2026-09-21T09:58:00Z', b: '2026-09-21T09:58:00+02:00', c: null, d: 180, e: 'Portal HTTP 500', f: 'NO_NEW_DATA',
    })
    expect(out).toEqual({ a: '2026-09-21T09:58:00Z', b: '2026-09-21T09:58:00+02:00', c: null, d: 180, e: 'Portal HTTP 500', f: 'NO_NEW_DATA' })
  })
})
