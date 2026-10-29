import { describe, it, expect } from 'vitest'
import { toLocalDatetimeInput, tripFormFrom, newTripFormAt, tripFormError, tripPayload, splitTripTimes, joinTripTimes } from '../tripForm'

describe('tripForm', () => {
  it('übernimmt eine Fahrt in Ortszeit fürs Formular', () => {
    const start = new Date(2026, 7, 16, 9, 5)
    const f = tripFormFrom({ tripStartedAt: start.toISOString(), tripEndedAt: null, distanceKm: 42, routeType: null, socStart: 80, socEnd: null })
    expect(f).toEqual({ tripStartedAt: '2026-08-16T09:05', tripEndedAt: '', distanceKm: 42, routeType: '', socStart: 80, socEnd: '' })
  })

  it('neue Fahrt endet zum gegebenen Zeitpunkt und beginnt eine Stunde vorher', () => {
    const f = newTripFormAt(new Date(2026, 7, 16, 19, 20))
    expect(f).toMatchObject({ tripStartedAt: '2026-08-16T18:20', tripEndedAt: '2026-08-16T19:20', distanceKm: '', routeType: '' })
  })

  it('prüft Pflichtzeiten und Reihenfolge', () => {
    expect(tripFormError({ tripStartedAt: '', tripEndedAt: '2026-08-16T10:00' })).toBe('dashboard.trip_err_times_required')
    expect(tripFormError({ tripStartedAt: '2026-08-16T10:00', tripEndedAt: '2026-08-16T10:00' })).toBe('dashboard.trip_err_end_before_start')
    expect(tripFormError({ tripStartedAt: '2026-08-16T09:00', tripEndedAt: '2026-08-16T10:00' })).toBeNull()
  })

  it('Payload: leere Felder bleiben weg, Zeiten mit Offset der Ortszeit', () => {
    const p = tripPayload({ tripStartedAt: '2026-08-16T09:00', tripEndedAt: '2026-08-16T10:00', distanceKm: '', routeType: 'CITY', socStart: 80, socEnd: '' })
    expect(Object.keys(p).sort()).toEqual(['routeType', 'socStart', 'tripEndedAt', 'tripStartedAt'])
    expect(new Date(p.tripStartedAt).getTime()).toBe(new Date(2026, 7, 16, 9, 0).getTime())
    expect(p.socStart).toBe(80)
  })

  it('toLocalDatetimeInput', () => {
    expect(toLocalDatetimeInput(new Date(2026, 0, 2, 3, 4).toISOString())).toBe('2026-01-02T03:04')
  })

  it('teilt Start und Ende in Datum und zwei Uhrzeiten', () => {
    expect(splitTripTimes({ tripStartedAt: '2026-10-15T23:50', tripEndedAt: '2026-10-16T00:20' }))
      .toEqual({ date: '2026-10-15', start: '23:50', end: '00:20' })
    expect(splitTripTimes({ tripStartedAt: '', tripEndedAt: '' })).toEqual({ date: '', start: '', end: '' })
  })

  it('setzt Datum und Uhrzeiten zusammen, Ende vor Start zählt als nächster Tag', () => {
    expect(joinTripTimes('2026-10-15', '07:35', '07:51')).toEqual({ tripStartedAt: '2026-10-15T07:35', tripEndedAt: '2026-10-15T07:51' })
    expect(joinTripTimes('2026-10-31', '23:50', '00:20')).toEqual({ tripStartedAt: '2026-10-31T23:50', tripEndedAt: '2026-11-01T00:20' })
    expect(joinTripTimes('2026-12-31', '23:50', '00:20').tripEndedAt).toBe('2027-01-01T00:20')
  })

  it('gleiche Uhrzeit bleibt am selben Tag, damit die Prüfung "Ende vor Start" greift', () => {
    const f = joinTripTimes('2026-10-15', '07:35', '07:35')
    expect(f.tripEndedAt).toBe('2026-10-15T07:35')
    expect(tripFormError(f)).toBe('dashboard.trip_err_end_before_start')
  })

  it('fehlende Teile bleiben leer', () => {
    expect(joinTripTimes('', '07:35', '07:51')).toEqual({ tripStartedAt: '', tripEndedAt: '' })
    expect(joinTripTimes('2026-10-15', '07:35', '')).toEqual({ tripStartedAt: '2026-10-15T07:35', tripEndedAt: '' })
  })
})
