// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, defineComponent, h, nextTick, reactive, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '../../../i18n'
import { useAuthStore } from '../../../stores/auth'

vi.mock('../../../api/axios', () => ({ default: { post: vi.fn(), get: vi.fn() } }))
vi.mock('../../../services/analytics', () => ({ analytics: { track: vi.fn() } }))
import api from '../../../api/axios'
import { analytics } from '../../../services/analytics'
import RangeBandBar from '../RangeBandBar.vue'

const INPUTS = { commuteKmPerDay: 40, highwaySharePercent: 30, homeCharging: true, longTripKm: 500, pricePerKwh: null }

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; vi.useRealTimers() })

function mount(opts: { loggedIn?: boolean; locked?: boolean; answered?: number; fallback?: { minKm: number; maxKm: number } | null } = {}) {
  setActivePinia(createPinia())
  if (opts.loggedIn) {
    const auth = useAuthStore()
    auth.token = 'token'
    auth.user = { userId: 'u1' } as never
  }
  const state = reactive({ inputs: { ...INPUTS }, answered: opts.answered ?? 0, opened: 0, locked: opts.locked ?? false })
  const Host = defineComponent({
    setup: () => () => h(RangeBandBar, {
      brand: 'Volkswagen', model: 'ID.3', modelName: 'Volkswagen ID.3',
      inputs: state.inputs, answered: state.answered, total: 5, locked: state.locked,
      fallbackBand: opts.fallback === undefined ? { minKm: 230, maxKm: 300 } : opts.fallback,
      profile: [
        { key: 'highwayShare', label: 'Autobahn', value: '5.000 km' },
        { key: 'commute', label: 'Pendeln', value: null },
      ],
      onOpen: () => { state.opened++ },
    }),
  })
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(Host)
  app.use(i18n)
  app.mount(host)
  return { host, state }
}

async function flush() { for (let i = 0; i < 6; i++) { await nextTick(); await Promise.resolve() } }
const band = (host: HTMLElement) => host.querySelector('[data-testid="range-band"]')!.textContent ?? ''

