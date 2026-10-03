// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import StoryLatestLine from '../StoryLatestLine.vue'
import type { StoryLatest } from '../../../composables/storyInsights'

const messages = {
  de: { story: {
    latest_prefix: 'Zuletzt: ', latest_just_now: 'gerade eben', latest_charge: 'Ladung {when}, {kwh}',
    latest_charge_cost: '{base} für {cost}', latest_trip: 'Fahrt {when}, {km}',
  } },
}
const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }

function mount(latest: StoryLatest, now: number): { el: HTMLElement; app: App } {
  const el = document.createElement('div')
  document.body.appendChild(el)
  const app = createApp(StoryLatestLine, { latest, now })
  app.use(createPinia())
  app.use(createI18n({ legacy: false, locale: 'de', messages }))
  app.component('router-link', RouterLink)
  app.mount(el)
  return { el, app }
}

describe('StoryLatestLine', () => {
  const now = Date.parse('2026-10-02T12:00:00Z')

  it('nennt Ladung und Fahrt relativ, mit kWh, Preis und km, beide verlinkt', () => {
    const { el, app } = mount({
      charge: { id: 'l', at: '2026-10-01T18:00:00Z', kwh: 31.9, costEur: 12.4 },
      trip: { id: 't', at: '2026-10-02T09:00:00Z', km: 23.4 },
    }, now)
    const text = el.textContent!.replace(/\s+/g, ' ').trim()
    expect(text).toContain('Zuletzt:')
    expect(text).toContain('Ladung vor 18 Stunden, 31,9 kWh für 12,40 €')
    expect(text).toContain('Fahrt vor 3 Stunden, 23 km')
    expect([...el.querySelectorAll('a')].map(a => a.getAttribute('href'))).toEqual(['/logs', '/logs'])
    app.unmount()
  })

  it('laesst Preis und Fahrt weg, wenn sie fehlen', () => {
    const { el, app } = mount({ charge: { id: 'l', at: '2026-10-02T11:59:30Z', kwh: 10, costEur: null }, trip: null }, now)
    expect(el.textContent!.replace(/\s+/g, ' ').trim()).toBe('Zuletzt: Ladung gerade eben, 10,0 kWh')
    expect(el.querySelectorAll('a')).toHaveLength(1)
    app.unmount()
  })
})
