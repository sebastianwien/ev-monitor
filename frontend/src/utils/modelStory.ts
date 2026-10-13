/**
 * Text and figures of the model page "Prospekt gegen Realität": the hero sentence, the data sheet
 * (four tiles: range, consumption, charging, cost) and the evidence behind it (range by season,
 * consumption by route type). Each tile is rated FULL (measured), PARTIAL (the model has logs but
 * not this figure) or NONE (spec only). Pure: all values come from the public stats response, no
 * consumption formula of its own beyond range = net battery × 0.9 / consumption.
 */

import { DC_CHARGING_EFFICIENCY } from './consumptionPreview'

/** Same as the backend calculator: below this many highway cars the highway effect is the fleet estimate */
const MIN_HIGHWAY_CARS = 5
/** Below this many cars the page speaks of first measurements, like the result box (same threshold) */
const MIN_MEASURED_CARS = 5

export type StoryLevel = 'FULL' | 'PARTIAL' | 'NONE'

export interface StoryInput {
  modelName: string
  brandName: string
  logCount: number
  carCount: number
  driverCount: number
  netBatteryKwh: number | null
  wltpKwhPer100km: number | null
  wltpRangeKm: number | null
  avgKwhPer100km: number | null
  /** typedMean: mean of all route-typed trips, the base the highway value is compared with (same period) */
  routeType: { highway: number | null; city: number | null; combined: number | null; highwayCars: number; highwayLogs?: number; cityLogs: number; combinedLogs: number; typedMean?: number | null } | null
  /** null when either season has too few logs */
  seasonal: { summer: number; winter: number; summerLogs: number; winterLogs: number } | null
  fastChargePowerKw: number | null
  dcPricePerKwh: number | null
  avgPricePerKwh: number | null
  /** median winter/summer ratio over the models that have both, for the PARTIAL season chapter */
  fleetWinterFactor: { factor: number; models: number } | null
}

export interface StoryFormatters {
  t: (key: string, params?: Record<string, string | number>) => string
  formatConsumption: (kwhPer100km: number) => string
  /** the same without the unit, for the big figure of a tile */
  formatConsumptionValue: (kwhPer100km: number) => string
  consumptionUnit: string
  formatDistance: (km: number) => string
  formatDistanceValue: (km: number) => string
  distanceUnit: string
  formatCostPerKwh: (eurPerKwh: number) => string
  formatCostPerDistance: (eurPer100km: number) => string
  formatCurrency: (eur: number) => string
  formatNumber: (n: number) => string
}

export type FactKey = 'range' | 'consumption' | 'charging' | 'cost'

/** One tile of the data sheet: a big figure with its unit, one comparison line below */
export interface FactTile {
  key: FactKey
  label: string
  /** null when nobody has measured it yet; the note says so */
  value: string | null
  unit: string
  note: string
  level: StoryLevel
}

export interface EvidenceBar {
  key: 'wltp' | 'summer' | 'winter' | 'year'
  label: string
  value: number
  text: string
  /** the manufacturer's figure, drawn as the grey reference */
  reference: boolean
}

/** Consumption of one route type against the mean of all route-typed trips, in percent */
export interface RouteDelta {
  key: 'city' | 'combined' | 'highway'
  label: string
  pct: number
  text: string
}

export interface ModelEvidence {
  range: { bars: EvidenceBar[]; note: string } | null
  routes: { rows: RouteDelta[]; note: string } | null
}

export interface ModelStory {
  level: StoryLevel
  heroSentence: string
  realRangeKm: number | null
  /** the prospectus range on the drivers' window, 100 to 10 %; null without WLTP */
  prospectRangeKm: number | null
  summerRangeKm: number | null
  winterRangeKm: number | null
  facts: FactTile[]
  evidence: ModelEvidence
}

const RANGE_SHARE = 0.9
const STOP_SHARE = 0.7

/** Non-breaking space between a number and its unit, so a headline never wraps before "km." */
export function keepUnits(text: string): string {
  return text.replace(/(\d) (?=(?:km|mi|kWh|kW|min|ct)\b|[%€])/g, '$1\u00a0')
}

/** A fast-charging stop from 10 to 80 % as billed at the charger: 70 % of the net battery through DC losses. */
export function fastStop(netKwh: number | null, pricePerKwh: number | null): { kwh: number; eur: number } | null {
  if (!netKwh || !pricePerKwh) return null
  const kwh = netKwh * STOP_SHARE / DC_CHARGING_EFFICIENCY
  return { kwh, eur: kwh * pricePerKwh }
}

/** Range on 90 % of the net battery, rounded to 10 km like the hero figure. */
export function rangeKm(netKwh: number | null, kwhPer100km: number | null): number | null {
  if (!netKwh || !kwhPer100km) return null
  return Math.round(netKwh * RANGE_SHARE / kwhPer100km * 10) * 10
}

