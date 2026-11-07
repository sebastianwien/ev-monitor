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
  return parts
}

/** "EnBW L": Kartenname mit Tarifvariante, wenn es eine gibt. */
export const cardLabel = (name: string, variant: string | null) => variant ? `${name} ${variant}` : name

/** Datum der Prüfung in der Schreibweise der Sprache. */
export function formatDay(iso: string, locale: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  const tag = locale === 'en' ? 'en-GB' : locale === 'nb' ? 'nb-NO' : locale === 'sv' ? 'sv-SE' : 'de-DE'
  return new Date(y, m - 1, d).toLocaleDateString(tag, { day: '2-digit', month: '2-digit', year: 'numeric' })
}
