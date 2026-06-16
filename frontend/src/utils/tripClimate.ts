// Pure helpers for the trip logfeed climate/comfort markers. The component maps the
// returned load keys to Heroicons + i18n labels; this module stays presentation-free so
// the share/duration logic is unit-testable on its own.

export interface ClimateLoad {
  active: boolean
  seconds: number
}

export interface ClimateSummary {
  tripSeconds: number
  comfortHeat: ClimateLoad
  hvacHeating: ClimateLoad
  hvacCooling: ClimateLoad
  batteryHeater: ClimateLoad
}

export type ClimateLoadKey = 'comfortHeat' | 'hvacHeating' | 'hvacCooling' | 'batteryHeater'

// Fixed render order (matches backend + UX concept).
export const CLIMATE_LOAD_ORDER: ClimateLoadKey[] = [
  'comfortHeat',
  'hvacHeating',
  'hvacCooling',
  'batteryHeater',
]

export interface ActiveClimateLoad {
  key: ClimateLoadKey
  seconds: number
  /** Percentage of the trip the load was active, clamped to 100; null when trip duration is unknown. */
  share: number | null
}

/** Share of the trip a load was active, clamped to 100% (a load can never exceed the drive). */
export function climateShare(seconds: number, tripSeconds: number | null | undefined): number | null {
  if (!tripSeconds || tripSeconds <= 0) return null
  return Math.min(100, Math.round((seconds / tripSeconds) * 100))
}

/** Active loads in fixed order, each with its share. Empty when no climate data or nothing active. */
export function activeClimateLoads(climate: ClimateSummary | null | undefined): ActiveClimateLoad[] {
  if (!climate) return []
  return CLIMATE_LOAD_ORDER
    .filter((key) => climate[key]?.active)
    .map((key) => ({
      key,
      seconds: climate[key]?.seconds ?? 0,
      share: climateShare(climate[key]?.seconds ?? 0, climate.tripSeconds),
    }))
}

export function hasActiveClimateLoads(climate: ClimateSummary | null | undefined): boolean {
  return activeClimateLoads(climate).length > 0
}

/** Whole minutes for durations >= 60s; callers render "<1 min" below that threshold. */
export function climateDurationMinutes(seconds: number): number {
  return Math.round(seconds / 60)
}
