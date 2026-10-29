/** Formular einer Fahrt (TripForm): Werte als datetime-local und Eingabefelder, '' = leer. */
export interface TripFormValues {
  tripStartedAt: string
  tripEndedAt: string
  distanceKm?: number | ''
  routeType?: string
  socStart?: number | ''
  socEnd?: number | ''
}

const pad = (n: number) => String(n).padStart(2, '0')
const localInput = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`

/** UTC/Offset-ISO als datetime-local in Ortszeit des Browsers */
export function toLocalDatetimeInput(isoString: string): string {
  return localInput(new Date(isoString))
}

/** datetime-local ("2026-04-24T14:30") als OffsetDateTime mit dem Offset der Ortszeit an diesem Tag (Sommer-/Winterzeit) */
export function toOffsetDateTime(localDt: string): string {
  const off = -new Date(localDt).getTimezoneOffset()
  const sign = off >= 0 ? '+' : '-'
  const abs = Math.abs(off)
  return `${localDt}:00${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`
}

interface TripLike {
  tripStartedAt?: string | null
  tripEndedAt?: string | null
  distanceKm?: number | null
  routeType?: string | null
  socStart?: number | null
  socEnd?: number | null
}

export function tripFormFrom(trip: TripLike): Required<TripFormValues> {
  return {
    tripStartedAt: trip.tripStartedAt ? toLocalDatetimeInput(trip.tripStartedAt) : '',
    tripEndedAt: trip.tripEndedAt ? toLocalDatetimeInput(trip.tripEndedAt) : '',
    distanceKm: trip.distanceKm ?? '',
    routeType: trip.routeType ?? '',
    socStart: trip.socStart ?? '',
    socEnd: trip.socEnd ?? '',
  }
}

/** Neue Fahrt: endet zum gegebenen Zeitpunkt, beginnt eine Stunde vorher. */
export function newTripFormAt(end: Date): TripFormValues {
  return { tripStartedAt: localInput(new Date(end.getTime() - 3_600_000)), tripEndedAt: localInput(end), distanceKm: '', routeType: '' }
}

type TripTimes = Pick<TripFormValues, 'tripStartedAt' | 'tripEndedAt'>

/** Formular zeigt ein Datum und zwei Uhrzeiten: Datum = Starttag */
export function splitTripTimes(f: TripTimes): { date: string; start: string; end: string } {
  return { date: f.tripStartedAt.slice(0, 10) || f.tripEndedAt.slice(0, 10), start: f.tripStartedAt.slice(11, 16), end: f.tripEndedAt.slice(11, 16) }
}

/** Gegenstück zu splitTripTimes: liegt das Ende vor dem Start, endet die Fahrt am nächsten Tag */
export function joinTripTimes(date: string, start: string, end: string): TripTimes {
  if (!date) return { tripStartedAt: '', tripEndedAt: '' }
  const [y, m, d] = date.split('-').map(Number)
  const endDate = start && end && end < start ? localInput(new Date(y, m - 1, d + 1)).slice(0, 10) : date
  return { tripStartedAt: start ? `${date}T${start}` : '', tripEndedAt: end ? `${endDate}T${end}` : '' }
}

/** i18n-Schlüssel des ersten Fehlers oder null */
export function tripFormError(f: Pick<TripFormValues, 'tripStartedAt' | 'tripEndedAt'>): string | null {
  if (!f.tripStartedAt || !f.tripEndedAt) return 'dashboard.trip_err_times_required'
  if (new Date(f.tripEndedAt) <= new Date(f.tripStartedAt)) return 'dashboard.trip_err_end_before_start'
  return null
}

/** Body für POST /trips und PATCH /trips/{id}; leere Felder bleiben weg. */
export function tripPayload(f: TripFormValues): Record<string, any> {
  const p: Record<string, any> = {
    tripStartedAt: toOffsetDateTime(f.tripStartedAt),
    tripEndedAt: toOffsetDateTime(f.tripEndedAt),
  }
  if (f.distanceKm !== '' && f.distanceKm != null) p.distanceKm = Number(f.distanceKm)
  if (f.routeType) p.routeType = f.routeType
  if (f.socStart !== '' && f.socStart != null) p.socStart = Number(f.socStart)
  if (f.socEnd !== '' && f.socEnd != null) p.socEnd = Number(f.socEnd)
  return p
}