export function buildModelStory(d: StoryInput, f: StoryFormatters): ModelStory {
  const hasLogs = d.logCount > 0 && d.avgKwhPer100km != null
  const level: StoryLevel = hasLogs ? 'FULL' : 'NONE'
  const realRangeKm = rangeKm(d.netBatteryKwh, d.avgKwhPer100km)
  // WLTP counts 100 to 0 %, the drivers 100 to 10 %: the comparison puts both on the same window
  const prospectRangeKm = d.wltpRangeKm ? Math.round(d.wltpRangeKm * RANGE_SHARE / 10) * 10 : null
  const summerRangeKm = d.seasonal ? rangeKm(d.netBatteryKwh, d.seasonal.summer) : null
  const winterRangeKm = d.seasonal ? rangeKm(d.netBatteryKwh, d.seasonal.winter) : null
  const p = { model: d.modelName, brand: d.brandName, cars: f.formatNumber(d.carCount), drivers: f.formatNumber(d.driverCount) }

  const heroSentence = !hasLogs || realRangeKm == null
    ? d.wltpRangeKm
      ? f.t('model.story.hero_none', { ...p, wltp: f.formatDistance(d.wltpRangeKm) })
      : f.t('model.story.hero_none_no_wltp', p)
    : prospectRangeKm && d.carCount < MIN_MEASURED_CARS
      ? f.t(d.driverCount === 1 ? 'model.story.hero_thin_one' : 'model.story.hero_thin', { ...p, wltp: f.formatDistance(prospectRangeKm), real: f.formatDistance(realRangeKm) })
    : prospectRangeKm
      ? f.t('model.story.hero_full', { ...p, wltp: f.formatDistance(prospectRangeKm), real: f.formatDistance(realRangeKm) })
      : f.t('model.story.hero_full_no_wltp', { ...p, real: f.formatDistance(realRangeKm) })

  const range = hasLogs ? rangeEvidence(d, f, { real: realRangeKm, prospect: prospectRangeKm, summer: summerRangeKm, winter: winterRangeKm }) : null
  const routes = hasLogs ? routeEvidence(d, f) : null
  return {
    level,
    heroSentence,
    realRangeKm,
    prospectRangeKm,
    summerRangeKm,
    winterRangeKm,
    facts: [
      rangeFact(d, f, realRangeKm, winterRangeKm, prospectRangeKm),
      consumptionFact(d, f, hasLogs),
      chargingFact(d, f, hasLogs),
      costFact(d, f, hasLogs),
    ],
    evidence: { range, routes },
  }
}

const label = (f: StoryFormatters, key: FactKey) => f.t(`model.page.fact_${key}`)

function rangeFact(d: StoryInput, f: StoryFormatters, real: number | null, winter: number | null, prospect: number | null): FactTile {
  const base = { key: 'range' as const, label: label(f, 'range'), unit: f.distanceUnit }
  if (real == null) {
    return d.wltpRangeKm
      ? { ...base, value: f.formatDistanceValue(d.wltpRangeKm), note: f.t('model.page.note_spec'), level: 'NONE' }
      : { ...base, value: null, note: f.t('model.page.note_spec_none'), level: 'NONE' }
  }
  const note = winter != null ? f.t('model.page.note_winter', { winter: f.formatDistance(winter) })
    : prospect ? f.t('model.page.note_wltp', { wltp: f.formatDistance(prospect) })
    : ''
  return { ...base, value: f.formatDistanceValue(real), note, level: 'FULL' }
}

function consumptionFact(d: StoryInput, f: StoryFormatters, hasLogs: boolean): FactTile {
  const base = { key: 'consumption' as const, label: label(f, 'consumption'), unit: f.consumptionUnit }
  if (!hasLogs || d.avgKwhPer100km == null) {
    return d.wltpKwhPer100km
      ? { ...base, value: f.formatConsumptionValue(d.wltpKwhPer100km), note: f.t('model.page.note_spec'), level: 'NONE' }
      : { ...base, value: null, note: f.t('model.page.note_spec_none'), level: 'NONE' }
  }
  let note: string
  if (d.wltpKwhPer100km) {
    const pct = Math.round((d.avgKwhPer100km / d.wltpKwhPer100km - 1) * 100)
    const wltp = f.formatConsumptionValue(d.wltpKwhPer100km)
    note = pct > 0 ? f.t('model.page.note_more', { pct, wltp })
      : pct < 0 ? f.t('model.page.note_less', { pct: -pct, wltp })
      : f.t('model.page.note_same', { wltp })
  } else {
    note = f.t('model.page.note_logs', { logs: f.formatNumber(d.logCount) })
  }
  return { ...base, value: f.formatConsumptionValue(d.avgKwhPer100km), note, level: 'FULL' }
}

