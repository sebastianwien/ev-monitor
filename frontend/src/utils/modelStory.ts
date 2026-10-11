/**
 * Text and figures of the model page story "Prospekt gegen Realität": hero sentence, four
 * chapters (consumption, season, long trip, cost), each rated FULL (measured), PARTIAL (the
 * model has logs but not this figure) or NONE (spec only). Pure: all values come from the public
 * stats response, no consumption formula of its own beyond range = net battery × 0.9 / consumption,
 * the same rule the page used before.
 */

import { DC_CHARGING_EFFICIENCY } from './consumptionPreview'

/** Same as the backend calculator: below this many highway cars the highway effect is the fleet estimate */
const MIN_HIGHWAY_CARS = 5

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
  minKwhPer100km: number | null
  maxKwhPer100km: number | null
  /** typedMean: mean of all route-typed trips, the base the highway value is compared with (same period) */
  routeType: { highway: number | null; city: number | null; combined: number | null; highwayCars: number; cityLogs: number; combinedLogs: number; typedMean?: number | null } | null
  /** null when either season has too few logs */
  seasonal: { summer: number; winter: number; summerLogs: number; winterLogs: number } | null
  fastChargePowerKw: number | null
  avgChargingPowerKw: number | null
  acPricePerKwh: number | null
  dcPricePerKwh: number | null
  avgPricePerKwh: number | null
  /** median winter/summer ratio over the models that have both, for the PARTIAL season chapter */
  fleetWinterFactor: { factor: number; models: number } | null
}

export interface StoryFormatters {
  t: (key: string, params?: Record<string, string | number>) => string
  formatConsumption: (kwhPer100km: number) => string
  /** the same without the unit, for "from 16,9 to 20,6 kWh/100km" */
  formatConsumptionValue?: (kwhPer100km: number) => string
  formatDistance: (km: number) => string
  formatCostPerKwh: (eurPerKwh: number) => string
  formatCostPerDistance: (eurPer100km: number) => string
  formatCurrency: (eur: number) => string
  formatNumber: (n: number) => string
}

export interface StoryTile {
  value: string
  label: string
  /** 0 to 100: the tile draws a bar of this width (season chapter) */
  bar?: number
  tone?: 'summer' | 'winter'
  /** line under the figure; when set, the value stays whole ("19 €" with "per stop" below) */
  unit?: string
}

export type ChapterKey = 'consumption' | 'season' | 'longTrip' | 'cost'

export interface StoryChapterData {
  key: ChapterKey
  level: StoryLevel
  headline: string
  say: string
  tiles: StoryTile[]
  /** what the chapter's answers change, shown under the chips */
  effect?: string
}

export interface ModelStory {
  level: StoryLevel
  heroSentence: string
  lede: string
  realRangeKm: number | null
  summerRangeKm: number | null
  winterRangeKm: number | null
  /** winter to summer range of the model itself, shown before the first answer; null without both seasons */
  fallbackBand: { minKm: number; maxKm: number } | null
  chapters: StoryChapterData[]
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
  const partial: StoryLevel = hasLogs ? 'PARTIAL' : 'NONE'
  const realRangeKm = rangeKm(d.netBatteryKwh, d.avgKwhPer100km)
  const summerRangeKm = d.seasonal ? rangeKm(d.netBatteryKwh, d.seasonal.summer) : null
  const winterRangeKm = d.seasonal ? rangeKm(d.netBatteryKwh, d.seasonal.winter) : null
  const p = { model: d.modelName, brand: d.brandName, cars: f.formatNumber(d.carCount), drivers: f.formatNumber(d.driverCount) }

  const heroSentence = !hasLogs || realRangeKm == null
    ? d.wltpRangeKm
      ? f.t('model.story.hero_none', { ...p, wltp: f.formatDistance(d.wltpRangeKm) })
      : f.t('model.story.hero_none_no_wltp', p)
    : d.wltpRangeKm
      ? f.t('model.story.hero_full', { ...p, wltp: f.formatDistance(d.wltpRangeKm), real: f.formatDistance(realRangeKm) })
      : f.t('model.story.hero_full_no_wltp', { ...p, real: f.formatDistance(realRangeKm) })
  const chapters = [
    consumption(d, f, p, level),
    season(d, f, p, partial, summerRangeKm, winterRangeKm),
    longTrip(d, f, p, partial),
    cost(d, f, p, partial),
  ]
  // "summer and winter, motorway and city" only when every chapter is measured
  const lede = !hasLogs
    ? f.t('model.story.lede_none', p)
    : chapters.every(c => c.level === 'FULL')
      ? f.t('model.story.lede_full', { ...p, logs: f.formatNumber(d.logCount) })
      : f.t('model.story.lede_partial', { ...p, logs: f.formatNumber(d.logCount) })

