import { describe, it, expect } from 'vitest'
import {
  emptyLogForm, canProceed, applyPlace, applyVoiceDraft, voiceCost, canJumpToReview, voiceFlags, type VoiceDraft, type VoiceDraftFields, placeKind, buildLogPayload, buildLogUpdatePayload, missingRequired, netEnergyKwh, socToKwh, optionalFacts,
} from '../wizardLogic'
import type { NearbyStation } from '../../../composables/useNearbyStations'
import type { KnownPlace } from '../../../composables/useKnownPlaces'
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

  it('Schritt 2 braucht Energie, Tachostand, Ladestand danach und Kosten zusammen', () => {
    const f = emptyLogForm()
    expect(canProceed(2, f, { place: 'home' })).toBe(false)
    f.kwhAtVehicle = 12; f.odometerKm = 48_210; f.socAfterChargePercent = 80
    expect(canProceed(2, f, { place: 'home' })).toBe(false)
    f.costEur = 0
    expect(canProceed(2, f, { place: 'home' })).toBe(true)
    f.kwhAtVehicle = null; f.kwhCharged = 0
    expect(canProceed(2, f, { place: 'home' })).toBe(false)
    f.kwhCharged = 30; f.odometerKm = 0
    expect(canProceed(2, f, { place: 'home' })).toBe(false)
  })

  it('Die Prüfseite blockiert nie', () => {
    expect(canProceed(3, emptyLogForm(), { place: 'home' })).toBe(true)
  })
})

