// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '../../../i18n'

vi.mock('../../../api/axios', () => ({ default: { post: vi.fn(), get: vi.fn() } }))
vi.mock('../../../services/analytics', () => ({ analytics: { track: vi.fn() } }))
import api from '../../../api/axios'
import { analytics } from '../../../services/analytics'
import NeedsCheckCard from '../NeedsCheckCard.vue'

const TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiJ1MSIsInN1YiI6Im5ldUBleGFtcGxlLmNvbSJ9.x'
const SUMMARY = { total: 56, weeklyOk: 40, tripOk: 9, tripOkWithin: [9, 30, 50, 56, 56] }

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; sessionStorage.clear() })

const ANSWERED = { annualKm: null, highwayShare: null, dailyKm: 40, commuteDays: 3, longestTripKm: 400, homeCharging: null, maxStops: 0 }
const EMPTY = { annualKm: null, highwayShare: null, dailyKm: null, commuteDays: null, longestTripKm: null, homeCharging: null, maxStops: 0 }

function mount(locked: boolean, modelValue: Record<string, unknown> = ANSWERED, tripOnly = false) {
  setActivePinia(createPinia())
  const events: string[] = []
  const trips: (number | null)[] = []
  const updates: Record<string, unknown>[] = []
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(NeedsCheckCard, {
    modelValue,
    summary: SUMMARY, tripOnly, locked,
    onUnlocked: () => events.push('unlocked'),
    onTrip: (n: number | null) => trips.push(n),
    'onUpdate:modelValue': (v: Record<string, unknown>) => updates.push(v),
  })
  app.use(i18n)
  app.mount(host)
  return { host, events, updates, trips }
}

async function flush() { for (let i = 0; i < 4; i++) { await nextTick(); await Promise.resolve() } }
async function fill(host: HTMLElement, selector: string, value: string) {
  const input = host.querySelector(selector) as HTMLInputElement
  input.value = value
  input.dispatchEvent(new Event('input'))
  await nextTick()
}

describe('NeedsCheckCard answers', () => {
  it('starts empty: the five sliders of the model page, all open, no chips, no unlock', () => {
    const { host } = mount(false, EMPTY)
    const values = [...host.querySelectorAll('output')].map(o => o.textContent!.trim())
    expect(values).toEqual(['noch offen', 'noch offen', 'noch offen', 'noch offen', 'noch offen'])
    expect(host.querySelector('[data-testid="needs-prompt"]')).not.toBeNull()
    expect(host.querySelector('[data-testid^="needs-stops-"]')).toBeNull()
    expect(host.querySelector('[data-testid="needs-unlock"]')).toBeNull()
  })

  it('a slider answer lands in the field the model page shares', () => {
    const { host, updates } = mount(false, EMPTY)
    const slider = host.querySelector('[data-testid="slider-needs-commute"]') as HTMLInputElement
    slider.value = '35'
    slider.dispatchEvent(new Event('change'))
    expect(updates[updates.length - 1]).toMatchObject({ dailyKm: 35 })
  })

  it('the longest trip alone brings one tile per stop count, up to the first that every model makes', () => {
    const { host } = mount(false, { ...EMPTY, longestTripKm: 400 })
    const tiles = [...host.querySelectorAll('[data-testid^="needs-stops-"]')]
    expect(tiles.map(t => t.querySelector('b')!.textContent)).toEqual(['9', '30', '50', '56'])
  })

  it('a tile filters the list to its models, the active tile shows all again', async () => {
    const off = mount(false, { ...ANSWERED, maxStops: 0 })
    ;(off.host.querySelector('[data-testid="needs-stops-2"]') as HTMLButtonElement).click()
    expect(off.trips).toEqual([2])
    app?.unmount()
    const on = mount(false, { ...ANSWERED, maxStops: 2 }, true)
    const active = on.host.querySelector('[data-testid="needs-stops-2"]') as HTMLButtonElement
    expect(active.getAttribute('aria-pressed')).toBe('true')
    active.click()
    expect(on.trips).toEqual([null])
  })
})

