// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryFeedItem from '../StoryFeedItem.vue'
import type { StoryFeedItem as Item } from '../../../composables/storyModel'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }

function mount(item: Item) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryFeedItem, { item, upsellTo: '/upgrade', carId: 'c1', carName: 'Tesla Model 3' })
  app.component('router-link', RouterLink)
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}

describe('StoryFeedItem mit Moment', () => {
  it('nennt Anlass und Satz des Moments und bietet einen Link als Handlung an', () => {
    const host = mount({
      id: 'moment', question: 1, relevance: 80, sentiment: 'neutral', params: {},
      moment: { kind: 'just_charged', key: 'k', at: Date.now(), lifetimeMs: 1, actionable: true, dismissible: false, logId: 'l', kwh: 20, socFrom: null, socTo: null, rangeKm: null, costEur: null },
    })
    const text = host.textContent!.replace(/ /g, ' ')
    expect(text).toContain('Gerade geladen. 20,0 kWh.')
    expect(host.querySelector('a')!.getAttribute('href')).toBe('/logs?car=c1&nachtragen=preis')
  })
})

describe('StoryFeedItem Strecke', () => {
  it('mehr unterwegs als im Vormonat, mit beiden Strecken', () => {
    const host = mount({ id: 'distance_trend', question: 1, relevance: 40, sentiment: 'neutral', params: { diff: 88, current: 600, previous: 512 } })
    expect(host.textContent!.replace(/\s+/g, ' ')).toMatch(/Mehr unterwegs als im Vormonat: 600\skm statt 512\skm\./)
  })

  it('weniger unterwegs, wenn der Monat vorbei ist', () => {
    const host = mount({ id: 'distance_trend', question: 1, relevance: 40, sentiment: 'neutral', params: { diff: -82, current: 430, previous: 512 } })
    expect(host.textContent!.replace(/\s+/g, ' ')).toMatch(/Weniger unterwegs als im Vormonat: 430\skm statt 512\skm\./)
  })
})
