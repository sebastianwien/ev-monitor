import { describe, it, expect } from 'vitest'
import { pauseBeforeTripMinutes, isRestBreak } from '../tripCalculations'

/**
 * Fahrten kommen in Ketten: kurz weg, kurz zurueck, dann Stunden Ruhe. Die Pausenzeile im
 * Log-Feed zeigt allein diese Ruhe - der Tageswechsel hat sein eigenes Datumsband.
 *
 * Trips einer Fahrtgruppe sind absteigend sortiert (neuester zuerst) - die frueher gefahrene
 * Fahrt steht also beim hoeheren Index.
 */
const trips = [
  // Kette 2: 16:41 -> 17:02 (7 min Pause), davor 2h43 Ruhe
  { tripStartedAt: '2026-08-20T17:02:00', tripEndedAt: '2026-08-20T17:20:00' },
  { tripStartedAt: '2026-08-20T16:41:00', tripEndedAt: '2026-08-20T16:55:00' },
  // Kette 1
  { tripStartedAt: '2026-08-20T13:44:00', tripEndedAt: '2026-08-20T13:58:00' },
  { tripStartedAt: '2026-08-20T13:31:00', tripEndedAt: '2026-08-20T13:45:00' },
  // Vortag
  { tripStartedAt: '2026-08-19T18:00:00', tripEndedAt: '2026-08-19T18:30:00' },
]

describe('pauseBeforeTripMinutes', () => {
  it('misst die Ruhe zwischen der frueheren Ankunft und dieser Abfahrt', () => {
    expect(pauseBeforeTripMinutes(trips, 0)).toBe(7)      // 16:55 -> 17:02
    expect(pauseBeforeTripMinutes(trips, 1)).toBe(163)    // 13:58 -> 16:41
  })

  it('ist null fuer die aelteste Fahrt - davor liegt nichts, was wir sehen', () => {
    expect(pauseBeforeTripMinutes(trips, trips.length - 1)).toBeNull()
  })

  it('ist null ohne verwertbare Zeitstempel', () => {
    const broken = [{ tripStartedAt: '2026-08-20T17:02:00', tripEndedAt: null }, { tripStartedAt: null, tripEndedAt: null }]
    expect(pauseBeforeTripMinutes(broken, 0)).toBeNull()
  })

  it('faellt bei fehlender Ankunft auf die Abfahrt der frueheren Fahrt zurueck', () => {
    // Ohne tripEndedAt waere die Pause sonst unbekannt; die Abfahrt ist die naechstbeste
    // Naeherung und liegt immer vor der Ankunft.
    const partial = [
      { tripStartedAt: '2026-08-20T17:02:00', tripEndedAt: '2026-08-20T17:20:00' },
      { tripStartedAt: '2026-08-20T16:41:00', tripEndedAt: null },
    ]
    expect(pauseBeforeTripMinutes(partial, 0)).toBe(21)
  })
})

describe('isRestBreak', () => {
  it('schweigt bei kurz aufeinanderfolgenden Fahrten', () => {
    expect(isRestBreak(trips, 0)).toBe(false)  // 7 min
  })

  it('meldet eine laengere Ruhe', () => {
    expect(isRestBreak(trips, 1)).toBe(true)   // 2h43
  })

  it('kennt keine Tagesgrenze - der Aufrufer reicht die Fahrten eines Tages herein', () => {
    // Der Feed gruppiert vorher nach Tagen und ruft diese Funktion je Tag auf. Zwischen zwei
    // Tagen steht das Datumsband, dort kommt die Funktion gar nicht zum Zug.
    const longRestSameDay = [
      { tripStartedAt: '2026-08-20T23:40:00', tripEndedAt: '2026-08-20T23:55:00' },
      { tripStartedAt: '2026-08-20T08:00:00', tripEndedAt: '2026-08-20T08:30:00' },
    ]
    expect(pauseBeforeTripMinutes(longRestSameDay, 0)).toBe(15 * 60 + 10)
    expect(isRestBreak(longRestSameDay, 0)).toBe(true)
  })

  it('ist nie wahr fuer die aelteste Fahrt - eine Pause liegt zwischen zwei Fahrten', () => {
    expect(isRestBreak(trips, trips.length - 1)).toBe(false)
  })

  it('schweigt, wenn die Pause nicht bestimmbar ist', () => {
    const broken = [
      { tripStartedAt: '2026-08-20T17:02:00', tripEndedAt: '2026-08-20T17:20:00' },
      { tripStartedAt: null, tripEndedAt: null },
    ]
    expect(isRestBreak(broken, 0)).toBe(false)
  })
})
