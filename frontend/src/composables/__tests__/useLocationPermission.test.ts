import { describe, it, expect, vi, afterEach } from 'vitest'
import { queryLocationPermission, getCurrentPosition } from '../useLocationPermission'

const setNavigator = (nav: Partial<Navigator>) =>
  Object.defineProperty(globalThis, 'navigator', { value: nav, configurable: true, writable: true })

describe('queryLocationPermission', () => {
  afterEach(() => setNavigator({}))

  it('liefert den Status der Permissions API', async () => {
    setNavigator({ permissions: { query: vi.fn().mockResolvedValue({ state: 'denied' }) } as any, geolocation: {} as any })
    expect(await queryLocationPermission()).toBe('denied')
  })

  it('ohne Permissions API ist der Status unbekannt, ohne Geolocation nicht verfügbar', async () => {
    setNavigator({ geolocation: {} as any })
    expect(await queryLocationPermission()).toBe('unknown')
    setNavigator({})
    expect(await queryLocationPermission()).toBe('unavailable')
  })

  it('ein Fehler der Permissions API ist kein Absturz', async () => {
    setNavigator({ permissions: { query: vi.fn().mockRejectedValue(new TypeError('geolocation not supported')) } as any, geolocation: {} as any })
    expect(await queryLocationPermission()).toBe('unknown')
  })
})

describe('getCurrentPosition', () => {
  afterEach(() => setNavigator({}))

  it('löst mit Koordinaten auf', async () => {
    setNavigator({ geolocation: { getCurrentPosition: (ok: any) => ok({ coords: { latitude: 1, longitude: 2 } }) } as any })
    expect(await getCurrentPosition()).toEqual({ latitude: 1, longitude: 2 })
  })

  it('unterscheidet Verweigerung von anderen Fehlern', async () => {
    setNavigator({ geolocation: { getCurrentPosition: (_ok: any, err: any) => err({ code: 1 }) } as any })
    await expect(getCurrentPosition()).rejects.toMatchObject({ denied: true })
    setNavigator({ geolocation: { getCurrentPosition: (_ok: any, err: any) => err({ code: 3 }) } as any })
    await expect(getCurrentPosition()).rejects.toMatchObject({ denied: false })
  })
})
