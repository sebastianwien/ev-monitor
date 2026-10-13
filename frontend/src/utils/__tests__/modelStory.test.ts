import { describe, it, expect } from 'vitest'
import { buildModelStory, keepUnits, type StoryInput, type StoryFormatters } from '../modelStory'
import { i18n } from '../../i18n'

// Loose signature: the typed vue-i18n `t` makes the compiler walk every locale key
const translate = (i18n.global as unknown as { t: (key: string, params: Record<string, unknown>) => string }).t

const fmt: StoryFormatters = {
  t: (key, params) => translate(key, params ?? {}),
  formatConsumption: v => `${v.toFixed(1)} kWh/100km`,
  formatConsumptionValue: v => v.toFixed(1),
  consumptionUnit: 'kWh/100km',
  formatDistance: km => `${km} km`,
  formatDistanceValue: km => String(km),
  distanceUnit: 'km',
  formatCostPerKwh: v => `${Math.round(v * 100)} ct/kWh`,
  formatCostPerDistance: v => `${v.toFixed(2)} €/100km`,
  formatCurrency: v => `${Math.round(v)} €`,
  formatNumber: v => String(v),
}

/** ID.3 Pro as measured on prod 11.10.2026 */
const full: StoryInput = {
  modelName: 'Volkswagen ID.3', brandName: 'Volkswagen', logCount: 6230, carCount: 51, driverCount: 50,
  netBatteryKwh: 58, wltpKwhPer100km: 15.3, wltpRangeKm: 434,
  avgKwhPer100km: 19.1, minKwhPer100km: 16.6, maxKwhPer100km: 19.9,
  routeType: { highway: 18.9, city: 16.2, combined: 17.1, highwayCars: 16, highwayLogs: 121, cityLogs: 119, combinedLogs: 800, typedMean: 17.6 },
  seasonal: { summer: 17.6, winter: 22.6, summerLogs: 1630, winterLogs: 891 },
  fastChargePowerKw: 85,
  dcPricePerKwh: 0.427, avgPricePerKwh: 0.372,
  fleetWinterFactor: { factor: 1.28, models: 29 },
}

const specOnly: StoryInput = {
  ...full, logCount: 0, carCount: 0, driverCount: 0, avgKwhPer100km: null, minKwhPer100km: null, maxKwhPer100km: null,
  routeType: null, seasonal: null, fastChargePowerKw: null,
  dcPricePerKwh: null, avgPricePerKwh: null,
}

const fact = (s: ReturnType<typeof buildModelStory>, key: string) => s.facts.find(f => f.key === key)!

describe('keepUnits', () => {
  it('binds a unit to its number so a line never starts with "km."', () => {
    expect(keepUnits('Der Prospekt sagt 434 km.')).toBe('Der Prospekt sagt 434 km.')
    expect(keepUnits('25 % mehr, 19,2 kWh/100km, 84 kW, 30 min, 9 €')).toBe('25 % mehr, 19,2 kWh/100km, 84 kW, 30 min, 9 €')
  })

  it('leaves words after a number alone', () => {
    expect(keepUnits('48 Fahrer sagen')).toBe('48 Fahrer sagen')
  })
})

