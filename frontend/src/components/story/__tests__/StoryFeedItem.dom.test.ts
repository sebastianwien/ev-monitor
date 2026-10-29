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
  app = createApp(StoryFeedItem, { item, modelName: 'Model 3', monthName: 'Oktober', ...extra })
  app.component('router-link', RouterLink)
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}

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

  it('Temperatur-Effekt: Zahl in Amber, nie Rot', () => {
    const host = mount({ id: 'temperature_effect', question: 2, relevance: 40, sentiment: 'warn', params: { deltaPercent: 43.75, cold: 23, mild: 16 } })
    const n = host.querySelector('[data-testid="story-feed-number"]')!
    expect(n.textContent!.replace(/\s/g, ' ')).toBe('+44 %')
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

describe('StoryFeedItem Insights mit Vergleichsbasis und Balken', () => {
  const text = (host: Element) => host.textContent!.replace(/\s+/g, ' ').trim()
  const bars = (host: Element) => [...host.querySelectorAll('[data-testid="story-insight-bars"] > div')].map(x => [...x.querySelectorAll('span')].filter(s => s.textContent!.trim()).map(s => text(s)).join(' '))

  it('Herbst-Prognose: Satz mit Monat, Basis darunter, Balken Oktober gegen November mit "ca."', () => {
    const host = mount({ id: 'autumn_forecast', question: 1, relevance: 65, sentiment: 'neutral', params: { deltaEur: 19.6, currentEur: 59.2, nextEur: 78.8, nextMonth: 11, peerWinter: 0 } }, { sheets: ['range'] })
    expect(text(host)).toContain('Ab November rund 20 € mehr im Monat')
    expect(text(host)).toContain('Es wird kälter, dein Auto braucht mehr Strom.')
    expect(host.querySelector('[data-testid="story-feed-number"]')!.textContent!.replace(/\s/g, ' ')).toBe('+20 €')
    expect(bars(host)).toEqual(['Oktober 59 €', 'November ca. 79 €'])
    expect(host.querySelector('b')!.textContent!.replace(/\s/g, ' ')).toBe('20 €')
    expect(host.querySelector('button')).not.toBeNull()
  })

  it('Herbst-Prognose mit Winterwert anderer Fahrer nennt die Quelle', () => {
    const host = mount({ id: 'autumn_forecast', question: 1, relevance: 65, sentiment: 'neutral', params: { deltaEur: 19.6, currentEur: 59.2, nextEur: 78.8, nextMonth: 11, peerWinter: 1 } })
    expect(text(host)).toContain('Winterwert von anderen Model 3-Fahrern.')
  })

  it('Preisvergleich in Euro: weniger als andere in Grün, Balken Du gegen Andere', () => {
    const host = mount({ id: 'peer_cost_eur', question: 3, relevance: 60, sentiment: 'good', params: { diffEur: -35.4, mineEur: 59.2, theirsEur: 94.6, cars: 40 } }, { sheets: ['peer'] })
    expect(text(host)).toContain('35 € weniger als andere Model 3-Fahrer')
    expect(text(host)).toContain('Für dieselbe Menge Strom im Oktober.')
    const n = host.querySelector('[data-testid="story-feed-number"]')!
    expect(n.textContent!.replace(/\s/g, ' ')).toBe('-35 €')
    expect(n.className).toContain('text-green-700')
    expect(bars(host)).toEqual(['Du 59 €', 'Andere ca. 95 €'])
  })

  it('Preisvergleich: mehr als andere bleibt schwarz, kein Amber', () => {
    const host = mount({ id: 'peer_cost_eur', question: 3, relevance: 60, sentiment: 'neutral', params: { diffEur: 12.2, mineEur: 59.2, theirsEur: 47, cars: 40 } })
    expect(text(host)).toContain('12 € mehr als andere Model 3-Fahrer')
    const n = host.querySelector('[data-testid="story-feed-number"]')!
    expect(n.textContent!.replace(/\s/g, ' ')).toBe('+12 €')
    expect(n.className).not.toContain('amber')
  })

  it('Heimlade-Ersparnis: Titel, Basis "12 Monate", Betrag rechts, keine Balken', () => {
    const host = mount({ id: 'home_savings', question: 1, relevance: 70, sentiment: 'good', params: { eur: 747.3, monthsRemaining: 14 } })
    expect(text(host)).toContain('Gespart, weil du meist zu Hause lädst')
    expect(text(host)).toContain('In den letzten 12 Monaten, gegenüber öffentlichem Laden. Die Wallbox ist in 14 Monaten bezahlt.')
    expect(host.querySelector('[data-testid="story-feed-number"]')!.textContent!.replace(/\s/g, ' ')).toBe('747 €')
    expect(host.querySelector('[data-testid="story-insight-bars"]')).toBeNull()
  })
})

describe('StoryFeedItem Ladungen ohne Preis', () => {
  it('eine Ladung: Einzahl im Satz, Tippen öffnet sie bei den Kosten', () => {
    const charged: unknown[][] = []
    const host = mount({ id: 'data_quality', question: 1, relevance: 35, sentiment: 'warn', params: { estimated: 0, total: 4, priceless: 1, pricelessOpen: 1 }, logId: 'l1' },
      { sheets: ['cost'], onCharge: (...a: unknown[]) => charged.push(a) })
    expect(host.textContent).toContain('Eine Ladung ohne Preis')
    host.querySelector('button')!.click()
    expect(charged).toEqual([['l1', 'cost']])
  })

  it('mehrere: Mehrzahl, Tippen öffnet die Liste', () => {
    let priceless = 0
    const host = mount({ id: 'data_quality', question: 1, relevance: 80, sentiment: 'bad', params: { estimated: 0, total: 9, priceless: 3, pricelessOpen: 3 }, logId: null },
      { sheets: ['cost'], onPriceless: () => { priceless++ } })
    expect(host.textContent).toContain('3 Ladungen ohne Preis')
    host.querySelector('button')!.click()
    expect(priceless).toBe(1)
  })
})