describe('applyPlace mit bekanntem Ort', () => {
  const site = { id: 's1', name: 'EnBW Kaufland', cpoName: 'EnBW', geohash: 'u33dc0c', maxAcKw: null, maxDcKw: 150, chargePoints: 4,
    fastCharging: true, address: null, plugTypes: ['CCS'], lastUsedAt: '2026-09-24T10:00:00', usageCount: 7 }
  const known = (o: Partial<KnownPlace>): KnownPlace => ({ geohash: 'u33dc0', isPublic: false, usageCount: 4, lastUsedAt: '2026-09-27T18:00:00',
    cpoName: null, lastProviderId: null, placeName: null, site: null, here: true, ...o })

  it('Säule: wie der zuletzt genutzte Standort, samt Ladekarte vom letzten Mal', () => {
    const f = emptyLogForm()
    const place = known({ site, isPublic: true, geohash: 'u33dc0c', lastProviderId: 'card-1' })
    applyPlace(f, { kind: 'known', place })
    expect(f).toMatchObject({ isPublicCharging: true, chargingType: 'DC', cpoName: 'EnBW',
      chargingSite: { name: 'EnBW Kaufland', geohash: 'u33dc0c' }, chargingProviderId: 'card-1' })
    expect(placeKind({ kind: 'known', place })).toBe('site')
  })

  it('öffentlich ohne Säule: Anbieter vom letzten Mal, kein Standortverweis', () => {
    const f = emptyLogForm(); f.chargingType = 'DC'
    const place = known({ isPublic: true, geohash: 'u33dc0c', cpoName: 'Ionity', lastProviderId: 'card-2' })
    applyPlace(f, { kind: 'known', place })
    expect(f).toMatchObject({ isPublicCharging: true, chargingType: 'DC', cpoName: 'Ionity', chargingSite: null, chargingProviderId: 'card-2' })
    expect(placeKind({ kind: 'known', place })).toBe('other')
  })

  it('privat ist Zuhause ohne Ladekarte', () => {
    const f = emptyLogForm(); f.chargingProviderId = 'stale'
    const place = known({ isPublic: false, lastProviderId: 'card-3' })
    applyPlace(f, { kind: 'known', place })
    expect(f).toMatchObject({ isPublicCharging: false, chargingType: 'AC', cpoName: null, chargingSite: null, chargingProviderId: null })
    expect(placeKind({ kind: 'known', place })).toBe('home')
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

describe('optionalFacts', () => {
  it('liefert nur gesetzte Werte in fester Reihenfolge, Zeit und Vorbelegungen immer', () => {
    const f = emptyLogForm()
    expect(optionalFacts(f)).toEqual([
      { kind: 'time', value: null },
      { kind: 'route', value: 'COMBINED' },
      { kind: 'tires', value: 'SUMMER' },
    ])
  })

  it('nimmt Akku vorher, Dauer und Spitzenleistung auf, sobald sie eingetragen sind', () => {
    const f = { ...emptyLogForm(), loggedAt: '2026-09-14T20:00', socBeforeChargePercent: 20, chargeDurationMinutes: 35, maxChargingPowerKw: 150, routeType: 'HIGHWAY' as const }
    expect(optionalFacts(f).map(x => x.kind)).toEqual(['time', 'socBefore', 'route', 'tires', 'duration', 'peak'])
    expect(optionalFacts(f)[0]).toEqual({ kind: 'time', value: '2026-09-14T20:00' })
  })

  it('kann die Zeit weglassen, wenn sie als eigene Kachel steht', () => {
    expect(optionalFacts(emptyLogForm(), { withTime: false }).map(x => x.kind)).toEqual(['route', 'tires'])
  })
})

describe('applyVoiceDraft', () => {
  const fields = (over: Partial<VoiceDraftFields> = {}): VoiceDraftFields => ({
    kwhCharged: null, kwhAtVehicle: null, socBefore: null, socAfter: null, odometerKm: null, costEur: null, pricePerKwh: null,
    loggedAt: null, chargeDurationMinutes: null, maxChargingPowerKw: null, chargingType: null, routeType: null, tireType: null,
    uncertain: [], ...over,
  })
  const draft = (over: Partial<VoiceDraft> = {}): VoiceDraft => ({
    transcript: 'x', fields: fields(), place: null, chargingProviderId: null, usage: { limit: null, remaining: null, resetsOn: '2026-11-01' }, ...over,
  })

  it('übernimmt die gesagten Zahlen in die Formularfelder', () => {
    const f = emptyLogForm()
    applyVoiceDraft(f, draft({ fields: fields({ kwhCharged: 32, socBefore: 20, socAfter: 80, odometerKm: 48_210,
      loggedAt: '2026-10-02T19:00', chargeDurationMinutes: 25, maxChargingPowerKw: 140, routeType: 'HIGHWAY', tireType: 'WINTER' }) }))
    expect(f).toMatchObject({ kwhCharged: 32, socBeforeChargePercent: 20, socAfterChargePercent: 80, odometerKm: 48_210,
      loggedAt: '2026-10-02T19:00', chargeDurationMinutes: 25, maxChargingPowerKw: 140, routeType: 'HIGHWAY', tireType: 'WINTER' })
  })

  it('Ungesagtes überschreibt nichts: Reifen und Strecke vom letzten Mal bleiben', () => {
    const f = emptyLogForm(); f.tireType = 'WINTER'; f.routeType = 'CITY'
    applyVoiceDraft(f, draft())
    expect(f.tireType).toBe('WINTER'); expect(f.routeType).toBe('CITY')
  })

  it('Kosten setzt es nicht direkt - die laufen über die Kosteneingabe', () => {
    const f = emptyLogForm()
    applyVoiceDraft(f, draft({ fields: fields({ costEur: 18.4 }) }))
    expect(f.costEur).toBeNull()
  })

  it('ohne Ort bleibt die Ortswahl offen', () => {
    const f = emptyLogForm()
    expect(applyVoiceDraft(f, draft())).toBeNull()
  })

  it('Säule aus der Umkreissuche wird zur Ortswahl, die Ladekarte kommt mit', () => {
    const f = emptyLogForm()
    const choice = applyVoiceDraft(f, draft({ place: { kind: 'station', station: ionity, site: null, cpoName: null }, chargingProviderId: 'p1' }))
    expect(choice).toEqual({ kind: 'station', station: ionity })
    expect(f).toMatchObject({ isPublicCharging: true, chargingType: 'DC', cpoName: 'IONITY', chargingProviderId: 'p1',
      chargingSite: { name: 'IONITY', geohash: 'u33dc0c' } })
  })

  it('gesagte Ladeart schlägt die Ladeart der Säule', () => {
    const f = emptyLogForm()
    applyVoiceDraft(f, draft({ place: { kind: 'station', station: ionity, site: null, cpoName: null }, fields: fields({ chargingType: 'AC' }) }))
    expect(f.chargingType).toBe('AC')
  })

  it('Zuhause und freier Betreiber mappen 1:1', () => {
    const f = emptyLogForm()
    expect(applyVoiceDraft(f, draft({ place: { kind: 'home', station: null, site: null, cpoName: null } }))).toEqual({ kind: 'home' })
    expect(f.isPublicCharging).toBe(false)
    expect(applyVoiceDraft(f, draft({ place: { kind: 'other', station: null, site: null, cpoName: 'Aral pulse' } })))
      .toEqual({ kind: 'other', cpoName: 'Aral pulse' })
    expect(f).toMatchObject({ isPublicCharging: true, cpoName: 'Aral pulse' })
  })

  it('bekannter Standort wird zur Kachel "zuletzt genutzt"', () => {
    const site = { id: 's1', name: 'EnBW Kaufland', cpoName: 'EnBW', geohash: 'u33dc0c', maxAcKw: null, maxDcKw: 150, chargePoints: 4,
      fastCharging: true, address: null, plugTypes: ['CCS'], lastUsedAt: '2026-09-24T10:00:00', usageCount: 7 }
    const f = emptyLogForm()
    expect(applyVoiceDraft(f, draft({ place: { kind: 'site', station: null, site, cpoName: null } }))).toEqual({ kind: 'site', site })
    expect(f.cpoName).toBe('EnBW')
  })
})

describe('voiceCost', () => {
  it('Gesamtbetrag vor Preis je kWh', () => {
    expect(voiceCost({ costEur: 18.4, pricePerKwh: 0.59 })).toEqual({ mode: 'total', eur: 18.4 })
    expect(voiceCost({ costEur: null, pricePerKwh: 0.59 })).toEqual({ mode: 'per_kwh', eur: 0.59 })
    expect(voiceCost({ costEur: 0, pricePerKwh: null })).toEqual({ mode: 'total', eur: 0 })
    expect(voiceCost({ costEur: null, pricePerKwh: null })).toBeNull()
  })
})

describe('canJumpToReview', () => {
  it('nur mit Ort und allen Pflichtwerten direkt auf die Prüfseite', () => {
    const f = emptyLogForm()
    f.kwhCharged = 30; f.odometerKm = 48_210; f.socAfterChargePercent = 80; f.costEur = 12
    expect(canJumpToReview(f, { place: 'home' })).toBe(true)
    expect(canJumpToReview(f, { place: null })).toBe(false)
    f.costEur = null
    expect(canJumpToReview(f, { place: 'home' })).toBe(false)
  })
})

describe('voiceFlags', () => {
  it('fasst unsichere Felder zu den Kacheln der Prüfseite zusammen', () => {
    expect(voiceFlags(['kwhCharged', 'kwhAtVehicle', 'tariffIndex', 'costEur', 'placeIndex', 'loggedAt', 'odometerKm', 'socAfter']))
      .toEqual(['place', 'energy', 'odometer', 'soc', 'cost', 'time'])
  })

  it('Details ohne eigene Kachel landen gesammelt unter "details"', () => {
    expect(voiceFlags(['socBefore', 'maxChargingPowerKw', 'tireType'])).toEqual(['details'])
    expect(voiceFlags([])).toEqual([])
  })
})