function chargingFact(d: StoryInput, f: StoryFormatters, hasLogs: boolean): FactTile {
  const base = { key: 'charging' as const, label: label(f, 'charging'), unit: 'min' }
  if (!d.fastChargePowerKw || !d.netBatteryKwh) {
    return { ...base, value: null, note: f.t('model.page.note_charging_none'), level: hasLogs ? 'PARTIAL' : 'NONE' }
  }
  const minutes = Math.round(d.netBatteryKwh * STOP_SHARE / d.fastChargePowerKw * 60 / 5) * 5
  return {
    ...base, value: f.formatNumber(minutes), level: 'FULL',
    note: f.t('model.page.note_charging', { kw: f.formatNumber(Math.round(d.fastChargePowerKw)) }),
  }
}

function costFact(d: StoryInput, f: StoryFormatters, hasLogs: boolean): FactTile {
  const base = { key: 'cost' as const, label: label(f, 'cost') }
  if (!d.avgPricePerKwh || !d.avgKwhPer100km) {
    return { ...base, value: null, unit: '', note: f.t('model.page.note_cost_none'), level: hasLogs ? 'PARTIAL' : 'NONE' }
  }
  // "7,14 €/100km" → "7,14 €" big, "/100km" small; "£7.14/100mi" alike
  const text = f.formatCostPerDistance(d.avgPricePerKwh * d.avgKwhPer100km)
  const cut = text.lastIndexOf('/')
  // A stop from 10 to 80 % at the DC price, the charge a buyer plans for; not the mean session, which includes short top-ups
  const stop = fastStop(d.netBatteryKwh, d.dcPricePerKwh ?? d.avgPricePerKwh)
  const price = f.formatCostPerKwh(d.avgPricePerKwh)
  return {
    ...base, level: 'FULL',
    value: cut > 0 ? text.slice(0, cut) : text,
    unit: cut > 0 ? text.slice(cut) : '',
    note: stop ? f.t('model.page.note_cost', { price, stop: f.formatCurrency(stop.eur) }) : f.t('model.page.note_cost_short', { price }),
  }
}

function rangeEvidence(d: StoryInput, f: StoryFormatters,
                       { real, prospect, summer, winter }: Record<'real' | 'prospect' | 'summer' | 'winter', number | null>): ModelEvidence['range'] {
  const bar = (key: EvidenceBar['key'], value: number): EvidenceBar =>
    ({ key, label: f.t(`model.page.range_${key}`), value, text: f.formatDistance(value), reference: key === 'wltp' })
  const wltp = prospect ? [bar('wltp', prospect)] : []
  if (d.seasonal && summer != null && winter != null) {
    return {
      bars: [...wltp, bar('summer', summer), bar('winter', winter)],
      note: f.t('model.page.range_note', {
        pct: Math.round((1 - winter / summer) * 100),
        summerLogs: f.formatNumber(d.seasonal.summerLogs), winterLogs: f.formatNumber(d.seasonal.winterLogs),
      }),
    }
  }
  if (real == null) return null
  const fleet = d.fleetWinterFactor
  return {
    bars: [...wltp, bar('year', real)],
    note: fleet
      ? f.t('model.page.range_note_fleet', { pct: Math.round((fleet.factor - 1) * 100), n: fleet.models })
      : f.t('model.page.range_note_estimated'),
  }
}

/**
 * Route types against the mean of the route-typed trips of the same period. The absolute values sit
 * on a younger subset of trips and can all lie below the overall mean of the data sheet, which would
 * read like a contradiction; the difference in percent does not. Highway only from MIN_HIGHWAY_CARS
 * cars, the same rule as the calculator. Fewer than two rows say nothing and are left out.
 */
function routeEvidence(d: StoryInput, f: StoryFormatters): ModelEvidence['routes'] {
  const rt = d.routeType
  if (!rt?.typedMean) return null
  const mean = rt.typedMean
  const values: [RouteDelta['key'], number | null][] = [
    ['city', rt.city],
    ['combined', rt.combined],
    ['highway', rt.highwayCars >= MIN_HIGHWAY_CARS ? rt.highway : null],
  ]
  const rows = values.flatMap(([key, v]) => {
    if (v == null) return []
    const pct = Math.round((v / mean - 1) * 100)
    return [{ key, label: f.t(`model.page.routes_${key}`), pct, text: `${pct > 0 ? '+' : ''}${f.formatNumber(pct)} %` }]
  })
  if (rows.length < 2) return null
  const trips = f.formatNumber(rt.cityLogs + rt.combinedLogs + (rt.highwayLogs ?? 0))
  return {
    rows,
    note: rows.some(r => r.key === 'highway')
      ? f.t('model.page.routes_note', { trips, cars: f.formatNumber(rt.highwayCars) })
      : f.t('model.page.routes_note_no_highway', { trips }),
  }
}
