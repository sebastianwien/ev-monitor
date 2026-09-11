import { describe, it, expect } from 'vitest'
import {
  emptyLogForm, canProceed, applyPlace, buildLogPayload, netEnergyKwh, socToKwh,
} from '../wizardLogic'
import type { NearbyStation } from '../../../composables/useNearbyStations'
import { datetimeLocalToUtcIso } from '../../../utils/datetime'

const ionity: NearbyStation = {
  name: 'IONITY', known: true, distanceMeters: 40, maxPowerKw: 350, fastCharging: true, chargePoints: 6,
}

describe('canProceed', () => {
  it('Schritt 1 braucht eine Ortswahl', () => {
    const f = emptyLogForm()
    expect(canProceed(1, f, { place: null })).toBe(false)
    expect(canProceed(1, f, { place: 'home' })).toBe(true)
  })

  it('Schritt 2 braucht Energie an Säule oder Fahrzeug', () => {
    const f = emptyLogForm()
    expect(canProceed(2, f, { place: 'home' })).toBe(false)
    f.kwhAtVehicle = 12
    expect(canProceed(2, f, { place: 'home' })).toBe(true)
    f.kwhAtVehicle = null; f.kwhCharged = 0
    expect(canProceed(2, f, { place: 'home' })).toBe(false)
  })

  it('Schritt 3 braucht Tachostand und Ladestand danach', () => {
    const f = emptyLogForm()
    f.odometerKm = 48211
    expect(canProceed(3, f, { place: 'home' })).toBe(false)
    f.socAfterChargePercent = 80
    expect(canProceed(3, f, { place: 'home' })).toBe(true)
    f.odometerKm = 0
    expect(canProceed(3, f, { place: 'home' })).toBe(false)
  })

  it('Schritt 4 braucht Kosten, auch 0 ist gültig', () => {
    const f = emptyLogForm()
    expect(canProceed(4, f, { place: 'home' })).toBe(false)
    f.costEur = 0
    expect(canProceed(4, f, { place: 'home' })).toBe(true)
  })
})

describe('applyPlace', () => {
  it('Zuhause ist privat und AC', () => {
    const f = emptyLogForm()
    f.isPublicCharging = true; f.cpoName = 'IONITY'; f.chargingType = 'DC'
    applyPlace(f, { kind: 'home' })
    expect(f).toMatchObject({ isPublicCharging: false, chargingType: 'AC', cpoName: null })
  })

  it('Registerstandort setzt öffentlich, Anbieter und Ladeart aus der Säule', () => {
    const f = emptyLogForm()
    applyPlace(f, { kind: 'station', station: ionity })
    expect(f).toMatchObject({ isPublicCharging: true, chargingType: 'DC', cpoName: 'IONITY' })
    applyPlace(f, { kind: 'station', station: { ...ionity, name: 'EWE Go', fastCharging: false } })
    expect(f).toMatchObject({ chargingType: 'AC', cpoName: 'EWE Go' })
  })

  it('Anderer Anbieter ist öffentlich und behält die gewählte Ladeart', () => {
    const f = emptyLogForm()
    f.chargingType = 'DC'
    applyPlace(f, { kind: 'other', cpoName: 'EnBW' })
    expect(f).toMatchObject({ isPublicCharging: true, chargingType: 'DC', cpoName: 'EnBW' })
  })
})

describe('buildLogPayload', () => {
  it('rundet und lässt leere Felder weg', () => {
    const f = emptyLogForm()
    f.kwhCharged = 42.345; f.costEur = 29.615; f.odometerKm = 48211; f.socAfterChargePercent = 80
    f.maxChargingPowerKw = 187.456; f.latitude = 52.52; f.longitude = 13.405
    f.isPublicCharging = true; f.cpoName = 'IONITY'; f.chargingType = 'DC'
    const p = buildLogPayload(f, 'car-1', false)
    expect(p).toMatchObject({
      carId: 'car-1', kwhCharged: 42.35, costEur: 29.62, odometerKm: 48211, socAfterChargePercent: 80,
      maxChargingPowerKw: 187.46, latitude: 52.52, longitude: 13.405,
      isPublicCharging: true, cpoName: 'IONITY', chargingType: 'DC', routeType: 'COMBINED', tireType: 'SUMMER',
    })
    expect(p).not.toHaveProperty('kwhAtVehicle')
    expect(p).not.toHaveProperty('socBeforeChargePercent')
    expect(p).not.toHaveProperty('ocrUsed')
    expect(p).not.toHaveProperty('loggedAt')
  })

  it('cpoName nur bei öffentlicher Ladung, ocrUsed nur wenn genutzt', () => {
    const f = emptyLogForm()
    f.kwhAtVehicle = 10; f.costEur = 0; f.cpoName = 'IONITY'; f.isPublicCharging = false
    const p = buildLogPayload(f, 'car-1', true)
    expect(p).toMatchObject({ kwhAtVehicle: 10, costEur: 0, ocrUsed: true })
    expect(p).not.toHaveProperty('cpoName')
  })

  it('Währungsdaten und Zeitstempel werden übernommen', () => {
    const f = emptyLogForm()
    f.kwhCharged = 1; f.costEur = 1; f.costExchangeRate = 11.2; f.costCurrency = 'NOK'
    f.loggedAt = '2026-09-11T10:00'
    const p = buildLogPayload(f, 'car-1', false)
    expect(p).toMatchObject({ costExchangeRate: 11.2, costCurrency: 'NOK' })
    expect(p.loggedAt).toBe(datetimeLocalToUtcIso('2026-09-11T10:00'))
  })
})

describe('SoC-Rechnung', () => {
  it('rechnet SoC in kWh über die effektive Kapazität', () => {
    expect(socToKwh(80, 77)).toBeCloseTo(61.6, 1)
    expect(socToKwh(80, null)).toBeNull()
  })

  it('Netto-Energie aus SoC vorher und danach, nie negativ', () => {
    expect(netEnergyKwh(20, 80, 77)).toBeCloseTo(46.2, 1)
    expect(netEnergyKwh(null, 80, 77)).toBeNull()
    expect(netEnergyKwh(90, 80, 77)).toBe(0)
  })
})
