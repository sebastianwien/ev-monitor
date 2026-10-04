import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../../api/axios', () => ({ default: { get: vi.fn() } }))
import api from '../../api/axios'
import { useNearbyStations } from '../useNearbyStations'

describe('useNearbyStations', () => {
  beforeEach(() => vi.mocked(api.get).mockReset())

  it('lädt Standorte über den Umkreis-Endpoint', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [{ name: 'IONITY', known: true, distanceMeters: 40, maxAcKw: null, maxDcKw: 350, fastCharging: true, chargePoints: 6, geohash: 'u33dc0c', address: null, plugTypes: [], registerId: null }] })
    const { stations, load, loading } = useNearbyStations()
    await load(52.52, 13.405)
    expect(api.get).toHaveBeenCalledWith('/charging-provider-tariffs/cpos/nearby-stations', { params: { lat: 52.52, lon: 13.405 } })
    expect(stations.value).toHaveLength(1)
    expect(loading.value).toBe(false)
  })

  it('Fehler und unerwartete Antworten ergeben eine leere Liste', async () => {
    vi.mocked(api.get).mockRejectedValue(new Error('429'))
    const { stations, load } = useNearbyStations()
    await load(52.52, 13.405)
    expect(stations.value).toEqual([])
    vi.mocked(api.get).mockResolvedValue({ data: { message: 'nope' } })
    await load(52.52, 13.405)
    expect(stations.value).toEqual([])
  })
})

const station = (name: string) => ({ name, known: true, distanceMeters: 400, maxAcKw: 22, maxDcKw: null, fastCharging: false, chargePoints: 2, geohash: 'u33dc0c', address: null, plugTypes: [], registerId: null })
const radiusOf = (call: unknown[]) => (call[1] as { params: { radius?: number } }).params.radius ?? 250

describe('useNearbyStations: wachsender Umkreis', () => {
  beforeEach(() => vi.mocked(api.get).mockReset())

  it('erweitert bei leerer Liste auf 1 km und hält beim ersten Treffer an', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [] }).mockResolvedValueOnce({ data: [station('EnBW')] })
    const n = useNearbyStations()
    await n.load(52.53, 13.45)
    expect(vi.mocked(api.get).mock.calls.map(radiusOf)).toEqual([250, 1000])
    expect(n.stations.value.map(s => s.name)).toEqual(['EnBW'])
    expect(n.radius.value).toBe(1000)
    expect(n.exhausted.value).toBe(false)
    expect(n.canExpand.value).toBe(true)
  })

  it('sucht bis 2,5 km und meldet dann "erschöpft"', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] })
    const n = useNearbyStations()
    await n.load(52.53, 13.45)
    expect(vi.mocked(api.get).mock.calls.map(radiusOf)).toEqual([250, 1000, 2500])
    expect(n.radius.value).toBe(2500)
    expect(n.exhausted.value).toBe(true)
    expect(n.canExpand.value).toBe(false)
  })

  it('zeigt während der Suche den Umkreis, der gerade abgefragt wird', async () => {
    let release!: (v: unknown) => void
    vi.mocked(api.get).mockResolvedValueOnce({ data: [] }).mockReturnValueOnce(new Promise(r => { release = r }) as never)
    const n = useNearbyStations()
    const done = n.load(52.53, 13.45)
    await vi.waitFor(() => expect(api.get).toHaveBeenCalledTimes(2))
    expect(n.loading.value).toBe(true)
    expect(n.radius.value).toBe(1000)
    release({ data: [station('EnBW')] })
    await done
    expect(n.loading.value).toBe(false)
  })

  it('bricht bei einem Fehler ab, statt weiter zu erweitern', async () => {
    // Danach hätte die nächste Stufe Treffer: die Suche darf sie trotzdem nicht mehr abfragen
    vi.mocked(api.get).mockRejectedValueOnce(new Error('429')).mockResolvedValue({ data: [station('EnBW')] })
    const n = useNearbyStations()
    await n.load(52.53, 13.45)
    expect(api.get).toHaveBeenCalledTimes(1)
    expect(n.stations.value).toEqual([])
    expect(n.exhausted.value).toBe(false)
  })

  it('"Nicht dabei?" geht eine Stufe weiter und ersetzt die Liste', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [station('Aral')] }).mockResolvedValueOnce({ data: [station('Aral'), station('EnBW')] })
    const n = useNearbyStations()
    await n.load(52.53, 13.45)
    expect(n.radius.value).toBe(250)
    await n.expand()
    expect(radiusOf(vi.mocked(api.get).mock.calls[1])).toBe(1000)
    expect(n.stations.value).toHaveLength(2)
    expect(n.nextRadius.value).toBe(2500)
  })

  it('canExpand reagiert auf die erste Suche, auch wenn es vorher schon gelesen wurde', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [station('Aral')] })
    const n = useNearbyStations()
    expect(n.canExpand.value).toBe(false)
    await n.load(52.53, 13.45)
    expect(n.canExpand.value).toBe(true)
  })

  it('ein neuer Ort beginnt wieder bei 250 m', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] })
    const n = useNearbyStations()
    await n.load(52.53, 13.45)
    vi.mocked(api.get).mockReset().mockResolvedValue({ data: [station('EnBW')] })
    await n.load(48.1, 11.5)
    expect(vi.mocked(api.get).mock.calls.map(radiusOf)).toEqual([250])
    expect(n.radius.value).toBe(250)
  })
})
