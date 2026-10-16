// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, defineComponent, h, reactive, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '../../../i18n'
import { analytics } from '../../../services/analytics'
import ProfileTune from '../ProfileTune.vue'
import type { PersonalRangeInputs } from '../../../api/personalRangeService'

vi.mock('../../../services/analytics', () => ({ analytics: { track: vi.fn() } }))

const INPUTS: PersonalRangeInputs = { commuteKmPerDay: 40, commuteDaysPerWeek: 3, highwaySharePercent: 30, homeCharging: true, longTripKm: 500, pricePerKwh: null }

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

function mount(inputs: Partial<PersonalRangeInputs> = {}, communityPrice: number | null = 0.31) {
  setActivePinia(createPinia())
  const state = reactive({ home: null as boolean | null, price: undefined as number | null | undefined, inputs: { ...INPUTS, ...inputs } })
  const Host = defineComponent({
    setup: () => () => h(ProfileTune, {
      modelName: 'Volkswagen ID.3', inputs: state.inputs, communityPrice,
      onHome: (v: boolean) => { state.home = v },
      onPrice: (v: number | null) => { state.price = v },
    }),
  })
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(Host)
  app.use(i18n)
  app.mount(host)
  return { host, state }
}

describe('ProfileTune', () => {
  beforeEach(() => { vi.mocked(analytics.track).mockReset() })

  it('the wallbox answer goes back to the page, tracked', () => {
    const { host, state } = mount()
    ;(host.querySelector('[data-testid="wallbox-no"]') as HTMLButtonElement).click()
    expect(state.home).toBe(false)
    expect(analytics.track).toHaveBeenCalledWith('personal_range_wallbox', { model: 'Volkswagen ID.3', home: false })
  })

  it('without an own price the slider starts at the community price, with one there is a way back', () => {
    const community = mount()
    expect((community.host.querySelector('[data-testid="slider-price"]') as HTMLInputElement).value).toBe('0.31')
    expect(community.host.textContent).toContain('Startwert ist der Schnitt aller Fahrer')
    app?.unmount(); document.body.innerHTML = ''

    const own = mount({ pricePerKwh: 0.28 })
    expect((own.host.querySelector('[data-testid="slider-price"]') as HTMLInputElement).value).toBe('0.28')
    const reset = [...own.host.querySelectorAll('button')].find(b => b.textContent?.includes('Zurück zum Community-Preis'))!
    reset.click()
    expect(own.state.price).toBeNull()
  })
})
