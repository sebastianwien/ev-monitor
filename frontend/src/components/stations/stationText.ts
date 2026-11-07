import type { MapStation } from '../../api/chargingStationService'
import { plugLine, type ChargeType } from '../../utils/stationMap'

type T = (key: string, ...args: any[]) => string

/** "CCS bis 150 kW", "4 Ladepunkte" für Liste und Säulen-Ebene; fehlende Angaben fallen weg. */
export function stationMeta(s: MapStation, type: ChargeType, t: T): string[] {
  const p = plugLine(s, type)
  const kw = p.kw != null ? Math.round(p.kw) : null
  const parts: string[] = []
  if (kw != null) parts.push(p.plugs ? t('stations.plugs_up_to', { plugs: p.plugs, kw }) : t('stations.up_to', { kw }))
  else if (p.plugs) parts.push(p.plugs)
  if (p.points > 0) parts.push(t('stations.points', p.points, { named: { n: p.points } }))
  // Umbruch nur an den Trennern, nie mitten in "12 Ladepunkte"
  return parts.map(part => part.replace(/ /g, '\u00a0'))
}

/** "EnBW L": Kartenname mit Tarifvariante, wenn es eine gibt. */
export const cardLabel = (name: string, variant: string | null) => variant ? `${name} ${variant}` : name

/** Datum der Prüfung in der Schreibweise der Sprache. */
export function formatDay(iso: string, locale: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  const tag = locale === 'en' ? 'en-GB' : locale === 'nb' ? 'nb-NO' : locale === 'sv' ? 'sv-SE' : 'de-DE'
  return new Date(y, m - 1, d).toLocaleDateString(tag, { day: '2-digit', month: '2-digit', year: 'numeric' })
}

/** Öffnungszeiten aus dem Register: es kennt nur "247", "Eingeschränkt" und "Keine Angabe"; Letzteres fällt weg. */
export function openingLabel(raw: string | null, t: T): string | null {
  if (!raw) return null
  const v = raw.trim().toLowerCase()
  if (v === '247' || v === '24/7') return t('stations.open_247')
  if (v === 'eingeschränkt') return t('stations.open_limited')
  if (v === 'keine angabe') return null
  return t('stations.opening_hours', { v: raw })
}

/** Bezahlarten aus dem Register ("RFID-Karte;Kreditkarte (NFC);") als lesbare Aufzählung. */
export function paymentLabel(raw: string | null): string | null {
  if (!raw) return null
  const parts = [...new Set(raw.split(';').map(p => p.trim()).filter(Boolean))]
  return parts.length ? parts.join(', ') : null
}

/** Ortsvorschlag mit Namen und Adresse ("Hauptbahnhof, Ernst-August-Platz 1, 30159 Hannover"), ohne Land. */
export function placeLabel(displayName: string, country?: string): string {
  const parts = displayName.split(',').map(p => p.trim()).filter(Boolean)
  if (country && parts.length > 1 && parts[parts.length - 1] === country) parts.pop()
  return parts.join(', ')
}
