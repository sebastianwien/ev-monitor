import { describe, it, expect, vi, beforeEach } from 'vitest'
import { tariffLocationParams } from '../tariffLocation'
import { applyTariffToLocationIfRequested } from '../applyTariffToLocation'
import api from '../../api/axios'

vi.mock('../../api/axios', () => ({
  default: { patch: vi.fn() },
}))

const base = {
  latitude: null as number | null,
  longitude: null as number | null,
  isPublicCharging: false,
  geohash: null as string | null,
  chargingProviderId: 'card-1',
  applyTariffToLocation: true,
}

describe('tariffLocationParams', () => {
  it('uses lat/lon when creating a new log', () => {
    expect(tariffLocationParams({ ...base, latitude: 52.5, longitude: 13.4, isPublicCharging: true }))
      .toEqual({ lat: 52.5, lon: 13.4, isPublic: true })
  })

  it('falls back to the stored geohash when editing - lat/lon are never persisted', () => {
    expect(tariffLocationParams({ ...base, geohash: 'u1mc1v8' }))
      .toEqual({ geohash: 'u1mc1v8' })
  })

  it('prefers a freshly picked position over the logs old geohash', () => {
    expect(tariffLocationParams({ ...base, latitude: 48.1, longitude: 11.5, geohash: 'u1mc1v8' }))
      .toEqual({ lat: 48.1, lon: 11.5, isPublic: false })
  })

  it('returns null when there is no location at all', () => {
    expect(tariffLocationParams(base)).toBeNull()
  })

  it('needs both coordinates - a lone latitude is not a location', () => {
    expect(tariffLocationParams({ ...base, latitude: 52.5 })).toBeNull()
  })
})

describe('applyTariffToLocationIfRequested', () => {
  beforeEach(() => vi.clearAllMocks())

  it('does nothing when the user did not tick the box', async () => {
    const priced = await applyTariffToLocationIfRequested({
      ...base, geohash: 'u1mc1v8', applyTariffToLocation: false,
    })

    expect(priced).toBe(0)
    expect(api.patch).not.toHaveBeenCalled()
  })

  it('does nothing without a charging card', async () => {
    const priced = await applyTariffToLocationIfRequested({
      ...base, geohash: 'u1mc1v8', chargingProviderId: null,
    })

    expect(priced).toBe(0)
    expect(api.patch).not.toHaveBeenCalled()
  })

  it('does nothing without a location', async () => {
    const priced = await applyTariffToLocationIfRequested(base)

    expect(priced).toBe(0)
    expect(api.patch).not.toHaveBeenCalled()
  })

  it('sends geohash plus card and reports how many logs were priced', async () => {
    vi.mocked(api.patch).mockResolvedValue({ data: { priced: 43 } } as any)

    const priced = await applyTariffToLocationIfRequested({ ...base, geohash: 'u1mc1v8' })

    expect(priced).toBe(43)
    expect(api.patch).toHaveBeenCalledWith('/logs/apply-tariff-at-location', {
      geohash: 'u1mc1v8',
      chargingProviderId: 'card-1',
    })
  })

  it('sends lat/lon plus isPublic when creating a new log', async () => {
    vi.mocked(api.patch).mockResolvedValue({ data: { priced: 2 } } as any)

    await applyTariffToLocationIfRequested({
      ...base, latitude: 52.5, longitude: 13.4, isPublicCharging: true,
    })

    expect(api.patch).toHaveBeenCalledWith('/logs/apply-tariff-at-location', {
      lat: 52.5, lon: 13.4, isPublic: true, chargingProviderId: 'card-1',
    })
  })

  it('swallows a failed backfill - the saved log must not be called into question', async () => {
    vi.mocked(api.patch).mockRejectedValue(new Error('boom'))

    await expect(applyTariffToLocationIfRequested({ ...base, geohash: 'u1mc1v8' }))
      .resolves.toBe(0)
  })
})
