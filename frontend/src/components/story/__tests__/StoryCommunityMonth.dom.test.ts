// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'

vi.mock('../../../api/axios', () => ({ default: { get: vi.fn() } }))

import apiClient from '../../../api/axios'
import { i18n } from '../../../i18n'
import StoryCommunityMonth from '../StoryCommunityMonth.vue'
import type { RawTickerItem } from '../../../composables/useTickerItems'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; vi.mocked(apiClient.get).mockReset() })
const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }

const stat = (messageKey: string, params: Record<string, string>, variant: RawTickerItem['variant'] = 'energy'): RawTickerItem =>
  ({ type: 'STAT', messageKey, params: { month: '10', ...params }, variant })

function mount(items: RawTickerItem[], events: string[] = []) {
  vi.mocked(apiClient.get).mockImplementation((url: string) =>
    Promise.resolve({ data: url.includes('month=2026-10') ? items : [] }) as never)
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryCommunityMonth, { year: 2026, month: 10, onRank: () => events.push('rank') })
  app.component('router-link', RouterLink)
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}
const flush = () => new Promise(r => setTimeout(r, 0))
const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim() ?? ''

describe('StoryCommunityMonth', () => {
  it('nennt den Monat nur im Titel, alle Kategorien in fester Reihenfolge und den Weg zur Rangliste', async () => {
    const events: string[] = []
    const host = mount([
      stat('wind', { amount: '16', unit: 'hours' }, 'eco'),
      stat('top_provider', { provider: 'EnBW', count: '312' }),
      stat('savings_vs_fuel', { amount: '12000', price: '1.75' }, 'money'),
      stat('stat_base', { kwh: '48300', charges: '2140' }),
      { type: 'LEADER', messageKey: 'leader', params: { category: 'MONTHLY_KWH', name: 'anna', value: '900' }, variant: 'leader' },
    ], events)
    await flush(); await flush()

    expect(vi.mocked(apiClient.get)).toHaveBeenCalledWith('/public/leaderboard/ticker?month=2026-10')
    expect(text(host.querySelector('h2'))).toBe('Die Community im Oktober')
    const lines = [...host.querySelectorAll('[data-testid="story-community-line"]')].map(text)
    expect(lines).toEqual([
      '48.300 kWh in 2.140 Ladevorgängen geladen',
      '12.000 € gespart gegenüber Verbrennern (Benzin 1,75 €/L)',
      'Ein Windrad müsste dafür 16 Stunden laufen',
      'Beliebtester öffentlicher Ladeanbieter: EnBW',
    ])
    expect(lines.join(' ')).not.toMatch(/Oktober|Community/)
    const bold = [...host.querySelectorAll('[data-testid="story-community-line"] strong')].map(text)
    expect(bold).toEqual(['48.300', '2.140', '12.000', '1,75', '16'])
    const link = host.querySelector('a[href="/leaderboard"]')!
    expect(text(link)).toBe('Rangliste ansehen')
    ;(link as HTMLElement).click()
    expect(events).toEqual(['rank'])
  })

  it('zeigt ohne Community-Daten nichts', async () => {
    const host = mount([])
    await flush(); await flush()
    expect(host.querySelector('[data-testid="story-community-month"]')).toBeNull()
  })
})
