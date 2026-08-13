import { describe, it, expect, vi, beforeEach } from 'vitest'
import api from '../axios'
import teslaFleetService from '../teslaFleetService'

vi.mock('../axios', () => ({
  default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
}))

beforeEach(() => vi.clearAllMocks())

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
