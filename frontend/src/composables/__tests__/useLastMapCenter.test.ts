// @vitest-environment jsdom
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import geohash from 'ngeohash'
import { readLastMapCenter, rememberMapCenter, LAST_CENTER_KEY } from '../useLastMapCenter'

describe('gemerkte Kartenmitte', () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => vi.unstubAllGlobals())

  it('merkt sich die Mitte nur auf eine Zelle gerundet', () => {
    rememberMapCenter(52.374512, 9.738533)

    expect(localStorage.getItem(LAST_CENTER_KEY)).toBe(geohash.encode(52.374512, 9.738533, 6))
    const back = readLastMapCenter()!
    const cell = geohash.decode(geohash.encode(52.374512, 9.738533, 6))
    expect(back).toEqual({ lat: cell.latitude, lon: cell.longitude })
  })

  it('ohne Eintrag oder mit Unsinn: null', () => {
    expect(readLastMapCenter()).toBeNull()
    localStorage.setItem(LAST_CENTER_KEY, 'not-a-hash!')
    expect(readLastMapCenter()).toBeNull()
  })

  it('gesperrter Speicher (privates Fenster): kein Fehler', () => {
    const denied = () => { throw new Error('denied') }
    vi.stubGlobal('localStorage', { getItem: denied, setItem: denied })

    expect(() => rememberMapCenter(52.37, 9.73)).not.toThrow()
    expect(readLastMapCenter()).toBeNull()
  })
})
