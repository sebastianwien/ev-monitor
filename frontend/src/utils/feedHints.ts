/**
 * Hinweise über dem Log-Feed, in einer Karte statt als Bannerstapel.
 *
 * Erst die Aufgaben (unplausibel, ohne Preis), dann höchstens ein Angebot, zuletzt der
 * einmalige Tipp. Gedeckelt, damit der Feed auf dem Handy im ersten Bildschirm beginnt:
 * was über die Grenze fällt, rückt nach, sobald ein Hinweis erledigt oder ausgeblendet ist.
 */
export type FeedHintId = 'implausible' | 'priceless' | 'trips' | 'live' | 'autosync' | 'supporter' | 'cost_tip'

export interface FeedHintFlags {
  implausible: boolean
  priceless: boolean
  /** Auto einer Marke mit Fahrten, aber noch ohne Fahrt: Fahrten automatisch erfassen */
  trips: boolean
  live: boolean
  autosync: boolean
  supporter: boolean
  costTip: boolean
}

export const MAX_FEED_HINTS = 3

export function feedHints(f: FeedHintFlags): FeedHintId[] {
  const hints: FeedHintId[] = []
  if (f.implausible) hints.push('implausible')
  if (f.priceless) hints.push('priceless')
  const offer: FeedHintId | null = f.trips ? 'trips' : f.live ? 'live' : f.autosync ? 'autosync' : f.supporter ? 'supporter' : null
  if (offer) hints.push(offer)
  if (f.costTip) hints.push('cost_tip')
  return hints.slice(0, MAX_FEED_HINTS)
}

export interface TripsHintInput {
  /** Marke liefert Fahrten (brandDeliversTrips) */
  brandDeliversTrips: boolean
  /** Verbindung steht schon (Tesla, Polestar), die Fahrten kommen also ohnehin */
  connected: boolean
  /** Fahrten werden noch geladen: dann nicht anzeigen, sonst blitzt der Hinweis auf */
  loading: boolean
  hasTrip: boolean
  /** Für dieses Auto weggeklickt */
  dismissed: boolean
}

/** Hinweis "Fahrten automatisch erfassen": nur, wenn er dem Auto wirklich Fahrten bringt. Mit der ersten Fahrt verschwindet er. */
export function tripsHintVisible(i: TripsHintInput): boolean {
  return i.brandDeliversTrips && !i.connected && !i.loading && !i.hasTrip && !i.dismissed
}
