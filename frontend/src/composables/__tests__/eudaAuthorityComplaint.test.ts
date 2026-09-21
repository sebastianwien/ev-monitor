import { describe, it, expect } from 'vitest'
import {
  buildEudaEvidenceDocument, buildEudaAuthorityFormValues, AUTHORITY_FORM_URL,
} from '../useEudaAuthorityComplaint'
import type { EudaSyncActivity } from '../../api/euDataActSyncService'

const NOW = new Date('2026-09-21T10:00:00Z')

const activity: EudaSyncActivity = {
  provider: 'VW_GROUP',
  manufacturerContact: 'euda-support@cariad.technology',
  connection: {
    carId: 'c1', brand: 'volkswagen', status: 'ACTIVE', connectedAt: '2026-09-01T15:12:19Z',
    lastPolledAt: '2026-09-21T09:58:00Z', lastSuccessAt: '2026-09-21T09:58:00Z', consecutiveFailures: 0,
    lastError: 'Anfrage anlegen HTTP 400: {"error":"x"}', dataRequestActive: true, lastDeliveryAt: '2026-09-21T09:58:00Z', lastDataAt: null,
    history: { requestedAt: '2026-09-01T15:20:00Z', importedAt: null, running: false, attempts: 3, attemptsExhausted: true, error: 'Read timed out' },
  },
  identifiers: [
    { label: 'VIN', value: 'WVWZZZE1ZM8000365' },
    { label: 'Account', value: 'max@example.com' },
    { label: 'Data request identifier', value: 'a1b2c3d4e5f6' },
  ],
  summary: { deliveriesSeen: 480, deliveriesWithContent: 0, sessionsImported: 0, lastContentAt: null },
  polls: [
    { at: '2026-09-21T09:58:00Z', outcome: 'NO_NEW_DATA', deliveriesSeen: 1, deliveriesWithContent: 0, sessionsImported: 0, sessionsSkipped: 0, history: false, error: null },
    { at: '2026-09-21T08:58:00Z', outcome: 'PORTAL_ERROR', deliveriesSeen: 0, deliveriesWithContent: 0, sessionsImported: 0, sessionsSkipped: 0, history: false, error: 'Liste HTTP 503: <html>' },
  ],
  deliveries: [
    { filename: 'WVW_2026-09-20.zip', createdOn: '2026-09-20T10:00:00Z', sizeBytes: 2048, outcome: 'NO_CHARGING_DATA', sessionsImported: 0, sessionsSkipped: 0, error: null },
  ],
}

describe('buildEudaEvidenceDocument', () => {
  const doc = buildEudaEvidenceDocument(activity, 'de', NOW)
  const text = doc.sections.flatMap(s => [s.heading, ...s.lines]).join('\n')

  it('benennt Datei nach VIN und Datum', () => {
    expect(doc.filename).toBe('ev-monitor-data-act-beleg-WVWZZZE1ZM8000365-2026-09-21.pdf')
  })

  it('traegt alle Identifier, Zaehler und Fehlermeldungen', () => {
    for (const id of activity.identifiers) expect(text).toContain(id.value)
    expect(text).toContain('480')
    expect(text).toContain('Anfrage anlegen HTTP 400')
    expect(text).toContain('Read timed out')
    expect(text).toContain('Liste HTTP 503')
  })

  it('listet Abfragen und Lieferungen mit Zeitstempel', () => {
    const polls = doc.sections.find(s => s.key === 'polls')!
    expect(polls.lines).toHaveLength(2)
    expect(polls.lines[1]).toMatch(/2026.*Portal-Fehler.*503/)
    const deliveries = doc.sections.find(s => s.key === 'deliveries')!
    expect(deliveries.lines[0]).toContain('WVW_2026-09-20.zip')
  })

  it('nennt die Rechtsgrundlage und dass ev-monitor nicht Partei ist', () => {
    expect(text).toContain('2023/2854')
    expect(text).toMatch(/nicht Partei/)
  })

  it('englisch fuer andere Locales', () => {
    const en = buildEudaEvidenceDocument(activity, 'sv', NOW)
    expect(en.title).toMatch(/Evidence/)
    expect(en.sections.find(s => s.key === 'polls')!.lines[1]).toMatch(/Portal error/)
  })
})

describe('buildEudaAuthorityFormValues', () => {
  const values = buildEudaAuthorityFormValues(activity, 'de', NOW)
  const byKey = Object.fromEntries(values.map(v => [v.key, v.value]))

  it('liefert die Felder in Formular-Reihenfolge', () => {
    expect(values.map(v => v.key)).toEqual(['role', 'respondent', 'violation', 'product', 'sector', 'personalData', 'priorComplaint', 'comment'])
  })

  it('leitet Beschwerdegegner aus dem Provider ab', () => {
    expect(byKey.respondent).toContain('Volkswagen AG')
    expect(byKey.respondent).toContain('Cariad SE')
  })

  it('nennt Produkt mit Marke und FIN', () => {
    expect(byKey.product).toContain('Volkswagen')
    expect(byKey.product).toContain('WVWZZZE1ZM8000365')
  })

  it('Kommentar enthaelt Sachverhalt mit Zahlen, Identifiern und Rechtsgrundlage', () => {
    expect(byKey.comment).toContain('480')
    expect(byKey.comment).toContain('a1b2c3d4e5f6')
    expect(byKey.comment).toContain('Art. 4')
    expect(byKey.comment).toContain(activity.manufacturerContact)
    expect(byKey.comment.length).toBeLessThan(3000)
  })

  it('verweist auf das BNetzA-Formular', () => {
    expect(AUTHORITY_FORM_URL).toMatch(/^https:\/\/www\.bundesnetzagentur\.de\/.*Beschwerde/)
  })

  it('englische Werte fuer andere Locales', () => {
    const en = Object.fromEntries(buildEudaAuthorityFormValues(activity, 'en', NOW).map(v => [v.key, v.value]))
    expect(en.role).toBe('Nutzer (User)')
    expect(en.comment).toContain('Art. 4(1)')
  })
})
