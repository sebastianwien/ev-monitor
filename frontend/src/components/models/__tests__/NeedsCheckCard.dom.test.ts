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

const ANSWERED = { dailyKm: 40, commuteDays: 3, longestTripKm: 400, homeCharging: true, maxStops: 0 }
const EMPTY = { dailyKm: null, commuteDays: null, longestTripKm: null, homeCharging: null, maxStops: 0 }

function mount(locked: boolean, modelValue: Record<string, unknown> = ANSWERED) {
  setActivePinia(createPinia())
  const events: string[] = []
  const updates: Record<string, unknown>[] = []
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(NeedsCheckCard, {
    modelValue,
    summary: SUMMARY, tripOnly: false, locked,
    onUnlocked: () => events.push('unlocked'),
    'onUpdate:modelValue': (v: Record<string, unknown>) => updates.push(v),
  })
  app.use(i18n)
  app.mount(host)
  return { host, events, updates }
}

async function flush() { for (let i = 0; i < 4; i++) { await nextTick(); await Promise.resolve() } }
async function fill(host: HTMLElement, selector: string, value: string) {
  const input = host.querySelector(selector) as HTMLInputElement
  input.value = value
  input.dispatchEvent(new Event('input'))
  await nextTick()
}

describe('NeedsCheckCard answers', () => {
  it('starts empty: no values, no counter, a prompt instead', () => {
    const { host } = mount(false, EMPTY)
    expect((host.querySelector('[data-testid="needs-daily"]') as HTMLInputElement).value).toBe('')
    expect((host.querySelector('[data-testid="needs-longest"]') as HTMLInputElement).value).toBe('')
    expect(host.querySelectorAll('[aria-pressed="true"]')).toHaveLength(0)
    expect(host.textContent).not.toContain('von 56 Modellen')
    expect(host.querySelector('[data-testid="needs-summary"]')!.textContent).toContain('Beantworte die Fragen, dann bewerten wir jedes Modell für dich.')
    expect(host.querySelector('[data-testid="needs-unlock"]')).toBeNull()
  })

  it('asks the commute days with chips 1 to 5', async () => {
    const { host, updates } = mount(false, EMPTY)
    expect(host.textContent).toContain('An wie vielen Tagen pro Woche pendelst du?')
    const chips = [...host.querySelectorAll('[data-testid="needs-days"] button')] as HTMLButtonElement[]
    expect(chips.map(c => c.textContent!.trim())).toEqual(['1', '2', '3', '4', '5'])
    chips[2].click()
    await nextTick()
    expect(updates[updates.length - 1]).toMatchObject({ commuteDays: 3 })
  })

  it('an emptied field is unanswered again, not zero', async () => {
    const { host, updates } = mount(false, ANSWERED)
    await fill(host, '[data-testid="needs-daily"]', '')
    ;(host.querySelector('[data-testid="needs-daily"]') as HTMLInputElement).dispatchEvent(new Event('blur'))
    await nextTick()
    expect(updates[updates.length - 1]).toMatchObject({ dailyKm: null })
  })

  it('the counter comes with the longest trip alone', () => {
    const { host } = mount(false, { ...EMPTY, longestTripKm: 400 })
    expect(host.textContent).toContain('9 von 56 Modellen')
  })
})

describe('NeedsCheckCard gate', () => {
  beforeEach(() => { vi.mocked(api.post).mockReset(); vi.mocked(analytics.track).mockReset() })

  it('signed in: the figures line stays, nothing about a mail', () => {
    const { host } = mount(false)
    expect(host.textContent).toContain('9 von 56 Modellen')
    expect(host.querySelector('[data-testid="needs-unlock"]')).toBeNull()
  })

  it('guest: the count stays free, the per-model figures are announced behind the mail', () => {
    const { host } = mount(true)
    expect(host.textContent).toContain('9 von 56 Modellen')
    expect(host.querySelector('[data-testid="needs-locked"]')!.textContent).toContain('400 km')
    expect(host.querySelector('[data-testid="needs-locked"]')!.textContent).toContain('nach Bestätigung deiner Mail')
    expect(host.querySelector('input[type="email"]')).toBeNull()
  })

  it('guest with the commute only: the gate offers the charging figures', () => {
    const { host } = mount(true, { ...ANSWERED, longestTripKm: null })
    expect(host.querySelector('[data-testid="needs-locked"]')!.textContent).toContain('fürs Pendeln')
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
