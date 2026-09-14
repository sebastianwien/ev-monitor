import { describe, it, expect } from 'vitest'
import {
  emptyLogForm, canProceed, applyPlace, buildLogPayload, buildLogUpdatePayload, missingRequired, netEnergyKwh, socToKwh,
} from '../wizardLogic'
import type { NearbyStation } from '../../../composables/useNearbyStations'
import { datetimeLocalToUtcIso } from '../../../utils/datetime'

const ionity: NearbyStation = {
  name: 'IONITY', known: true, distanceMeters: 40, maxAcKw: null, maxDcKw: 350, fastCharging: true, chargePoints: 6,
  geohash: 'u33dc0c', address: 'A 9 Rasthof, 91710 Gunzenhausen', plugTypes: ['CCS'], registerId: 1,
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
    expect(f).toMatchObject({ isPublicCharging: true, chargingType: 'DC', cpoName: 'IONITY',
      chargingSite: { name: 'IONITY', geohash: 'u33dc0c' } })
    applyPlace(f, { kind: 'station', station: { ...ionity, name: 'EWE Go', fastCharging: false } })
    expect(f).toMatchObject({ chargingType: 'AC', cpoName: 'EWE Go' })
  })

  it('Zuletzt genutzter Standort trägt den Katalognamen als Anbieter, sonst den Standortnamen', () => {
    const f = emptyLogForm()
    const site = { id: 's1', name: 'Stadtwerke Musterstadt', cpoName: null, geohash: 'u33dc0c', maxAcKw: 22, maxDcKw: null,
      chargePoints: 2, fastCharging: false, address: null, plugTypes: ['Typ 2'], lastUsedAt: '2026-09-10T10:00:00', usageCount: 2 }
    applyPlace(f, { kind: 'site', site })
    expect(f).toMatchObject({ isPublicCharging: true, chargingType: 'AC', cpoName: 'Stadtwerke Musterstadt',
      chargingSite: { name: 'Stadtwerke Musterstadt', geohash: 'u33dc0c' } })
    applyPlace(f, { kind: 'site', site: { ...site, cpoName: 'Kaufland', fastCharging: true } })
    expect(f).toMatchObject({ chargingType: 'DC', cpoName: 'Kaufland' })
  })

  it('Zuhause und anderer Anbieter löschen den Standortverweis', () => {
    const f = emptyLogForm()
    applyPlace(f, { kind: 'station', station: ionity })
    applyPlace(f, { kind: 'home' })
    expect(f.chargingSite).toBeNull()
    applyPlace(f, { kind: 'station', station: ionity })
    applyPlace(f, { kind: 'other', cpoName: 'EnBW' })
    expect(f.chargingSite).toBeNull()
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
    f.chargingSite = { name: 'IONITY', geohash: 'u33dc0c' }
    const p = buildLogPayload(f, 'car-1', true)
    expect(p).toMatchObject({ kwhAtVehicle: 10, costEur: 0, ocrUsed: true })
    expect(p).not.toHaveProperty('cpoName')
    expect(p).not.toHaveProperty('chargingSite')
  })

  it('Standortverweis geht nur bei öffentlicher Ladung mit', () => {
    const f = emptyLogForm()
    f.kwhCharged = 5; f.costEur = 2; f.isPublicCharging = true
    f.chargingSite = { name: 'IONITY', geohash: 'u33dc0c' }
    expect(buildLogPayload(f, 'car-1', false)).toMatchObject({ chargingSite: { name: 'IONITY', geohash: 'u33dc0c' } })
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

describe('buildLogUpdatePayload', () => {
  it('ist der Anlage-Payload ohne Auto und OCR-Marker', () => {
    const f = emptyLogForm()
    f.kwhCharged = 40; f.costEur = 10; f.odometerKm = 1000; f.socAfterChargePercent = 80
    const p = buildLogUpdatePayload(f)
    expect(p).toMatchObject({ kwhCharged: 40, costEur: 10, odometerKm: 1000, socAfterChargePercent: 80 })
    expect(p).not.toHaveProperty('carId')
    expect(p).not.toHaveProperty('ocrUsed')
  })
})

describe('missingRequired', () => {
  it('nennt die fehlenden Pflichtwerte in Wizard-Reihenfolge', () => {
    const f = emptyLogForm()
    expect(missingRequired(f)).toEqual(['energy', 'odometer', 'soc', 'cost'])
    f.kwhAtVehicle = 5; f.socAfterChargePercent = 80
    expect(missingRequired(f)).toEqual(['odometer', 'cost'])
    f.odometerKm = 1; f.costEur = 0
    expect(missingRequired(f)).toEqual([])
  })
})
