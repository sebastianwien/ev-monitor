import { ANALYTICS_EVENTS, PLAUSIBLE_ONLY_GOALS, eventInfo, type EventArea } from '../../../services/analyticsEvents'
import type { SeoEventStat } from '../../../api/seoService'

/** ok: kommt an und ist Goal; no_goal: kommt an, kein Goal; silent: 30 Tage nichts; unknown: nicht im Katalog. */
export type EventStatus = 'ok' | 'no_goal' | 'silent' | 'unknown'

export interface EventRow {
  name: string
  title: string
  question: string | null
  where: string | null
  props: Record<string, string> | null
  area: EventArea | null
  visitors: number
  events: number
  status: EventStatus
}

export const STATUS_LABEL: Record<EventStatus, string> = {
  ok: 'Goal',
  no_goal: 'kein Goal',
  silent: 'still',
  unknown: 'unbekannt',
}

export const STATUS_HINT: Record<EventStatus, string> = {
  ok: 'Kommt an und ist in Plausible als Goal angelegt.',
  no_goal: 'Kommt an, ist in Plausible aber nicht als Goal angelegt. Unter Site Settings > Goals anlegen, sonst gibt es keine Conversion-Zahlen.',
  silent: 'In den letzten 30 Tagen kam nichts an. Seltenes Ereignis, tote Stelle oder kaputtes Tracking.',
  unknown: 'Kommt in Plausible an, steht aber nicht im Katalog. Meist ein altes Event aus einer früheren Version.',
}

/** Katalog und Plausible zusammenführen: jeder bekannte Name einmal, dazu unbekannte aus Plausible. */
export function eventRows(stats: SeoEventStat[]): EventRow[] {
  const byName = new Map<string, SeoEventStat>()
  for (const s of stats) if (!byName.has(s.name)) byName.set(s.name, s)

  const names = [...Object.keys(ANALYTICS_EVENTS), ...Object.keys(PLAUSIBLE_ONLY_GOALS)]
  for (const s of byName.keys()) if (!names.includes(s)) names.push(s)

  return names.map(name => {
    const info = eventInfo(name)
    const s = byName.get(name)
    const status: EventStatus = !info ? 'unknown' : !s ? 'silent' : s.goal ? 'ok' : 'no_goal'
    return {
      name,
      title: info?.title ?? name,
      question: info?.question ?? null,
      where: info?.where ?? null,
      props: info?.props ?? null,
      area: info?.area ?? null,
      visitors: s?.visitors ?? 0,
      events: s?.events ?? 0,
      status,
    }
  })
}