  return {
    level,
    heroSentence,
    lede,
    realRangeKm,
    summerRangeKm,
    winterRangeKm,
    fallbackBand: summerRangeKm != null && winterRangeKm != null ? { minKm: winterRangeKm, maxKm: summerRangeKm } : null,
    chapters,
  }
}

type P = { model: string; brand: string; cars: string; drivers: string }

function consumption(d: StoryInput, f: StoryFormatters, p: P, level: StoryLevel): StoryChapterData {
  if (level === 'NONE' || d.avgKwhPer100km == null) {
    return {
      key: 'consumption', level: 'NONE',
      headline: f.t('model.story.c1_none_headline', p),
      say: d.wltpKwhPer100km
        ? f.t('model.story.c1_none_say', { ...p, wltp: f.formatConsumption(d.wltpKwhPer100km) })
        : f.t('model.story.c1_none_say_no_wltp', p),
      tiles: d.wltpKwhPer100km ? [{ value: f.formatConsumption(d.wltpKwhPer100km), label: f.t('model.story.tile_wltp', p) }] : [],
    }
  }
  const real = f.formatConsumption(d.avgKwhPer100km)
  let headline: string
  if (d.wltpKwhPer100km) {
    const pct = Math.round((d.avgKwhPer100km / d.wltpKwhPer100km - 1) * 100)
    headline = pct >= 0
      ? f.t('model.story.c1_headline_more', { real, pct })
      : f.t('model.story.c1_headline_less', { real, pct: -pct })
  } else {
    headline = f.t('model.story.c1_headline_no_wltp', { ...p, real })
  }
  const say = d.wltpKwhPer100km && d.minKwhPer100km && d.maxKwhPer100km
    ? f.t('model.story.c1_say', { ...p, wltp: f.formatConsumption(d.wltpKwhPer100km), min: (f.formatConsumptionValue ?? f.formatConsumption)(d.minKwhPer100km), max: f.formatConsumption(d.maxKwhPer100km) })
    : f.t('model.story.c1_say_short', { ...p, logs: f.formatNumber(d.logCount) })
  // One population for all four figures: the model's logs. Route-type values rest on the subset
  // with a route type and can all sit below the overall mean, which reads like a contradiction.
  const tiles: StoryTile[] = [{ value: real, label: f.t('model.story.tile_avg', { n: f.formatNumber(d.logCount) }) }]
  if (d.minKwhPer100km) tiles.push({ value: f.formatConsumption(d.minKwhPer100km), label: f.t('model.story.tile_min') })
  if (d.maxKwhPer100km) tiles.push({ value: f.formatConsumption(d.maxKwhPer100km), label: f.t('model.story.tile_max') })
  if (d.wltpKwhPer100km) tiles.push({ value: f.formatConsumption(d.wltpKwhPer100km), label: f.t('model.story.tile_wltp', p) })
  // Highway against the route-typed trips of the same period, as a share: the absolute route values sit
  // on a younger subset and next to the overall mean they would read like a contradiction.
  // Same rule as the calculator: from MIN_HIGHWAY_CARS measured cars, else the fleet estimate.
  const rt = d.routeType
  const pct = rt?.highway && rt.typedMean && rt.highwayCars >= MIN_HIGHWAY_CARS
    ? Math.round((rt.highway / rt.typedMean - 1) * 100) : null
  const effect = pct == null ? f.t('model.story.c1_effect_estimated')
    : Math.abs(pct) < 2 ? f.t('model.story.c1_effect_same', { cars: f.formatNumber(rt!.highwayCars) })
    : f.t(pct > 0 ? 'model.story.c1_effect_more' : 'model.story.c1_effect_less', { pct: Math.abs(pct), cars: f.formatNumber(rt!.highwayCars) })
  return { key: 'consumption', level: 'FULL', headline, say, tiles, effect }
}

function season(d: StoryInput, f: StoryFormatters, p: P, partial: StoryLevel,
                summerRangeKm: number | null, winterRangeKm: number | null): StoryChapterData {
  const fleet = d.fleetWinterFactor
  const fleetSay = fleet
    ? f.t('model.story.c2_fleet_say', { pct: Math.round((fleet.factor - 1) * 100), n: fleet.models })
    : f.t('model.story.c2_wltp_say')
  if (!d.seasonal || summerRangeKm == null || winterRangeKm == null) {
    return {
      key: 'season', level: partial, tiles: [],
      headline: partial === 'PARTIAL' ? f.t('model.story.c2_partial_headline', p) : f.t('model.story.c2_none_headline', p),
      say: fleetSay,
    }
  }
  const s = d.seasonal
  return {
    key: 'season', level: 'FULL',
    headline: f.t('model.story.c2_headline', { loss: f.formatDistance(summerRangeKm - winterRangeKm) }),
    say: f.t('model.story.c2_say', {
      winterLogs: f.formatNumber(s.winterLogs), summerLogs: f.formatNumber(s.summerLogs),
      summer: f.formatConsumption(s.summer), winter: f.formatConsumption(s.winter),
    }),
    tiles: [
      { value: f.formatDistance(summerRangeKm), label: f.t('model.story.tile_summer', { consumption: f.formatConsumption(s.summer) }), bar: 100, tone: 'summer' },
      { value: f.formatDistance(winterRangeKm), label: f.t('model.story.tile_winter', { consumption: f.formatConsumption(s.winter) }), bar: Math.round(winterRangeKm / summerRangeKm * 100), tone: 'winter' },
    ],
  }
}

