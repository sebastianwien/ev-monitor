// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '../../../i18n'
import { useAuthStore } from '../../../stores/auth'

const get = vi.fn()
vi.mock('../../../api/axios', () => ({ default: { get: (...args: unknown[]) => get(...args) } }))

import CommunityLeaderboard from '../CommunityLeaderboard.vue'

const entry = (rank: number, username: string) => ({
  rank, username, carLabel: 'Tesla Model 3', value: 100 - rank, unit: 'kWh',
  previousRank: null, rankDelta: null, isNew: false, kwhTotal: null, sessionCount: null,
})

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; get.mockReset() })

async function mount(response: object, username = 'me') {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  auth.token = 'token'
  auth.user = { userId: 'u1', username } as never
  get.mockResolvedValue({ data: response })
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(CommunityLeaderboard)
  app.use(i18n)
  app.mount(host)
  for (let i = 0; i < 5; i++) { await Promise.resolve(); await nextTick() }
  return host
}

const base = { category: 'MONTHLY_KWH', displayName: 'kWh', unit: 'kWh', lowerIsBetter: false, hasMonthEndReward: false, period: '2026-10' }

describe('CommunityLeaderboard - eigene Position', () => {
  it('markiert den eigenen Platz in den Top 10', async () => {
    const host = await mount({ ...base, entries: [entry(1, 'anna'), entry(2, 'me'), entry(3, 'kurt')], ownEntry: null })
    const own = host.querySelectorAll('[data-testid="leaderboard-own"]')
    expect(own).toHaveLength(1)
    expect(own[0].textContent).toContain('me')
    expect(own[0].textContent).toContain('Du')
  })

  it('hängt den eigenen Platz außerhalb der Top 10 unten an', async () => {
    const host = await mount({ ...base, entries: [entry(1, 'anna'), entry(2, 'kurt')], ownEntry: entry(14, 'me') })
    const own = host.querySelector('[data-testid="leaderboard-own"]')
    expect(own?.textContent).toContain('14')
    expect(own?.textContent).toContain('Du')
  })
})
