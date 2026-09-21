import { describe, it, expect } from 'vitest'
import { classifyEudaHealth } from '../useEudaHealth'
import type { EudaSyncActivity } from '../../api/euDataActSyncService'

const NOW = new Date('2026-09-21T10:00:00Z')

function activity(over: Partial<EudaSyncActivity['connection']> = {}, summary: Partial<EudaSyncActivity['summary']> = {}): EudaSyncActivity {
  return {
    provider: 'VW_GROUP',
    manufacturerContact: 'euda-support@cariad.technology',
    connection: {
      carId: 'c1', brand: 'volkswagen', status: 'ACTIVE', connectedAt: '2026-09-19T15:00:00Z',
      lastPolledAt: '2026-09-21T09:58:00Z', lastSuccessAt: '2026-09-21T09:58:00Z', consecutiveFailures: 0,
      lastError: null, dataRequestActive: true, lastDeliveryAt: '2026-09-21T09:58:00Z', lastDataAt: null,
      history: null, ...over,
    },
    identifiers: [],
    summary: { deliveriesSeen: 180, deliveriesWithContent: 0, sessionsImported: 0, lastContentAt: null, ...summary },
    polls: [],
    deliveries: [],
  }
}

describe('classifyEudaHealth', () => {
  it('AUTH_FAILED hat Vorrang', () => {
    expect(classifyEudaHealth(activity({ status: 'AUTH_FAILED' }), NOW)).toBe('AUTH_FAILED')
  })

  it('EXPIRED wird als PAUSED gemeldet', () => {
    expect(classifyEudaHealth(activity({ status: 'EXPIRED' }), NOW)).toBe('PAUSED')
  })

  it('ohne Datenanfrage beim Hersteller: NO_REQUEST', () => {
    expect(classifyEudaHealth(activity({ dataRequestActive: false, consecutiveFailures: 27, lastError: 'Anfrage anlegen HTTP 400' }), NOW)).toBe('NO_REQUEST')
  })

  it('frisch verbunden ohne Inhalt: WAITING_FIRST', () => {
    expect(classifyEudaHealth(activity({ connectedAt: '2026-09-21T06:00:00Z' }), NOW)).toBe('WAITING_FIRST')
  })

  it('seit ueber 24h nur leere Lieferungen: NO_CONTENT', () => {
    expect(classifyEudaHealth(activity(), NOW)).toBe('NO_CONTENT')
  })

  it('Inhalt kam, aber seit 72h nichts mehr: STALE', () => {
    expect(classifyEudaHealth(activity({ lastDataAt: '2026-09-17T09:00:00Z' }, { deliveriesWithContent: 5, sessionsImported: 3 }), NOW)).toBe('STALE')
  })

  it('wiederholte Fehler ueberlagern einen gesunden Verlauf', () => {
    expect(classifyEudaHealth(activity({ lastDataAt: '2026-09-21T09:00:00Z', consecutiveFailures: 3, lastError: 'Portal HTTP 503' }, { deliveriesWithContent: 5 }), NOW)).toBe('FAILING')
  })

  it('Historie mit ausgeschoepften Versuchen: HISTORY_FAILED, wenn sonst alles laeuft', () => {
    const a = activity({ lastDataAt: '2026-09-21T09:00:00Z', history: { requestedAt: '2026-09-19T15:00:00Z', importedAt: null, running: false, attempts: 3, attemptsExhausted: true, error: 'Read timed out' } }, { deliveriesWithContent: 5 })
    expect(classifyEudaHealth(a, NOW)).toBe('HISTORY_FAILED')
  })

  it('nur leere Lieferungen schlagen den gescheiterten Historien-Import: NO_CONTENT', () => {
    const a = activity({ history: { requestedAt: '2026-09-19T15:00:00Z', importedAt: null, running: false, attempts: 3, attemptsExhausted: true, error: 'Read timed out' } })
    expect(classifyEudaHealth(a, NOW)).toBe('NO_CONTENT')
  })

  it('frisch verbunden mit gescheiterter Historie: HISTORY_FAILED', () => {
    const a = activity({ connectedAt: '2026-09-21T06:00:00Z', history: { requestedAt: '2026-09-21T06:05:00Z', importedAt: null, running: false, attempts: 3, attemptsExhausted: true, error: 'Read timed out' } })
    expect(classifyEudaHealth(a, NOW)).toBe('HISTORY_FAILED')
  })

  it('laufend Inhalt: HEALTHY', () => {
    expect(classifyEudaHealth(activity({ lastDataAt: '2026-09-21T09:00:00Z' }, { deliveriesWithContent: 5, sessionsImported: 2 }), NOW)).toBe('HEALTHY')
  })
})