function longTrip(d: StoryInput, f: StoryFormatters, p: P, partial: StoryLevel): StoryChapterData {
  if (!d.fastChargePowerKw || !d.netBatteryKwh) {
    return {
      key: 'longTrip', level: partial, tiles: [],
      headline: f.t('model.story.c3_partial_headline', p),
      say: f.t('model.story.c3_partial_say'),
    }
  }
  const minutes = Math.round(d.netBatteryKwh * STOP_SHARE / d.fastChargePowerKw * 60 / 5) * 5
  const kw = f.formatNumber(Math.round(d.fastChargePowerKw))
  const tiles: StoryTile[] = [
    { value: `${kw} kW`, label: f.t('model.story.tile_fast_charge') },
    { value: `${minutes} min`, label: f.t('model.story.tile_stop_minutes') },
  ]
  if (d.avgChargingPowerKw) tiles.splice(1, 0, { value: `${f.formatNumber(Math.round(d.avgChargingPowerKw))} kW`, label: f.t('model.story.tile_session_mean') })
  return {
    key: 'longTrip', level: 'FULL', tiles,
    headline: f.t('model.story.c3_headline', { minutes }),
    say: d.avgChargingPowerKw
      ? f.t('model.story.c3_say', { kw, avg: f.formatNumber(Math.round(d.avgChargingPowerKw)) })
      : f.t('model.story.c3_say_short', { kw }),
  }
}

function cost(d: StoryInput, f: StoryFormatters, p: P, partial: StoryLevel): StoryChapterData {
  if (!d.avgPricePerKwh || !d.avgKwhPer100km) {
    return {
      key: 'cost', level: partial, tiles: [],
      headline: f.t('model.story.c4_partial_headline', p),
      say: f.t('model.story.c4_partial_say'),
    }
  }
  const per100 = d.avgPricePerKwh * d.avgKwhPer100km
  const tiles: StoryTile[] = []
  if (d.acPricePerKwh) tiles.push({ value: f.formatCostPerKwh(d.acPricePerKwh), label: f.t('model.story.tile_ac') })
  if (d.dcPricePerKwh) tiles.push({ value: f.formatCostPerKwh(d.dcPricePerKwh), label: f.t('model.story.tile_dc') })
  tiles.push({ value: f.formatCostPerDistance(per100), label: f.t('model.story.tile_per_100') })
  // A stop from 10 to 80 % at the DC price, the charge a buyer plans for; not the mean session, which includes short top-ups
  const stop = fastStop(d.netBatteryKwh, d.dcPricePerKwh ?? d.avgPricePerKwh)
  if (stop) tiles.push({ value: f.formatCurrency(stop.eur), unit: f.t('model.story.tile_stop_unit'), label: f.t('model.story.tile_stop_cost', { kwh: f.formatNumber(Math.round(stop.kwh)) }) })
  const parts = [
    d.acPricePerKwh ? f.t('model.story.c4_say_ac', { ac: f.formatCostPerKwh(d.acPricePerKwh) }) : null,
    d.dcPricePerKwh ? f.t('model.story.c4_say_dc', { dc: f.formatCostPerKwh(d.dcPricePerKwh) }) : null,
  ].filter((x): x is string => x != null)
  const say = [
    f.t('model.story.c4_say_avg', { parts: parts.join(', '), avg: f.formatCostPerKwh(d.avgPricePerKwh) }),
    stop ? f.t('model.story.c4_say_stop', { kwh: f.formatNumber(Math.round(stop.kwh)), eur: f.formatCurrency(stop.eur) }) : '',
  ].filter(Boolean).join(' ')
  return {
    key: 'cost', level: 'FULL', tiles,
    // "cheaper at home, dearer on the road" only when the data say so
    headline: d.acPricePerKwh && d.dcPricePerKwh && d.acPricePerKwh < d.dcPricePerKwh
      ? f.t('model.story.c4_headline', { per100: f.formatCostPerDistance(per100) })
      : f.t('model.story.c4_headline_plain', { per100: f.formatCostPerDistance(per100) }),
    say,
  }
}
