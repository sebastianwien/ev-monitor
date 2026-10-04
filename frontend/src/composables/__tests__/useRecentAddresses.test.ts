// @vitest-environment jsdom
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useRecentAddresses } from '../useRecentAddresses'

/** Die zuletzt gewählten Adressen bleiben nur in diesem Browser und nur für diesen Nutzer. */
const a = { latitude: 52.53, longitude: 13.45, name: 'Storkower Str. 140, Berlin' }
const b = { latitude: 48.51, longitude: 14.5, name: 'Linzer Straße 51, Freistadt' }
const c = { latitude: 50.1, longitude: 8.6, name: 'Hauptstraße 1, Frankfurt' }
const d = { latitude: 53.5, longitude: 10.0, name: 'Elbchaussee 2, Hamburg' }

describe('useRecentAddresses', () => {
  beforeEach(() => { localStorage.clear(); vi.restoreAllMocks() })

  it('neueste zuerst, ohne Dubletten, höchstens drei', () => {
    const r = useRecentAddresses('u1')
    r.remember(a); r.remember(b); r.remember(a); r.remember(c); r.remember(d)
    expect(r.list.value.map(x => x.name)).toEqual([d.name, c.name, a.name])
    expect(useRecentAddresses('u1').list.value).toHaveLength(3)
  })

  it('je Nutzer getrennt', () => {
    useRecentAddresses('u1').remember(a)
    expect(useRecentAddresses('u2').list.value).toEqual([])
  })

  it('ohne Nutzer merkt es sich nichts', () => {
    const r = useRecentAddresses(null)
    r.remember(a)
    expect(r.list.value).toEqual([])
    expect(localStorage.length).toBe(0)
  })

  it('gesperrter Speicher und kaputte Daten: leere Liste, kein Fehler', () => {
    localStorage.setItem('recent-addresses:u1', '{kaputt')
    expect(useRecentAddresses('u1').list.value).toEqual([])
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked') })
    const r = useRecentAddresses('u1')
    expect(() => r.remember(a)).not.toThrow()
    expect(r.list.value[0].name).toBe(a.name)
  })
})
