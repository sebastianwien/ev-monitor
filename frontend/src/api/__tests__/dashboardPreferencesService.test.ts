// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import api from '../axios'
import dashboardPreferencesService from '../dashboardPreferencesService'

vi.mock('../axios', () => ({
  default: { get: vi.fn(), patch: vi.fn() },
}))

beforeEach(() => vi.clearAllMocks())

describe('dashboardPreferencesService', () => {
  it('liest den Ausblenden-Zustand der Ersparnis-Kachel', async () => {
    vi.mocked(api.get).mockResolvedValue({ status: 200, data: { savingsCardDismissed: true } })

    const result = await dashboardPreferencesService.get()

    expect(result.savingsCardDismissed).toBe(true)
  })

  it('blendet die Ersparnis-Kachel aus', async () => {
    vi.mocked(api.patch).mockResolvedValue({ status: 204 })

    await dashboardPreferencesService.setSavingsCardDismissed(true)

    expect(api.patch).toHaveBeenCalledWith('/users/me/dashboard-preferences', { savingsCardDismissed: true })
  })

  it('blendet die Ersparnis-Kachel wieder ein', async () => {
    vi.mocked(api.patch).mockResolvedValue({ status: 204 })

    await dashboardPreferencesService.setSavingsCardDismissed(false)

    expect(api.patch).toHaveBeenCalledWith('/users/me/dashboard-preferences', { savingsCardDismissed: false })
  })
})
