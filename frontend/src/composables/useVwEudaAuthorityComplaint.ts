import type { EudaSyncActivity, EudaPollEntry, EudaDeliveryEntry } from '../api/euDataActSyncService'
import { lastContentAt } from './useEudaHealth'

/**
 * Beschwerde bei der Aufsichtsbehörde (Bundesnetzagentur, Art. 37 Data Act, DA-DG seit 29.05.2026).
 * Die Behörde nimmt Beschwerden nur über ihr Web-Formular an, ohne API. Deshalb liefern wir zwei Dinge:
 * 1. ein Beleg-Dokument (PDF-Anlage) mit Identifiern, Zählern und dem Protokoll aus dem Sync,
 * 2. die Werte für die Formularfelder zum Kopieren, in Formular-Reihenfolge.
 * Der Nutzer reicht selbst ein. ev-monitor ist nicht Partei, nichts wird gespeichert.
 * Deutsch für de, sonst Englisch (das Formular selbst ist deutsch, die Feldnamen bleiben deutsch).
 */
export const AUTHORITY_FORM_URL =
  'https://www.bundesnetzagentur.de/_tools/_forms/05_Digitalisierung/DataAct/Form01_Beschwerde_II_III/node.html'

export interface EvidenceSection {
  key: 'summary' | 'identifiers' | 'history' | 'polls' | 'deliveries' | 'legal'
  heading: string
  lines: string[]
}

export interface EvidenceDocument {
  title: string
  subtitle: string
  filename: string
  sections: EvidenceSection[]
}

export type AuthorityFieldKey = 'role' | 'respondent' | 'violation' | 'product' | 'sector' | 'personalData' | 'priorComplaint' | 'comment'

export interface AuthorityFormValue {
  key: AuthorityFieldKey
  /** Feldname wie im Formular der Behörde (deutsch). */
  field: string
  value: string
  multiline?: boolean
}

/** Beschwerdegegner je Provider. Neue Data-Act-Provider (XPeng, Polestar) tragen sich hier ein. */
const RESPONDENT_BY_PROVIDER: Record<string, string> = {
  VW_GROUP: 'Volkswagen AG, Berliner Ring 2, 38440 Wolfsburg (Datenportal betrieben von Cariad SE, Ingolstadt)',
}

const BRAND_NAME: Record<string, string> = {
  volkswagen: 'Volkswagen', skoda: 'Škoda', audi: 'Audi', seat: 'SEAT', cupra: 'CUPRA',
}

const isDe = (locale: string) => locale === 'de'
const dateLocale = (locale: string) => (isDe(locale) ? 'de-DE' : 'en-GB')
const fmtDate = (iso: string | null, locale: string) =>
  iso ? new Date(iso).toLocaleDateString(dateLocale(locale), { day: '2-digit', month: '2-digit', year: 'numeric' }) : '-'
