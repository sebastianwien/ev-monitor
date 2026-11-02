import api from '../../../api/axios'
import { dayRange, type DayLog, type DayTrip, type OdometerGap } from '../../../composables/storyDay'
import type { EvLogResponse } from '../../dashboard/LogEditPanel.vue'

export interface DayData {
  logs: (EvLogResponse & DayLog)[]
  trips: DayTrip[]
  /** Strecke ohne Aufzeichnung, die an diesem Tag endet; ohne Antwort bleibt der Tag ohne. */
  gaps: OdometerGap[]
}

/** So lange gilt ein Vorabruf; länger liegen Fingerdruck und Öffnen nie auseinander. */
export const PREFETCH_TTL_MS = 10_000

const prefetched = new Map<string, { at: number; data: Promise<DayData> }>()
const keyOf = (carId: string, day: string) => `${carId}|${day}`

function fetchDay(carId: string, day: string): Promise<DayData> {
  const params = { carId, ...dayRange(day) }
  return Promise.all([
    api.get('/logs', { params }), api.get('/trips', { params }), api.get('/trips/gaps', { params }).catch(() => null),
  ]).then(([l, tr, g]) => ({ logs: l.data, trips: tr.data, gaps: Array.isArray(g?.data) ? g.data : [] }))
}

/**
 * Beim Fingerdruck auf einen Tag starten die Requests des Tagesblatts schon, das Öffnen beim Loslassen
 * nimmt sie dann (gut 100 ms Vorsprung). Ein Vorabruf wird genau einmal genommen; danach fragt jedes
 * Laden frisch, etwa nach dem Speichern.
 */
export function prefetchDay(carId: string, day: string): void {
  const key = keyOf(carId, day)
  const hit = prefetched.get(key)
  if (hit && Date.now() - hit.at <= PREFETCH_TTL_MS) return
  const data = fetchDay(carId, day)
  // Ein gescheiterter Vorabruf gilt nicht: das Öffnen fragt dann selbst
  data.catch(() => { if (prefetched.get(key)?.data === data) prefetched.delete(key) })
  prefetched.set(key, { at: Date.now(), data })
}

export function loadDay(carId: string, day: string): Promise<DayData> {
  const key = keyOf(carId, day)
  const hit = prefetched.get(key)
  prefetched.delete(key)
  if (!hit || Date.now() - hit.at > PREFETCH_TTL_MS) return fetchDay(carId, day)
  // Scheitert der Vorabruf erst jetzt, fragt das Öffnen selbst noch einmal
  return hit.data.catch(() => fetchDay(carId, day))
}