describe('NeedsCheckCard gate', () => {
  beforeEach(() => { vi.mocked(api.post).mockReset(); vi.mocked(analytics.track).mockReset() })

  it('signed in: the wallbox question follows the commute, nothing about a mail', async () => {
    const { host, updates } = mount(false)
    expect(host.querySelector('[data-testid="needs-stops-0"]')).not.toBeNull()
    expect(host.querySelector('[data-testid="needs-locked"]')).toBeNull()
    expect(host.querySelector('[data-testid="needs-unlock"]')).toBeNull()
    ;[...host.querySelectorAll('[data-testid="needs-home"] button')][1].dispatchEvent(new Event('click'))
    expect(updates[updates.length - 1]).toMatchObject({ homeCharging: false })
  })

  it('guest: the chips stay free, the commute figure waits behind the unlock, no wallbox question yet', () => {
    const { host } = mount(true)
    expect(host.querySelector('[data-testid="needs-home"]')).toBeNull()
    expect(host.querySelector('[data-testid="needs-stops-0"]')).not.toBeNull()
    expect(host.querySelector('[data-testid="needs-locked"]')).not.toBeNull()
    expect(host.querySelector('[data-testid="needs-unlock"]')).not.toBeNull()
    expect(host.querySelector('input[type="email"]')).toBeNull()
  })

  it('guest with the trip only: no commute line, no unlock yet', () => {
    const { host } = mount(true, { ...EMPTY, longestTripKm: 400 })
    expect(host.querySelector('[data-testid="needs-unlock"]')).toBeNull()
    expect(host.querySelector('[data-testid="needs-commute-answer"]')).toBeNull()
  })

  it('guest: a stop chip names its count but not the models; the gate opens, after it the list filters to them', async () => {
    vi.mocked(api.post)
      .mockResolvedValueOnce({ data: { status: 'SENT' } } as never)
      .mockResolvedValueOnce({ data: { token: TOKEN, isPremium: false } } as never)
    const { host, trips } = mount(true)
    ;(host.querySelector('[data-testid="needs-stops-1"]') as HTMLButtonElement).click()
    await flush()
    expect(trips).toEqual([])
    expect(host.querySelector('[data-testid="needs-gate"]')!.textContent).toContain('Deine 30 Modelle für 400 km sind bereit.')

    await fill(host, 'input[type="email"]', 'neu@example.com')
    host.querySelector('form')!.dispatchEvent(new Event('submit'))
    await flush()
    await fill(host, 'input[autocomplete="one-time-code"]', '123456')
    host.querySelector('form')!.dispatchEvent(new Event('submit'))
    await flush()
    expect(trips).toEqual([1])
  })

  it('the button opens the inline gate and a confirmed code reports the unlock', async () => {
    vi.mocked(api.post)
      .mockResolvedValueOnce({ data: { status: 'SENT' } } as never)
      .mockResolvedValueOnce({ data: { token: TOKEN, isPremium: false } } as never)
    const { host, events } = mount(true)

    ;(host.querySelector('[data-testid="needs-unlock"]') as HTMLButtonElement).click()
    await flush()
    expect(analytics.track).toHaveBeenCalledWith('ranking_gate_shown')
    expect(host.textContent).toContain('56 Modelle')
    expect(host.querySelector('input[type="email"]')).not.toBeNull()

    await fill(host, 'input[type="email"]', 'neu@example.com')
    host.querySelector('form')!.dispatchEvent(new Event('submit'))
    await flush()
    await fill(host, 'input[autocomplete="one-time-code"]', '123456')
    host.querySelector('form')!.dispatchEvent(new Event('submit'))
    await flush()

    expect(api.post).toHaveBeenLastCalledWith('/auth/code/verify', expect.objectContaining({ utmMedium: 'gate', utmCampaign: 'modelle:rangliste' }))
    expect(events).toEqual(['unlocked'])
  })
})