const fmtDateTime = (iso: string | null, locale: string) =>
  iso ? new Date(iso).toLocaleString(dateLocale(locale), { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Berlin' }) : '-'
const isoDay = (d: Date) => d.toISOString().slice(0, 10)

const POLL_OUTCOME: Record<string, [string, string]> = {
  OK: ['Daten importiert', 'Data imported'],
  NO_NEW_DATA: ['Keine neuen Daten', 'No new data'],
  PORTAL_ERROR: ['Portal-Fehler', 'Portal error'],
  IMPORT_ERROR: ['Import-Fehler', 'Import error'],
  AUTH_FAILED: ['Anmeldung abgelaufen', 'Login expired'],
}
const DELIVERY_OUTCOME: Record<string, [string, string]> = {
  IMPORTED: ['Importiert', 'Imported'],
  NO_CHARGING_DATA: ['Keine Ladedaten enthalten', 'No charging data'],
  FAILED: ['Import fehlgeschlagen', 'Import failed'],
}
const label = (table: Record<string, [string, string]>, key: string | null, locale: string) =>
  table[key ?? '']?.[isDe(locale) ? 0 : 1] ?? (key ?? '-')

interface Facts {
  de: boolean
  vin: string
  brand: string
  since: string
  today: string
  seen: number
  empty: number
  content: number
  imported: number
}

function facts(activity: EudaSyncActivity, locale: string, now: Date): Facts {
  const s = activity.summary
  return {
    de: isDe(locale),
    vin: activity.identifiers.find(i => i.label === 'VIN')?.value ?? '-',
    brand: BRAND_NAME[activity.connection.brand] ?? activity.connection.brand,
    since: fmtDate(activity.connection.connectedAt, locale),
    today: fmtDate(now.toISOString(), locale),
    seen: s.deliveriesSeen,
    empty: s.deliveriesSeen - s.deliveriesWithContent,
    content: s.deliveriesWithContent,
    imported: s.sessionsImported,
  }
}

function situationSentence(f: Facts): string {
  return f.de
    ? `Seit dem ${f.since} wurden ${f.seen} Datensätze bereitgestellt, davon ${f.empty} ohne Inhalt (no_content_found) und ${f.content} mit Inhalt. Daraus wurden ${f.imported} Ladevorgänge importiert.`
    : `Since ${f.since}, ${f.seen} data sets have been provided, ${f.empty} of them without content (no_content_found) and ${f.content} with content. ${f.imported} charging sessions were imported from them.`
}

function historyLines(activity: EudaSyncActivity, locale: string): string[] {
  const h = activity.connection.history
  const de = isDe(locale)
  if (!h?.requestedAt) return [de ? 'Kein Historien-Export (Request File) angefordert.' : 'No history export (Request File) requested.']
  const lines = [de ? `Angefordert am ${fmtDate(h.requestedAt, locale)}.` : `Requested on ${fmtDate(h.requestedAt, locale)}.`]
  if (h.importedAt) lines.push(de ? `Bereitgestellt und importiert am ${fmtDate(h.importedAt, locale)}.` : `Provided and imported on ${fmtDate(h.importedAt, locale)}.`)
  else lines.push(de ? 'Bisher nicht bereitgestellt bzw. nicht importierbar.' : 'Not provided or not importable so far.')
  if (h.error) lines.push(de ? `Letzter Fehler: ${h.error}` : `Last error: ${h.error}`)
  return lines
}

const pollLine = (p: EudaPollEntry, locale: string) => {
  const de = isDe(locale)
  const counts = p.history
    ? (de ? `Historie, ${p.sessionsImported} Ladevorgänge` : `history, ${p.sessionsImported} sessions`)
    : (de ? `${p.deliveriesSeen} Datensätze, ${p.deliveriesWithContent} mit Inhalt, ${p.sessionsImported} Ladevorgänge` : `${p.deliveriesSeen} data sets, ${p.deliveriesWithContent} with content, ${p.sessionsImported} sessions`)
  return [fmtDateTime(p.at, locale), label(POLL_OUTCOME, p.outcome, locale), counts, p.error].filter(Boolean).join(' | ')
}

const deliveryLine = (d: EudaDeliveryEntry, locale: string) =>
  [fmtDateTime(d.createdOn, locale), label(DELIVERY_OUTCOME, d.outcome, locale), `${Math.max(1, Math.round(d.sizeBytes / 1024))} KB`, d.filename, d.error].filter(Boolean).join(' | ')

export function buildEudaEvidenceDocument(activity: EudaSyncActivity, locale: string, now: Date = new Date()): EvidenceDocument {
  const f = facts(activity, locale, now)
  const c = activity.connection
  const de = f.de

  const summary: string[] = [
    de ? `Fahrzeug: ${f.brand}, FIN ${f.vin}` : `Vehicle: ${f.brand}, VIN ${f.vin}`,
    de ? `Datenempfänger (Dritter nach Art. 5): ev-monitor.net` : `Data recipient (third party under Art. 5): ev-monitor.net`,
    de ? `Verbunden seit: ${f.since}` : `Connected since: ${f.since}`,
    situationSentence(f),
    de ? `Letzte Lieferung mit Inhalt: ${fmtDateTime(lastContentAt(activity), locale)}` : `Last delivery with content: ${fmtDateTime(lastContentAt(activity), locale)}`,
    de ? `Laufende Datenanfrage beim Hersteller: ${c.dataRequestActive ? 'ja' : 'nein'}` : `Active data request at manufacturer: ${c.dataRequestActive ? 'yes' : 'no'}`,
  ]
  if (c.lastError) summary.push(de ? `Letzte Fehlermeldung des Portals (${c.consecutiveFailures} in Folge, zuletzt ${fmtDateTime(c.lastPolledAt, locale)}): ${c.lastError}` : `Last portal error (${c.consecutiveFailures} in a row, last ${fmtDateTime(c.lastPolledAt, locale)}): ${c.lastError}`)

  const sections: EvidenceSection[] = [
    { key: 'summary', heading: de ? 'Zusammenfassung' : 'Summary', lines: summary },
    { key: 'identifiers', heading: de ? 'Kennungen der Datenanfrage (aus dem Portal des Herstellers)' : 'Identifiers of the data request (from the manufacturer portal)', lines: activity.identifiers.map(i => `${i.label}: ${i.value}`) },
    { key: 'history', heading: de ? 'Historien-Export (Request File)' : 'History export (Request File)', lines: historyLines(activity, locale) },
    { key: 'polls', heading: de ? `Abfragen des Portals (letzte ${activity.polls.length}, Zeit Europe/Berlin)` : `Portal polls (last ${activity.polls.length}, time Europe/Berlin)`, lines: activity.polls.length ? activity.polls.map(p => pollLine(p, locale)) : [de ? 'Noch keine Abfrage.' : 'No polls yet.'] },
    { key: 'deliveries', heading: de ? `Lieferungen mit Inhalt (letzte ${activity.deliveries.length})` : `Deliveries with content (last ${activity.deliveries.length})`, lines: activity.deliveries.length ? activity.deliveries.map(d => deliveryLine(d, locale)) : [de ? 'Noch keine Lieferung mit Inhalt.' : 'No delivery with content yet.'] },
    { key: 'legal', heading: de ? 'Hinweise' : 'Notes', lines: de ? [
      'Rechtsgrundlage: Art. 4 Abs. 1 und Art. 5 Abs. 1 der Verordnung (EU) 2023/2854 (Data Act), anwendbar seit dem 12.09.2025. Zuständige Behörde in Deutschland: Bundesnetzagentur (Art. 37 Data Act, Data-Act-Durchführungsgesetz).',
      'Dieses Dokument wurde von ev-monitor.net automatisch aus dem Sync-Protokoll des Nutzers erzeugt. Die Abfragen erfolgen stündlich über die vom Hersteller bereitgestellte Data-Act-Schnittstelle. ev-monitor.net ist nicht Partei der Beschwerde und gibt keine Rechtsberatung.',
      'Ein Datensatz "ohne Inhalt" ist eine vom Portal bereitgestellte Datei, die ausschließlich den Vermerk no_content_found enthält.',
    ] : [
      'Legal basis: Art. 4(1) and Art. 5(1) of Regulation (EU) 2023/2854 (Data Act), applicable since 12 September 2025. Competent authority in Germany: Bundesnetzagentur (Art. 37 Data Act, German Data Act Implementation Act).',
      'This document was generated automatically by ev-monitor.net from the user\'s sync log. Polls run hourly against the Data Act interface provided by the manufacturer. ev-monitor.net is not a party to the complaint and does not give legal advice.',
      'A data set "without content" is a file provided by the portal that contains only the marker no_content_found.',
    ] },
  ]

  return {
    title: de ? 'Beleg zur Datenbereitstellung nach dem Data Act' : 'Evidence of data provision under the Data Act',
    subtitle: de ? `Stand ${f.today}, erstellt von ev-monitor.net` : `As of ${f.today}, generated by ev-monitor.net`,
    filename: `ev-monitor-data-act-${de ? 'beleg' : 'evidence'}-${f.vin}-${isoDay(now)}.pdf`,
    sections,
  }
}

export function buildEudaAuthorityFormValues(activity: EudaSyncActivity, locale: string, now: Date = new Date()): AuthorityFormValue[] {
  const f = facts(activity, locale, now)
  const c = activity.connection
  const de = f.de
  const ids = activity.identifiers.map(i => `${i.label}: ${i.value}`).join('\n')
  const respondent = RESPONDENT_BY_PROVIDER[activity.provider] ?? activity.provider

  const historyNote = c.history?.requestedAt && !c.history.importedAt
    ? (de ? ` Der am ${fmtDate(c.history.requestedAt, locale)} angeforderte Historien-Export (Request File) wurde bisher nicht bereitgestellt.` : ` The history export (Request File) requested on ${fmtDate(c.history.requestedAt, locale)} has not been provided.`)
    : ''
  const errorNote = c.lastError ? (de ? ` Letzte Fehlermeldung des Portals: ${c.lastError}` : ` Last portal error message: ${c.lastError}`) : ''

  const comment = de ? [
    `Ich bin Nutzer des vernetzten Fahrzeugs ${f.brand}, FIN ${f.vin}. Über das EU-Data-Act-Portal des Herstellers habe ich eine Datenanfrage angelegt und ev-monitor.net als Datenempfänger benannt (Art. 5 Data Act).`,
    '',
    `Sachverhalt (Stand ${f.today}): ${situationSentence(f)}${historyNote}${errorNote}`,
    '',
    `Ich habe den Hersteller am [Datum] unter ${activity.manufacturerContact} zur Bereitstellung innerhalb von 14 Tagen aufgefordert. [Keine Antwort / Antwort: ...]`,
    '',
    'Nach Art. 4 Abs. 1 und Art. 5 Abs. 1 der Verordnung (EU) 2023/2854 sind die Daten unverzüglich, unentgeltlich, kontinuierlich und in Echtzeit bereitzustellen. Das ist nicht der Fall. Ich bitte um Prüfung und Durchsetzung.',
    '',
    'Kennungen der Datenanfrage:',
    ids,
    '',
    'Als Anlage füge ich das Sync-Protokoll bei (Abfragen, Lieferungen, Fehlermeldungen mit Zeitstempel).',
  ] : [
    `I am the user of the connected vehicle ${f.brand}, VIN ${f.vin}. In the manufacturer's EU Data Act portal I created a data request and designated ev-monitor.net as data recipient (Art. 5 Data Act).`,
    '',
    `Facts (as of ${f.today}): ${situationSentence(f)}${historyNote}${errorNote}`,
    '',
    `On [date] I asked the manufacturer at ${activity.manufacturerContact} to provide the data within 14 days. [No reply / reply: ...]`,
    '',
    'Under Art. 4(1) and Art. 5(1) of Regulation (EU) 2023/2854 the data must be made available without undue delay, free of charge, continuously and in real time. This is not the case. I ask you to investigate and enforce.',
    '',
    'Identifiers of the data request:',
    ids,
    '',
    'Attached is the sync log (polls, deliveries, error messages with timestamps).',
  ]

  return [
    { key: 'role', field: 'Welche Rolle nach Data Act nehmen Sie ein?', value: de ? 'Nutzer' : 'Nutzer (User)' },
    { key: 'respondent', field: 'Über wen wollen Sie sich beschweren?', value: respondent },
    { key: 'violation', field: 'Welcher Verstoß wird gerügt?', value: de
      ? 'Art. 4 Abs. 1 und Art. 5 Abs. 1: Daten werden dem Nutzer bzw. dem benannten Datenempfänger nicht bereitgestellt'
      : 'Art. 4(1) and Art. 5(1): data is not made available to the user or the designated data recipient' },
    { key: 'product', field: 'Name Hersteller/Anbieter und Name Produkt/Dienst', value: `${f.brand}, ${de ? 'vernetztes Fahrzeug' : 'connected vehicle'}, FIN ${f.vin}` },
    { key: 'sector', field: 'Branche', value: de ? 'Automobil / Fahrzeuge' : 'Automotive / vehicles' },
    { key: 'personalData', field: 'Sind personenbezogene Daten betroffen?', value: de ? 'Ja (Fahrzeugdaten mit FIN, Ladeorte)' : 'Yes (vehicle data with VIN, charging locations)' },
    { key: 'priorComplaint', field: 'Wurde die Beschwerde bereits anderswo eingereicht?', value: de ? `Ja, beim Hersteller per E-Mail an ${activity.manufacturerContact} am [Datum]` : `Yes, with the manufacturer by e-mail to ${activity.manufacturerContact} on [date]` },
    { key: 'comment', field: 'Kommentar', value: comment.join('\n'), multiline: true },
  ]
}
