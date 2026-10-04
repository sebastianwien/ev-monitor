/** Ab dieser Ungenauigkeit der Ortung sagt die Ortswahl es offen, statt "keine Säule" zu melden. */
export const INACCURATE_LOCATION_M = 500

export function isLocationInaccurate(accuracyMeters: number | null | undefined): boolean {
  return accuracyMeters != null && accuracyMeters > INACCURATE_LOCATION_M
}

export function formatDistance(meters: number, locale?: string): string {
  return meters < 1000
    ? `${Math.round(meters)} m`
    : `${(meters / 1000).toLocaleString(locale, { maximumFractionDigits: 1 })} km`
}
