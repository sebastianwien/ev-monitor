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

function mount(item: Item, extra: Record<string, unknown> = {}) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryFeedItem, { item, upsellTo: '/upgrade', carId: 'c1', carName: 'Tesla Model 3', ...extra })
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

describe('StoryFeedItem Zahl rechts', () => {
  it('Kostentrend: Satz links, Monatsbetrag rechts in Grün, keine Fragen-Zeile, keine Grafik', () => {
    const host = mount({ id: 'cost_trend', question: 1, relevance: 40, sentiment: 'good', params: { diff: -3, current: 15.2, previous: 18.1 } })
    const n = host.querySelector('[data-testid="story-feed-number"]')!
    expect(n.textContent!.replace(/\s/g, ' ')).toBe('15 €')
    expect(n.className).toContain('text-green-700')
    expect(host.textContent).not.toContain('Frage 1')
    expect(host.querySelector('svg')).toBeNull()
  })

  it('Verbrauch höher: Zahl in Amber, nie Rot', () => {
    const host = mount({ id: 'consumption_trend', question: 2, relevance: 40, sentiment: 'bad', params: { diff: 8.7, current: 26.9, previous: 18.2 } })
    const n = host.querySelector('[data-testid="story-feed-number"]')!
    expect(n.textContent!.replace(/\s/g, ' ')).toBe('26,9')
    expect(n.className).toContain('text-amber-700')
    expect(n.className).not.toContain('rose')
  })

  it('teuerste Ladung und höhere Kosten: Zahl schwarz, Amber nur beim Verbrauch', () => {
    const host = mount({ id: 'most_expensive', question: 1, relevance: 40, sentiment: 'warn', params: { eur: 27.39, avgEur: 12.3 } })
    expect(host.querySelector('[data-testid="story-feed-number"]')!.className).not.toContain('amber')
  })

  it('die Zahl im Satz ist fett', () => {
    const host = mount({ id: 'cost_trend', question: 1, relevance: 40, sentiment: 'good', params: { diff: -3, current: 15.2, previous: 18.1 } })
    expect(host.querySelector('b')!.textContent!.replace(/\s/g, ' ')).toBe('3 €')
  })

  it('Rang: #Platz rechts, die Zeile führt zur Rangliste und trägt einen Pfeil', () => {
    const host = mount({ id: 'community_rank', question: 3, relevance: 40, sentiment: 'neutral', params: { rank: 16, value: 1 }, label: 'kwh' } as Item)
    expect(host.querySelector('[data-testid="story-feed-number"]')!.textContent).toBe('#16')
    expect(host.querySelector('a')!.getAttribute('href')).toBe('/leaderboard')
    expect(host.querySelector('[data-testid="story-feed-chevron"]')).not.toBeNull()
  })

  it('Zeile ohne Ziel: kein Pfeil, kein Knopf', () => {
    const host = mount({ id: 'most_expensive', question: 1, relevance: 40, sentiment: 'neutral', params: { eur: 27.39, avgEur: 12.3 } }, { sheets: [] })
    expect(host.querySelector('[data-testid="story-feed-chevron"]')).toBeNull()
    expect(host.querySelector('button')).toBeNull()
  })

  it('Zeile mit Sheet dahinter ist ein Knopf und meldet die Kachel', () => {
    const opened: string[] = []
    const host = mount({ id: 'most_expensive', question: 1, relevance: 40, sentiment: 'neutral', params: { eur: 27.39, avgEur: 12.3 } }, { sheets: ['cost'], onOpen: (id: string) => opened.push(id) })
    expect(host.querySelector('[data-testid="story-feed-chevron"]')).not.toBeNull()
    host.querySelector('button')!.click()
    expect(opened).toEqual(['cost'])
    app!.unmount(); app = null
    const plain = mount({ id: 'most_expensive', question: 1, relevance: 40, sentiment: 'neutral', params: { eur: 27.39, avgEur: 12.3 } }, { sheets: ['wltp'] })
    expect(plain.querySelector('button')).toBeNull()
  })
})

describe('StoryFeedItem Abgleich Fahren und Laden', () => {
  it('nennt beide Verbräuche, die Zeile führt zur Fahrten-Kachel', () => {
    const opened: string[] = []
    const host = mount({ id: 'drive_vs_charge', question: 2, relevance: 60, sentiment: 'neutral', params: { drive: 18.2, charged: 26.9 } }, { sheets: ['trips'], onOpen: (id: string) => opened.push(id) })
    expect(host.textContent!.replace(/\s+/g, ' ')).toMatch(/Beim Fahren brauchst du 18,2\skWh\/100km, aus deinen Ladungen werden 26,9\skWh\/100km\. Der Unterschied entsteht im Stand\./)
    host.querySelector('button')!.click()
    expect(opened).toEqual(['trips'])
  })
})
