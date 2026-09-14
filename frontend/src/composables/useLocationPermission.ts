/**
 * Standortfreigabe ohne Dialog abfragen und die Position holen.
 *
 * Der Browser-Dialog darf erst nach einem bewussten Tap des Nutzers erscheinen: wer ihn
 * einmal blockiert, kann ihn nur noch in den Browser-Einstellungen zurückholen. Deshalb
 * trennt das Formular "noch nie gefragt" (Hinweis mit Button) von "blockiert" (Hinweis
 * auf die Einstellungen) und "erlaubt" (direkt laden).
 */
export type LocationPermission = 'granted' | 'prompt' | 'denied' | 'unknown' | 'unavailable'

export const LOCATION_ENABLED_KEY = 'ev_location_enabled'

export async function queryLocationPermission(): Promise<LocationPermission> {
  const nav = globalThis.navigator as Navigator | undefined
  if (!nav?.geolocation) return 'unavailable'
  if (!nav.permissions?.query) return 'unknown'
  try {
    const status = await nav.permissions.query({ name: 'geolocation' })
    return status.state
  } catch {
    // Safari kennt "geolocation" in der Permissions API erst seit iOS 16
    return 'unknown'
  }
}

export interface Coordinates { latitude: number; longitude: number }

export class LocationError extends Error {
  constructor(readonly denied: boolean) {
    super(denied ? 'location denied' : 'location unavailable')
  }
}

export function getCurrentPosition(): Promise<Coordinates> {
  return new Promise((resolve, reject) => {
    const geo = (globalThis.navigator as Navigator | undefined)?.geolocation
    if (!geo) { reject(new LocationError(false)); return }
    geo.getCurrentPosition(
      (pos) => resolve({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
      (err) => reject(new LocationError(err.code === 1)),
      { timeout: 10_000, maximumAge: 60_000 },
    )
  })
}
