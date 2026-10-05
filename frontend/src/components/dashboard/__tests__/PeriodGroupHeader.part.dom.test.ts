// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'

vi.mock('vue-router', () => ({ useRoute: () => ({ path: '/logs', params: {}, query: {} }) }))

import PeriodGroupHeader from '../PeriodGroupHeader.vue'
import type { PeriodGroup } from '../../../utils/tripPeriods'

const group: PeriodGroup = {
  id: 'month:2026-09', level: 'month', periodKey: '2026-09', trips: [], charges: [], days: [],
  totals: {
    km: 279, tripCount: 72, chargeCount: 1, chargedKwh: 61.1, consumedKwh: 43.4,
    kwhPer100km: 17, costPer100km: 4.94, unmeasuredTrips: 0, kmIsOdometerEstimate: false,
  },
  bars: [{ dateKey: '2026-09-01', km: 30, charged: false }, { dateKey: '2026-09-02', km: 4, charged: true }],
}

let app: App | null = null
afterEach(() => {
  app?.unmount()
  app = null
  document.body.innerHTML = ''
})

async function mount(part?: 'title' | 'body') {
  const Host = defineComponent({ render: () => h(PeriodGroupHeader, { group, expanded: true, compact: true, part }) })
  app = createApp(Host)
  app.use(i18n).use(createPinia())
  app.mount(document.body.appendChild(document.createElement('div')))
  await nextTick()
}

const has = (testId: string) => document.body.querySelector(`[data-testid="${testId}"]`) !== null

describe('PeriodGroupHeader - Teile fuer den klebenden Kopf', () => {
  it('rendert ohne part alles: Titel, Chips und Tagesraster', async () => {
    await mount()
    expect(has('period-title')).toBe(true)
    expect(has('period-chips')).toBe(true)
    expect(has('period-bars')).toBe(true)
  })

  it('part="title" rendert nur die Titelzeile samt Chevron - das, was beim Scrollen kleben bleibt', async () => {
    await mount('title')
    expect(has('period-title')).toBe(true)
    expect(document.body.querySelector('svg')).not.toBeNull()
    expect(has('period-chips')).toBe(false)
    expect(has('period-bars')).toBe(false)
  })

  it('part="body" rendert Chips und Tagesraster ohne Titel', async () => {
    await mount('body')
    expect(has('period-title')).toBe(false)
    expect(has('period-chips')).toBe(true)
    expect(has('period-bars')).toBe(true)
    expect(document.body.textContent).not.toContain('September 2026')
  })
})
