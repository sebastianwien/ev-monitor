import type { StationMatch } from '../../composables/useNearbyStations'

/** "DC 50 kW · AC 43 kW · 2 Ladepunkte" - Leistung je Ladeart aus den Steckern, nie die Summe. */
export const stationSub = (
  s: Pick<StationMatch, 'chargePoints' | 'maxAcKw' | 'maxDcKw'>,
  t: (key: string, values: Record<string, number>, plural: number) => string,
) => [
  s.maxDcKw ? `DC ${Math.round(s.maxDcKw)} kW` : null,
  s.maxAcKw ? `AC ${Math.round(s.maxAcKw)} kW` : null,
  s.chargePoints ? t('logwizard.charge_points', { n: s.chargePoints }, s.chargePoints) : null,
].filter(Boolean).join(' · ')
