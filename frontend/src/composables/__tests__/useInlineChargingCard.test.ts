import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../../api/axios', () => ({
  default: { post: vi.fn() },
}))
import api from '../../api/axios'
import { useInlineChargingCard, CUSTOM_PROVIDER } from '../useInlineChargingCard'

// EUR-Land: der User tippt ct/kWh, gespeichert wird EUR/kWh.
const centsToEur = (v: number) => v / 100

const savedCard = {
  id: 'card-1', providerName: 'EnBW mobility+', label: null,
  acPricePerKwh: 0.39, dcPricePerKwh: 0.59,
  monthlyFeeEur: 0, sessionFeeEur: 0, activeFrom: '2026-07-12', activeUntil: null,
}

describe('useInlineChargingCard', () => {
  beforeEach(() => {
    vi.mocked(api.post).mockReset()
    vi.mocked(api.post).mockResolvedValue({ data: savedCard } as never)
  })

  it('speichert erst wenn ein Anbieter gewaehlt ist', () => {
    const card = useInlineChargingCard(centsToEur)
    expect(card.canSave.value).toBe(false)

    card.draft.value.providerName = 'EnBW mobility+'
    expect(card.canSave.value).toBe(true)
  })

  it('verlangt bei "Anderer Anbieter" einen eigenen Namen', () => {
    const card = useInlineChargingCard(centsToEur)
    card.draft.value.providerName = CUSTOM_PROVIDER

    expect(card.canSave.value).toBe(false)

    card.draft.value.customProviderName = '  Stadtwerke Wien  '
    expect(card.canSave.value).toBe(true)
    expect(card.resolvedName.value).toBe('Stadtwerke Wien')
  })

  it('rechnet die getippten ct/kWh in EUR/kWh um', async () => {
    const card = useInlineChargingCard(centsToEur)
    card.draft.value.providerName = 'EnBW mobility+'
    card.draft.value.acPrice = 39
    card.draft.value.dcPrice = 59

    await card.save()

    expect(api.post).toHaveBeenCalledWith('/users/me/charging-providers', expect.objectContaining({
      providerName: 'EnBW mobility+',
      acPricePerKwh: 0.39,
      dcPricePerKwh: 0.59,
    }))
  })

  it('laesst leere Preise leer statt sie auf 0 zu setzen', async () => {
    const card = useInlineChargingCard(centsToEur)
    card.draft.value.providerName = 'EnBW mobility+'

    await card.save()

    expect(api.post).toHaveBeenCalledWith('/users/me/charging-providers', expect.objectContaining({
      acPricePerKwh: null,
      dcPricePerKwh: null,
    }))
  })

  it('gibt die gespeicherte Karte zurueck und schliesst das Formular', async () => {
    const card = useInlineChargingCard(centsToEur)
    card.open()
    card.draft.value.providerName = 'EnBW mobility+'

    const created = await card.save()

    expect(created).toEqual(savedCard)
    expect(card.isOpen.value).toBe(false)
    expect(card.draft.value.providerName).toBe('')
  })

  it('haelt das Formular offen und meldet den Fehler wenn das Speichern scheitert', async () => {
    vi.mocked(api.post).mockRejectedValue(new Error('500'))
    const card = useInlineChargingCard(centsToEur)
    card.open()
    card.draft.value.providerName = 'EnBW mobility+'

    const created = await card.save()

    expect(created).toBeNull()
    expect(card.failed.value).toBe(true)
    expect(card.isOpen.value).toBe(true)
    expect(card.draft.value.providerName).toBe('EnBW mobility+')
  })

  it('speichert nicht doppelt solange ein Request laeuft', async () => {
    const card = useInlineChargingCard(centsToEur)
    card.draft.value.providerName = 'EnBW mobility+'

    const inFlight = card.save()
    expect(card.canSave.value).toBe(false)
    await inFlight
    await card.save()

    expect(api.post).toHaveBeenCalledTimes(1)
  })
})