describe('buildModelStory', () => {
  it('hero: the prospectus against the drivers', () => {
    const s = buildModelStory(full, fmt)

    expect(s.level).toBe('FULL')
    // 58 × 0.9 × 100 / 19.1 = 273 → 270
    expect(s.realRangeKm).toBe(270)
    expect(s.summerRangeKm).toBe(300)
    expect(s.winterRangeKm).toBe(230)
    // the prospectus on the same window as the drivers, 100 to 10 %: 434 × 0.9 = 391 → 390
    expect(s.prospectRangeKm).toBe(390)
    expect(s.heroSentence).toBe('Der Prospekt sagt 390 km. 50 Fahrer sagen 270 km.')
  })

  it('the drivers spread from the thirstiest to the most frugal driver, on the same battery window', () => {
    // 58 × 0.9 × 100 / 19.9 = 262 → 260; / 16.6 = 314 → 310
    expect(buildModelStory(full, fmt).driverRangeKm).toEqual({ minKm: 260, maxKm: 310 })
    expect(buildModelStory({ ...full, minKwhPer100km: null }, fmt).driverRangeKm).toBeNull()
    expect(buildModelStory(specOnly, fmt).driverRangeKm).toBeNull()
  })

  it('thin data (under 5 cars): the hero calls it first measurements, not a verdict', () => {
    expect(buildModelStory({ ...full, carCount: 2, driverCount: 2 }, fmt).heroSentence)
      .toBe('Der Prospekt sagt 390 km. Bisher messen 2 Fahrer 270 km.')
    expect(buildModelStory({ ...full, carCount: 1, driverCount: 1 }, fmt).heroSentence)
      .toBe('Der Prospekt sagt 390 km. Bisher misst ein Fahrer 270 km.')
  })

  it('data sheet: four tiles in fixed order, one figure and one comparison line each', () => {
    const s = buildModelStory(full, fmt)

    expect(s.facts.map(f => f.key)).toEqual(['range', 'consumption', 'charging', 'cost'])
    expect(s.facts.every(f => f.level === 'FULL')).toBe(true)
    expect(fact(s, 'range')).toMatchObject({ value: '270', unit: 'km', note: 'Winter 230 km' })
    // 19.1 / 15.3 = +25 %
    expect(fact(s, 'consumption')).toMatchObject({ value: '19.1', unit: 'kWh/100km', note: '25 % über Prospekt (15.3)' })
    // 58 × 0.7 / 85 kW = 28.7 min → 30
    expect(fact(s, 'charging')).toMatchObject({ value: '30', unit: 'min', note: '10 auf 80 %, typisch 85 kW' })
    // 0.372 × 19.1 = 7.11
    expect(fact(s, 'cost')).toMatchObject({ value: '7.11 €', unit: '/100km', note: 'Ø 37 ct/kWh, Schnellladestopp rund 18 €' })
  })

  it('evidence: range by season against the prospectus, bars from zero on one scale', () => {
    const s = buildModelStory(full, fmt)

    expect(s.evidence.range!.bars).toEqual([
      { key: 'wltp', label: 'Prospekt', value: 390, text: '390 km', reference: true },
      { key: 'summer', label: 'Sommer', value: 300, text: '300 km', reference: false },
      { key: 'winter', label: 'Winter', value: 230, text: '230 km', reference: false },
    ])
    // 230 / 300 = -23 %
    expect(s.evidence.range!.note).toBe('Im Winter kommst du 23 % weniger weit als im Sommer. Sommer aus 1630, Winter aus 891 Ladevorgängen.')
  })

  it('evidence: route types as the difference to their own mean, never as absolute values next to the overall mean', () => {
    const s = buildModelStory(full, fmt)

    // 16.2 / 17.6 = -8 %, 17.1 / 17.6 = -3 %, 18.9 / 17.6 = +7 %
    expect(s.evidence.routes!.rows).toEqual([
      { key: 'city', label: 'Stadt', pct: -8, text: '-8 %' },
      { key: 'combined', label: 'Gemischt', pct: -3, text: '-3 %' },
      { key: 'highway', label: 'Autobahn', pct: 7, text: '+7 %' },
    ])
    expect(s.evidence.routes!.note).toContain('1040 Fahrten')
    expect(s.evidence.routes!.note).toContain('Autobahn von 16 Autos')
  })

  it('too few highway cars: the highway row is left out, not estimated', () => {
    const s = buildModelStory({ ...full, routeType: { ...full.routeType!, highwayCars: 3 } }, fmt)

    expect(s.evidence.routes!.rows.map(r => r.key)).toEqual(['city', 'combined'])
  })

  it('a missing season: range evidence shows the year average and names the fleet surcharge', () => {
    const s = buildModelStory({ ...full, seasonal: null, fastChargePowerKw: null }, fmt)

    expect(fact(s, 'range').note).toBe('Prospekt 390 km')
    expect(s.evidence.range!.bars.map(b => b.key)).toEqual(['wltp', 'year'])
    expect(s.evidence.range!.note).toContain('28 % Winteraufschlag, gemessen an 29 anderen Modellen')
    expect(fact(s, 'charging')).toMatchObject({ value: null, level: 'PARTIAL' })
  })

  it('spec only: the tiles show the manufacturer figures as such and there is no evidence', () => {
    const s = buildModelStory(specOnly, fmt)

    expect(s.level).toBe('NONE')
    expect(s.realRangeKm).toBeNull()
    // nothing to compare: the manufacturer's own figure as it stands
    expect(s.heroSentence).toBe('434 km verspricht Volkswagen. Noch kein Fahrer hat nachgemessen.')
    expect(fact(s, 'range')).toMatchObject({ value: '434', level: 'NONE', note: 'laut Hersteller, noch nicht gemessen' })
    expect(fact(s, 'consumption')).toMatchObject({ value: '15.3', level: 'NONE' })
    expect(fact(s, 'cost').value).toBeNull()
    expect(s.evidence.range).toBeNull()
    expect(s.evidence.routes).toBeNull()
  })

  it('without WLTP the hero and the consumption tile drop the prospectus comparison', () => {
    const s = buildModelStory({ ...full, wltpKwhPer100km: null, wltpRangeKm: null }, fmt)

    expect(s.heroSentence).toBe('50 Fahrer sagen 270 km.')
    expect(fact(s, 'consumption').note).toBe('aus 6230 Ladevorgängen')
    expect(s.evidence.range!.bars.map(b => b.key)).toEqual(['summer', 'winter'])
  })

  it('a consumption below the prospectus says so', () => {
    const s = buildModelStory({ ...full, avgKwhPer100km: 14.5 }, fmt)

    expect(fact(s, 'consumption').note).toBe('5 % unter Prospekt (15.3)')
  })
})
