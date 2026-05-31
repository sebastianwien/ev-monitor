export function formatKm(km: number): string {
  if (km >= 1_000_000) return `${(km / 1_000_000).toFixed(1)} Mio.`
  if (km >= 1_000) return `${Math.floor(km / 1_000)} Tsd.`
  return km.toString()
}