describe('RangeBandBar', () => {
  beforeEach(() => {
    vi.mocked(api.post).mockReset()
    vi.mocked(analytics.track).mockReset()
  })

  it('shows the model band before any answer without calling the server', async () => {
    const { host } = mount()
    await flush()

    expect(band(host)).toContain('230')
    expect(band(host)).toContain('300')
    expect(host.textContent).toContain('Die Fahrer kommen')
    expect(api.post).not.toHaveBeenCalled()
    expect(analytics.track).toHaveBeenCalledWith('range_band_shown', { model: 'Volkswagen ID.3', data_level: 'MODEL' })
  })

  it('asks the visitor to answer when the model has no season band yet', async () => {
    const { host } = mount({ fallback: null })
    await flush()

    expect(host.textContent).toContain('Eine Frage beantworten')
    expect(host.querySelector('[data-testid="range-band"]')).toBeNull()
  })

  it('debounces answers into one preview call and shows the new band', async () => {
    vi.useFakeTimers()
    vi.mocked(api.post).mockResolvedValue({ data: { minKm: 210, maxKm: 280, dataLevel: 'MEASURED' } } as never)
    const { host, state } = mount()
    await flush()

    state.inputs.highwaySharePercent = 60
    state.answered = 1
    await flush()
    state.inputs.commuteKmPerDay = 80
    state.answered = 2
    await flush()
    expect(api.post).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(300)
    await flush()

    expect(api.post).toHaveBeenCalledTimes(1)
    expect(api.post).toHaveBeenCalledWith('/public/personal-range/Volkswagen/ID.3/preview',
      expect.objectContaining({ highwaySharePercent: 60, commuteKmPerDay: 80 }))
    expect(band(host)).toContain('210')
    expect(band(host)).toContain('280')
    expect(host.textContent).toContain('Mit deinem Profil kommst du mit vollem Akku')
    expect(host.textContent).toContain('2 von 5')
    expect(host.querySelectorAll('[data-testid="range-band-bar"] i.h-1').length).toBe(5)
  })

  it('keeps the band and notes the standard profile when the limit is exhausted', async () => {
    vi.useFakeTimers()
    vi.mocked(api.post).mockRejectedValue({ response: { status: 429 } })
    const { host, state } = mount()
    await flush()

    state.inputs.longTripKm = 800
    state.answered = 1
    await vi.advanceTimersByTimeAsync(300)
    await flush()

    expect(band(host)).toContain('230')
    expect(host.textContent).toContain('Standardprofil')
    expect(host.querySelector('[role="alert"]')).toBeNull()
  })

  it('logged-in visitors get the preview too: the calculator would store a half-answered profile', async () => {
    vi.useFakeTimers()
    vi.mocked(api.post).mockResolvedValue({ data: { minKm: 240, maxKm: 310, dataLevel: 'PARTIAL' } } as never)
    const { host, state } = mount({ loggedIn: true })
    await flush()

    state.inputs.longTripKm = 800
    state.answered = 1
    await vi.advanceTimersByTimeAsync(300)
    await flush()

    expect(api.post).toHaveBeenCalledWith('/public/personal-range/Volkswagen/ID.3/preview', expect.anything())
    expect(api.post).not.toHaveBeenCalledWith('/personal-range/Volkswagen/ID.3', expect.anything())
    expect(band(host)).toContain('240')
    expect(band(host)).toContain('310')
  })

  describe('locked (guest)', () => {
    it('shows no figure and asks nothing from the server, however many answers there are', async () => {
      vi.useFakeTimers()
      const { host, state } = mount({ locked: true })
      await flush()

      expect(host.querySelector('[data-testid="range-band"]')).toBeNull()
      expect(host.textContent).not.toContain('230')
      expect(host.textContent).toContain('Noch 5 Fragen')

      state.inputs.highwaySharePercent = 60
      state.answered = 1
      await vi.advanceTimersByTimeAsync(300)
      await flush()

      expect(api.post).not.toHaveBeenCalled()
      expect(host.querySelector('[data-testid="range-band"]')).toBeNull()
      expect(host.textContent).toContain('Noch 4 Fragen')
      expect(host.textContent).toContain('1 von 5')
      expect(analytics.track).not.toHaveBeenCalledWith('range_band_shown', expect.anything())
    })

    it('unlocking (the gate confirmed a code) fetches the band for the answers given so far', async () => {
      vi.useFakeTimers()
      vi.mocked(api.post).mockResolvedValue({ data: { minKm: 190, maxKm: 250, dataLevel: 'MEASURED' } } as never)
      const { host, state } = mount({ locked: true, answered: 5 })
      await flush()
      expect(api.post).not.toHaveBeenCalled()

      state.locked = false
      await flush()
      await vi.advanceTimersByTimeAsync(300)
      await flush()

      expect(api.post).toHaveBeenCalledTimes(1)
      expect(band(host)).toContain('190')
      expect(host.querySelector('[data-testid="range-band-locked"]')).toBeNull()
    })

    it('says the band is ready once every question is answered and keeps the button', async () => {
      const { host, state } = mount({ locked: true })
      state.answered = 5
      await flush()

      expect(host.textContent).toContain('Deine Spanne ist fertig')
      expect(host.textContent).toContain('Ladestopps')
      ;(host.querySelector('[data-testid="range-band-cta"]') as HTMLButtonElement).click()
      await flush()
      expect(state.opened).toBe(1)
    })
  })

  it('signed in with answers already there (profile or ranking): the band is fetched without a further change', async () => {
    vi.useFakeTimers()
    vi.mocked(api.post).mockResolvedValue({ data: { minKm: 200, maxKm: 260, dataLevel: 'MEASURED' } } as never)
    const { host } = mount({ loggedIn: true, answered: 3 })
    await flush()
    await vi.advanceTimersByTimeAsync(300)
    await flush()

    expect(api.post).toHaveBeenCalledTimes(1)
    expect(band(host)).toContain('200')
  })

  it('shows the profile rows with open questions marked', async () => {
    const { host } = mount()
    await flush()

    const rows = host.querySelector('[data-testid="range-band-profile"]')!.textContent
    expect(rows).toContain('Dein Fahrprofil')
    expect(rows).toContain('5.000 km')
    expect(rows).toContain('noch offen')
  })

  it('the button asks the page to open the exact result', async () => {
    const { host, state } = mount()
    await flush()

    ;(host.querySelector('[data-testid="range-band-cta"]') as HTMLButtonElement).click()
    await flush()

    expect(state.opened).toBe(1)
  })
})
