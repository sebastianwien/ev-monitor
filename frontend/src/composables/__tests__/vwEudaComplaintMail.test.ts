import { describe, it, expect } from 'vitest'
import { buildEudaComplaintMail } from '../useEudaComplaintMail'
import type { EudaSyncActivity } from '../../api/euDataActSyncService'

const NOW = new Date('2026-09-21T10:00:00Z')

const activity: EudaSyncActivity = {
  provider: 'VW_GROUP',
  manufacturerContact: 'euda-support@cariad.technology',
  connection: {
    carId: 'c1', brand: 'volkswagen', status: 'ACTIVE', connectedAt: '2026-09-19T15:12:19Z',
    lastPolledAt: '2026-09-21T09:58:00Z', lastSuccessAt: '2026-09-21T09:58:00Z', consecutiveFailures: 0,
    lastError: null, dataRequestActive: true, lastDeliveryAt: '2026-09-21T09:58:00Z', lastDataAt: null,
    history: { requestedAt: '2026-09-19T15:20:00Z', importedAt: null, running: false, attempts: 0, attemptsExhausted: false, error: null },
  },
  identifiers: [
    { label: 'VIN', value: 'WVWZZZE1ZM8000365' },
    { label: 'Account', value: 'max@example.com' },
    { label: 'Data request name', value: 'ev-monitor AutoSync' },
    { label: 'Data request identifier', value: 'a1b2c3d4e5f6' },
    { label: 'Request file identifier', value: 'ffeeddccbbaa' },
  ],
  summary: { deliveriesSeen: 180, deliveriesWithContent: 0, sessionsImported: 0, lastContentAt: null },
  polls: [],
  deliveries: [],
}

function decoded(href: string) {
  const [, query] = href.split('?')
  const params = new URLSearchParams(query)
  return { subject: params.get('subject') ?? '', body: params.get('body') ?? '' }
}

describe('buildEudaComplaintMail', () => {
  it('adressiert den Hersteller-Kontakt aus dem Vertrag', () => {
    expect(buildEudaComplaintMail(activity, 'de', NOW).href.startsWith('mailto:euda-support@cariad.technology?')).toBe(true)
  })

  it('traegt VIN im Betreff und alle Identifier im Text', () => {
    const { subject, body } = decoded(buildEudaComplaintMail(activity, 'de', NOW).href)
    expect(subject).toContain('WVWZZZE1ZM8000365')
    expect(subject).toContain('Art. 4')
    for (const id of activity.identifiers) expect(body).toContain(id.value)
  })

  it('nennt Zahlen, Rechtsgrundlage, Frist und Bundesnetzagentur (de)', () => {
    const { body } = decoded(buildEudaComplaintMail(activity, 'de', NOW).href)
    expect(body).toContain('180')
    expect(body).toContain('2023/2854')
    expect(body).toContain('14 Tagen')
    expect(body).toContain('Bundesnetzagentur')
    expect(body).toContain('[Name]')
  })

  it('schreibt fuer andere Sprachen Englisch', () => {
    const { body, subject } = decoded(buildEudaComplaintMail(activity, 'sv', NOW).href)
    expect(subject).toContain('Complaint')
    expect(body).toContain('Regulation (EU) 2023/2854')
    expect(body).toContain('Bundesnetzagentur')
  })

  it('nimmt die letzte Portal-Fehlermeldung woertlich auf', () => {
    const failing = { ...activity, connection: { ...activity.connection, dataRequestActive: false, consecutiveFailures: 27, lastError: 'Anfrage anlegen HTTP 400: {"error":"not eligible"}' } }
    const { body } = decoded(buildEudaComplaintMail(failing, 'de', NOW).href)
    expect(body).toContain('Anfrage anlegen HTTP 400: {"error":"not eligible"}')
  })

  it('bleibt unter dem mailto-Limit', () => {
    expect(buildEudaComplaintMail(activity, 'de', NOW).href.length).toBeLessThan(2000 * 3)
    expect(decoded(buildEudaComplaintMail(activity, 'de', NOW).href).body.length).toBeLessThan(2000)
    expect(decoded(buildEudaComplaintMail(activity, 'en', NOW).href).body.length).toBeLessThan(2000)
  })
})
