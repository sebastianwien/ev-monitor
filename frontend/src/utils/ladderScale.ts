import type { ConsumptionUnit } from '../config/unitSystems'
import { convertConsumption } from './unitConversions'

/**
 * Shared consumption axis for the model ranking ("ladder"): every row places its
 * values on the same scale, so models compare at a glance. The axis lives in the
 * market's display unit and always ascends left to right.
 */
export interface LadderAxis {
  unit: ConsumptionUnit
  min: number
  max: number
  ticks: number[]
}

// Plausible window in kWh/100km. Values beyond it are clamped to the edge instead of
// stretching the axis for everyone (one outlier would squeeze all other rows).
const WINDOW_KWH_PER_100KM: [number, number] = [10, 35]

const round = (v: number) => Number(v.toFixed(6))

function windowIn(unit: ConsumptionUnit): [number, number] {
  const a = convertConsumption(WINDOW_KWH_PER_100KM[0], unit)
  const b = convertConsumption(WINDOW_KWH_PER_100KM[1], unit)
  return [Math.min(a, b), Math.max(a, b)]
}

/** Step from the 1-2-5-10 series that splits the span into roughly four parts (no 2.5: whole ticks read faster). */
function niceStep(span: number): number {
  const rough = span / 4
  const magnitude = Math.pow(10, Math.floor(Math.log10(rough)))
  const norm = rough / magnitude
  const factor = norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10
  return factor * magnitude
}

/** Builds the axis from consumption values in kWh/100km (nulls and non-positive values ignored). */
export function buildLadderAxis(
  valuesKwhPer100km: Array<number | null | undefined>,
  unit: ConsumptionUnit,
): LadderAxis {
  const [windowMin, windowMax] = windowIn(unit)
  const display = valuesKwhPer100km
    .filter((v): v is number => v != null && v > 0)
    .map(v => Math.min(windowMax, Math.max(windowMin, convertConsumption(v, unit))))

  // Axis from the 5th to the 95th percentile: a handful of outliers would otherwise push
  // every other row into one corner. Values beyond pin to the edge (see ladderPosition).
  const sorted = [...display].sort((a, b) => a - b)
  const percentile = (p: number) => sorted[Math.min(sorted.length - 1, Math.max(0, Math.round((sorted.length - 1) * p)))]
  const lo = sorted.length ? percentile(0.05) : windowMin
  const hi = sorted.length ? percentile(0.95) : windowMax
  const step = niceStep(hi > lo ? hi - lo : (windowMax - windowMin) / 5)

  let min = Math.floor(round(lo / step)) * step
  let max = Math.ceil(round(hi / step)) * step
  if (min === max) {
    min -= step
    max += step
  }
  min = round(Math.max(min, windowMin))
  max = round(Math.min(max, windowMax))

  const ticks: number[] = []
  for (let t = Math.ceil(round(min / step)) * step; t <= max + step / 1000; t += step) {
    ticks.push(round(t))
  }
  return { unit, min, max, ticks }
}

/** Position of a kWh/100km value on the axis in percent (0 to 100, clamped), null if missing. */
export function ladderPosition(kwhPer100km: number | null | undefined, axis: LadderAxis): number | null {
  if (kwhPer100km == null || kwhPer100km <= 0) return null
  const v = convertConsumption(kwhPer100km, axis.unit)
  const pct = ((v - axis.min) / (axis.max - axis.min)) * 100
  return Math.min(100, Math.max(0, pct))
}

/** Bar length in percent from zero to the axis maximum (same scale in every row), null without a value. */
export function barLength(kwhPer100km: number | null | undefined, axis: LadderAxis): number | null {
  if (kwhPer100km == null || kwhPer100km <= 0) return null
  const v = convertConsumption(kwhPer100km, axis.unit)
  return Math.min(100, Math.max(0, (v / axis.max) * 100))
}
