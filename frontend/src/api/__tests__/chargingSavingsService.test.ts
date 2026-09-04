// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import api from '../axios'
import chargingSavingsService from '../chargingSavingsService'

vi.mock('../axios', () => ({
  default: { get: vi.fn(), patch: vi.fn() },
}))

beforeEach(() => vi.clearAllMocks())

describe('chargingSavingsService.get', () => {
  it('reicht die Zahlen samt Trial-Kontext durch', async () => {
    vi.mocked(api.get).mockResolvedValue({
      status: 200,
      data: { savingsEur: 83.2, viaTrial: true, trialEndsAt: '2026-10-03' },
    })

    const result = await chargingSavingsService.get()

    expect(result.entitled).toBe(true)
    expect(result.locked).toBe(false)
    expect(result.viaTrial).toBe(true)
    expect(result.trialEndsAt).toBe('2026-10-03')
    expect(result.savings?.savingsEur).toBe(83.2)
    expect(result.dismissed).toBe(false)
  })

  it('reicht das Ausblenden-Flag durch', async () => {
    vi.mocked(api.get).mockResolvedValue({
      status: 200,
      data: { savingsEur: 83.2, viaTrial: false, trialEndsAt: null, dismissed: true },
    })

    const result = await chargingSavingsService.get()

    expect(result.dismissed).toBe(true)
    expect(result.savings?.savingsEur).toBe(83.2)
  })

  it('zahlender Nutzer: berechtigt, aber kein Trial-Hinweis', async () => {
    vi.mocked(api.get).mockResolvedValue({
      status: 200,
      data: { savingsEur: 83.2, viaTrial: false, trialEndsAt: null },
    })

    const result = await chargingSavingsService.get()

    expect(result.entitled).toBe(true)
    expect(result.viaTrial).toBe(false)
    expect(result.trialEndsAt).toBeNull()
  })

  it('204 heisst berechtigt, aber keine relevante Kachel - nichts anzeigen, kein Trial-Hinweis', async () => {
    vi.mocked(api.get).mockResolvedValue({ status: 204, data: '' })

    const result = await chargingSavingsService.get()

    expect(result.savings).toBeNull()
    expect(result.entitled).toBe(true)
    expect(result.locked).toBe(false)
    expect(result.viaTrial).toBe(false)
    expect(result.dismissed).toBe(false)
  })

  it('403 nach Trial-Ende: nicht berechtigt und gesperrt - dann zeigt das Dashboard den Teaser', async () => {
    vi.mocked(api.get).mockRejectedValue({ response: { status: 403 } })

    const result = await chargingSavingsService.get()

    expect(result.entitled).toBe(false)
    expect(result.locked).toBe(true)
    expect(result.savings).toBeNull()
  })

  it('401 ohne Token: nicht berechtigt, aber nicht gesperrt - kein Teaser bei unklarem Zustand', async () => {
    vi.mocked(api.get).mockRejectedValue({ response: { status: 401 } })

    const result = await chargingSavingsService.get()

    expect(result.entitled).toBe(false)
    expect(result.locked).toBe(false)
  })

  it('reicht unerwartete Fehler durch, statt sie zu verschlucken', async () => {
    vi.mocked(api.get).mockRejectedValue({ response: { status: 500 } })

    await expect(chargingSavingsService.get()).rejects.toMatchObject({ response: { status: 500 } })
  })
})
