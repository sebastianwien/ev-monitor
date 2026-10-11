import { describe, it, expect } from 'vitest'
import { buildModelStory, keepUnits, type StoryInput, type StoryFormatters } from '../modelStory'
import { i18n } from '../../i18n'

// Loose signature: the typed vue-i18n `t` makes the compiler walk every locale key
const translate = (i18n.global as unknown as { t: (key: string, params: Record<string, unknown>) => string }).t

const fmt: StoryFormatters = {
  t: (key, params) => translate(key, params ?? {}),
  formatConsumption: v => `${v.toFixed(1)} kWh/100km`,
  formatDistance: km => `${km} km`,
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
  routeType: { highway: 18.9, city: 19.0, combined: 17.1, highwayCars: 16, cityLogs: 119, combinedLogs: 800 },
  seasonal: { summer: 17.6, winter: 22.6, summerLogs: 1630, winterLogs: 891 },
  fastChargePowerKw: 85, avgChargingPowerKw: 65.9,
  acPricePerKwh: 0.35, dcPricePerKwh: 0.427, avgPricePerKwh: 0.372,
  fleetWinterFactor: { factor: 1.28, models: 29 },
}

const specOnly: StoryInput = {
  ...full, logCount: 0, carCount: 0, driverCount: 0, avgKwhPer100km: null, minKwhPer100km: null, maxKwhPer100km: null,
  routeType: null, seasonal: null, fastChargePowerKw: null, avgChargingPowerKw: null,
  acPricePerKwh: null, dcPricePerKwh: null, avgPricePerKwh: null,
}

describe('keepUnits', () => {
  it('binds a unit to its number so a line never starts with "km."', () => {
    expect(keepUnits('Der Prospekt sagt 434 km.')).toBe('Der Prospekt sagt 434\u00a0km.')
    expect(keepUnits('25 % mehr, 19,2 kWh/100km, 84 kW, 30 min, 9 €')).toBe('25\u00a0% mehr, 19,2\u00a0kWh/100km, 84\u00a0kW, 30\u00a0min, 9\u00a0€')
  })

  it('leaves words after a number alone', () => {
    expect(keepUnits('48 Fahrer sagen')).toBe('48 Fahrer sagen')
  })
})

describe('buildModelStory', () => {
  it('full data: the hero pits the prospectus against the drivers and every chapter is measured', () => {
    const s = buildModelStory(full, fmt)

    expect(s.level).toBe('FULL')
    // 58 × 0.9 × 100 / 19.1 = 273 → 270
    expect(s.realRangeKm).toBe(270)
    expect(s.summerRangeKm).toBe(300)
    expect(s.winterRangeKm).toBe(230)
    expect(s.heroSentence).toBe('Der Prospekt sagt 434 km. 50 Fahrer sagen 270 km.')
    expect(s.lede).toContain('Sommer und Winter')
    expect(s.chapters.map(c => c.level)).toEqual(['FULL', 'FULL', 'FULL', 'FULL'])
    // 19.1 / 15.3 = +25 %
    expect(s.chapters[0].headline).toBe('19.1 kWh/100km. 25 % mehr als im Prospekt.')
    expect(s.chapters[1].headline).toBe('Im Winter fehlen 70 km.')
    expect(s.chapters[0].effect).toContain('18.9 kWh/100km statt 19.1 kWh/100km')
    // 58 × 0.7 / 85 kW = 28.7 min → 30
    expect(s.chapters[2].headline).toBe('Von 10 auf 80 Prozent in rund 30 Minuten.')
    // 0.372 × 19.1 = 7.11
    expect(s.chapters[3].headline).toBe('7.11 €/100km. Zuhause weniger, unterwegs mehr.')
    expect(s.fallbackBand).toEqual({ minKm: 230, maxKm: 300 })
  })

  it('partial data: a missing season says so and names the fleet surcharge', () => {
    const s = buildModelStory({ ...full, seasonal: null, fastChargePowerKw: null }, fmt)

    expect(s.level).toBe('FULL')
    expect(s.chapters[1].level).toBe('PARTIAL')
    expect(s.chapters[1].headline).toBe('Für den Volkswagen ID.3 fehlen uns Winterdaten.')
    expect(s.chapters[1].say).toContain('28 % Winteraufschlag, gemessen an 29 anderen Modellen')
    expect(s.chapters[2].level).toBe('PARTIAL')
    expect(s.chapters[2].headline).toContain('noch kein Fahrer gemessen')
    expect(s.lede).not.toContain('Sommer und Winter')
    expect(s.lede).toContain('Wo uns Daten fehlen')
    // the band falls back to the year average when a season is missing
    expect(s.fallbackBand).toBeNull()
  })

  it('spec only: WLTP speaks, nobody has measured yet, and the chapters still ask their questions', () => {
    const s = buildModelStory(specOnly, fmt)

    expect(s.level).toBe('NONE')
    expect(s.realRangeKm).toBeNull()
    expect(s.heroSentence).toBe('434 km verspricht Volkswagen. Noch kein Fahrer hat nachgemessen.')
    expect(s.chapters.map(c => c.level)).toEqual(['NONE', 'NONE', 'NONE', 'NONE'])
    expect(s.chapters[0].headline).toBe('Noch kein Fahrer hat den Verbrauch des Volkswagen ID.3 gemessen.')
    expect(s.fallbackBand).toBeNull()
  })

  it('the charge price is a fast-charging stop from 10 to 80 percent, not the average session', () => {
    const s = buildModelStory(full, fmt)

    // 58 kWh × 0.7 = 40.6 kWh into the battery, / 0.95 DC efficiency = 42.7 kWh at the charger × 0.427 € = 18.25 €
    const tiles = s.chapters[3].tiles
    const stop = tiles[tiles.length - 1]
    expect(stop.value).toBe('18 €')
    expect(stop.unit).toBe('pro Ladestopp')
    expect(stop.label).toBe('10 auf 80 %, 43 kWh')
    expect(s.chapters[3].say).toContain('Ein Schnellladestopp von 10 auf 80 Prozent zieht 43 kWh aus der Säule und kostet rund 18 €.')
  })

  it('the cost claim "cheaper at home" only appears when AC is cheaper than DC', () => {
    const s = buildModelStory({ ...full, acPricePerKwh: 0.5, dcPricePerKwh: 0.4 }, fmt)

    expect(s.chapters[3].headline).toBe('7.11 €/100km beim Community-Preis.')
  })

  it('without WLTP the consumption headline drops the prospectus comparison', () => {
    const s = buildModelStory({ ...full, wltpKwhPer100km: null, wltpRangeKm: null }, fmt)

    expect(s.chapters[0].headline).toBe('19.1 kWh/100km, gemessen an 51 Autos.')
    expect(s.heroSentence).toBe('50 Fahrer sagen 270 km.')
  })
})
