// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import api from '../axios'
import teslaFleetService from '../teslaFleetService'

vi.mock('../axios', () => ({
  default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
}))

beforeEach(() => {
  vi.clearAllMocks()
  Object.defineProperty(window, 'location', { value: { href: '' }, writable: true })
})

describe('startReconnect', () => {
  it('redirects to the auth URL when the Fleet API is configured', async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: { authUrl: 'https://auth.tesla.com/oauth2/v3/authorize?client_id=x', fleetApiConfigured: true },
    })

    const result = await teslaFleetService.startReconnect('car-1')

    expect(api.get).toHaveBeenCalledWith('/tesla/fleet/auth/start', { params: { carId: 'car-1' } })
    expect(window.location.href).toBe('https://auth.tesla.com/oauth2/v3/authorize?client_id=x')
    expect(result).toBe('redirected')
  })

  it('reports not_configured without redirecting when the Fleet API is not set up', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { authUrl: null, fleetApiConfigured: false } })

    const result = await teslaFleetService.startReconnect('car-1')

    expect(window.location.href).toBe('')
    expect(result).toBe('not_configured')
  })

  it('reports not_configured when configured but no authUrl came back', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { authUrl: null, fleetApiConfigured: true } })

    const result = await teslaFleetService.startReconnect('car-1')

    expect(window.location.href).toBe('')
    expect(result).toBe('not_configured')
  })
})

describe('repushAllTelemetry', () => {
  it('ruft den Admin-Endpoint und reicht das Ergebnis durch', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { total: 12, pushed: 11, failed: 1 } })

    const result = await teslaFleetService.repushAllTelemetry()

    expect(api.post).toHaveBeenCalledWith('/tesla/pairing/repush-all-telemetry')
    expect(result).toEqual({ total: 12, pushed: 11, failed: 1 })
  })

  it('reicht Fehler durch, statt sie zu verschlucken', async () => {
    // Der Admin muss sehen, wenn der Repush gar nicht erst losgelaufen ist.
    vi.mocked(api.post).mockRejectedValue({ response: { status: 403 } })

    await expect(teslaFleetService.repushAllTelemetry()).rejects.toMatchObject({
      response: { status: 403 },
    })
  })
})
