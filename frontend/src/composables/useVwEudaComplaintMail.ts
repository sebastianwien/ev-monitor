import type { EudaSyncActivity } from '../api/euDataActSyncService'

/**
 * Beschwerde an den Hersteller als mailto-Link. Der Nutzer schickt sie selbst aus seinem
 * Postfach; ev-monitor ist nicht Partei und speichert nichts. Der Text ist eine sachliche
 * Vorlage mit den Identifiern aus dem Sync-Protokoll, damit der Hersteller die Datenanfrage
 * ohne Rueckfrage findet. Deutsch fuer de, sonst Englisch. Unter 2000 Zeichen wegen mailto-Limits.
 */
export interface ComplaintMail {
  href: string
  subject: string
  body: string
}

const dateOf = (iso: string | null, locale: string) =>
  iso ? new Date(iso).toLocaleDateString(locale === 'de' ? 'de-DE' : 'en-GB', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '-'

export function buildEudaComplaintMail(activity: EudaSyncActivity, locale: string, now: Date = new Date()): ComplaintMail {
  const de = locale === 'de'
  const c = activity.connection
  const s = activity.summary
  const vin = activity.identifiers.find(i => i.label === 'VIN')?.value ?? '-'
  const ids = activity.identifiers.map(i => `${i.label}: ${i.value}`).join('\n')
  const since = dateOf(c.connectedAt, locale)
  const today = dateOf(now.toISOString(), locale)
  const empty = s.deliveriesSeen - s.deliveriesWithContent
  const errorLine = c.lastError ? (de ? `Letzte Fehlermeldung des Portals: ${c.lastError}` : `Last portal error message: ${c.lastError}`) : ''
  const historyLine = c.history?.requestedAt && !c.history.importedAt
    ? (de ? `Der am ${dateOf(c.history.requestedAt, locale)} angeforderte Historien-Export (Request File) wurde bisher nicht bereitgestellt.`
          : `The history export (Request File) requested on ${dateOf(c.history.requestedAt, locale)} has not been provided.`)
    : ''

  const subject = de
    ? `Beschwerde nach Art. 4 Data Act - keine Datenbereitstellung - FIN ${vin}`
    : `Complaint under Art. 4 Data Act - no data provided - VIN ${vin}`

  const body = de ? [
    'Sehr geehrte Damen und Herren,',
    '',
    `ich bin Nutzer des Fahrzeugs mit der FIN ${vin} und habe über das EU-Data-Act-Portal eine Datenanfrage angelegt. Die Daten sollen an den von mir gewählten Dritten ev-monitor.net weitergegeben werden.`,
    '',
    'Identifier zur Anfrage:',
    ids,
    '',
    `Sachverhalt (Stand ${today}): Seit dem ${since} wurden ${s.deliveriesSeen} Datensätze bereitgestellt, davon ${empty} ohne Inhalt (no_content_found) und ${s.deliveriesWithContent} mit Inhalt. ${historyLine} ${errorLine}`.replace(/\s+/g, ' ').trim(),
    '',
    'Nach Art. 4 Abs. 1 und Art. 5 Abs. 1 der Verordnung (EU) 2023/2854 (Data Act), anwendbar seit dem 12.09.2025, sind mir bzw. dem von mir benannten Dritten die Daten unverzüglich, unentgeltlich, kontinuierlich und in Echtzeit bereitzustellen. Das ist derzeit nicht der Fall.',
    '',
    'Ich bitte Sie, die Bereitstellung innerhalb von 14 Tagen sicherzustellen oder mir die Ursache mitzuteilen. Andernfalls werde ich Beschwerde bei der Bundesnetzagentur als zuständiger Behörde nach Art. 37 Data Act einlegen. Weitere Schritte behalte ich mir vor.',
    '',
    'Mit freundlichen Grüßen',
    '[Name]',
  ] : [
    'Dear Sir or Madam,',
    '',
    `I am the user of the vehicle with VIN ${vin} and have created a data request in the EU Data Act portal. The data is to be shared with the third party of my choice, ev-monitor.net.`,
    '',
    'Identifiers of the request:',
    ids,
    '',
    `Facts (as of ${today}): Since ${since}, ${s.deliveriesSeen} data sets have been provided, ${empty} of them without content (no_content_found) and ${s.deliveriesWithContent} with content. ${historyLine} ${errorLine}`.replace(/\s+/g, ' ').trim(),
    '',
    'Under Art. 4(1) and Art. 5(1) of Regulation (EU) 2023/2854 (Data Act), applicable since 12 September 2025, the data must be made available to me or to the third party I designate without undue delay, free of charge, continuously and in real time. This is currently not the case.',
    '',
    'I ask you to ensure provision within 14 days or to inform me of the cause. Otherwise I will lodge a complaint with the Bundesnetzagentur as the competent authority under Art. 37 Data Act. I reserve the right to take further steps.',
    '',
    'Kind regards',
    '[Name]',
  ]

  const text = body.join('\n')
  const href = `mailto:${activity.manufacturerContact}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(text)}`
  return { href, subject, body: text }
}
