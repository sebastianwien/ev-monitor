import { describe, it, expect, vi } from 'vitest'
import { nextTick } from 'vue'
import { useStationsData } from '../useStationsData'
import type { MapStation, StationMapResponse } from '../../api/chargingStationService'

const station = (id: string): MapStation => ({
  id, name: 'EnBW', known: true, latitude: 52.37, longitude: 9.73, maxAcKw: 22, maxDcKw: null, chargePoints: 2,
  plugTypes: ['Typ 2'], address: null, openingHours: null, payment: null, priceAc: null, priceDc: null,
})

const ok = (cell: string, ...ids: string[]): StationMapResponse =>
  ({ cells: [{ cell, inGermany: true, stations: ids.map(station) }], missing: [] })

const flush = async () => { for (let i = 0; i < 5; i++) { await Promise.resolve(); await nextTick() } }

describe('useStationsData', () => {
  it('fragt jedes Feld einzeln und einmal, Säulen in mehreren Feldern zählen einmal', async () => {
    const cells = vi.fn((c: string[]) => Promise.resolve(ok(c[0], c[0] + '-1', 'shared')))
    const data = useStationsData({ cells })

    data.request(['u1qcvx', 'u1qcvz'])
    data.request(['u1qcvx'])
    await flush()

    expect(cells).toHaveBeenCalledTimes(2)
    expect(cells.mock.calls.map(c => c[0])).toEqual([['u1qcvx'], ['u1qcvz']])
    expect(data.stations.value.map(s => s.id).sort()).toEqual(['shared', 'u1qcvx-1', 'u1qcvz-1'])
    expect(data.isLoaded('u1qcvx')).toBe(true)
  })

  it('höchstens vier Felder gleichzeitig, das mittlere zuerst', async () => {
    const resolvers: (() => void)[] = []
    const cells = vi.fn((c: string[]) => new Promise<StationMapResponse>(r => resolvers.push(() => r(ok(c[0])))))
    const data = useStationsData({ cells })

    data.request(['a00000', 'b00000', 'c00000', 'd00000', 'e00000', 'f00000'])
    await flush()
    expect(cells).toHaveBeenCalledTimes(4)
    expect(cells.mock.calls[0][0]).toEqual(['a00000'])
    expect(data.loading.value).toBe(true)

    resolvers.shift()!()
    await flush()
    expect(cells).toHaveBeenCalledTimes(5)
    resolvers.splice(0).forEach(r => r())
    await flush()
    resolvers.splice(0).forEach(r => r())
    await flush()
    expect(data.loading.value).toBe(false)
  })

  it('fehlende Felder (Drossel, Register) bleiben offen und kommen mit retry erneut', async () => {
    const cells = vi.fn()
      .mockResolvedValueOnce({ cells: [], missing: ['u1qcvx'] })
      .mockResolvedValueOnce(ok('u1qcvx', '1'))
    const data = useStationsData({ cells })

    data.request(['u1qcvx'])
    await flush()
    expect(data.failed.value).toBe(true)
    data.request(['u1qcvx'])
    await flush()
    expect(cells).toHaveBeenCalledTimes(1)

    data.retry()
    await flush()
    expect(cells).toHaveBeenCalledTimes(2)
    expect(data.failed.value).toBe(false)
    expect(data.stations.value).toHaveLength(1)
  })

  it('Netzfehler bei einem Feld: die anderen kommen trotzdem', async () => {
    const cells = vi.fn((c: string[]) => c[0] === 'bad000' ? Promise.reject(new Error('offline')) : Promise.resolve(ok(c[0], '1')))
    const data = useStationsData({ cells })

    data.request(['bad000', 'u1qcvx'])
    await flush()

    expect(data.stations.value).toHaveLength(1)
    expect(data.failed.value).toBe(true)
  })

  it('Feld außerhalb Deutschlands ist bekannt', async () => {
    const cells = vi.fn(() => Promise.resolve({ cells: [{ cell: 'u2edk8', inGermany: false, stations: [] }], missing: [] }))
    const data = useStationsData({ cells })

    data.request(['u2edk8'])
    await flush()

    expect(data.cellInfo('u2edk8')?.inGermany).toBe(false)
  })

  it('beim Verlassen bricht es ab und übernimmt späte Antworten nicht mehr', async () => {
    let resolve: (r: StationMapResponse) => void = () => {}
    let signal: AbortSignal | undefined
    const cells = vi.fn((_c: string[], s?: AbortSignal) => { signal = s; return new Promise<StationMapResponse>(r => { resolve = r }) })
    const data = useStationsData({ cells })

    data.request(['u1qcvx'])
    await flush()
    data.dispose()
    resolve(ok('u1qcvx', '1'))
    await flush()

    expect(signal?.aborted).toBe(true)
    expect(data.stations.value).toHaveLength(0)
  })
})
