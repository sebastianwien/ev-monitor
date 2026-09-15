/**
 * Standortfreigabe ohne Dialog abfragen und die Position holen.
 *
 * Der Browser-Dialog darf erst nach einem bewussten Tap des Nutzers erscheinen: wer ihn
 * einmal blockiert, kann ihn nur noch in den Browser-Einstellungen zurückholen. Deshalb
 * trennt das Formular "noch nie gefragt" (Hinweis mit Button) von "blockiert" (Hinweis
 * auf die Einstellungen) und "erlaubt" (direkt laden).
 */
import { Capacitor } from '@capacitor/core'
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

/**
 * Wo der Nutzer die Standortfreigabe wieder einschaltet, hängt von der Plattform ab:
 * die App kann ihre Einstellungen direkt öffnen, im Browser gibt es nur eine Anleitung
 * je System - iPadOS meldet sich als Mac und ist nur am Touch-Support erkennbar.
 */
export type SettingsPlatform = 'native' | 'ios' | 'android' | 'desktop'

export function settingsPlatform(
  ua = globalThis.navigator?.userAgent ?? '',
  native = Capacitor.isNativePlatform(),
  touchPoints = globalThis.navigator?.maxTouchPoints ?? 0,
): SettingsPlatform {
  if (native) return 'native'
  if (/iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && touchPoints > 1)) return 'ios'
  if (/Android/.test(ua)) return 'android'
  return 'desktop'
}

/** Nur in der App: springt direkt in die Systemeinstellungen von ev-monitor. */
export async function openAppSettings(): Promise<void> {
  const { NativeSettings, IOSSettings, AndroidSettings } = await import('capacitor-native-settings')
  await NativeSettings.open({ optionIOS: IOSSettings.App, optionAndroid: AndroidSettings.ApplicationDetails })
}
