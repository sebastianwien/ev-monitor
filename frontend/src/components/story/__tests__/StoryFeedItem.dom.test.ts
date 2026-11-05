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

describe('StoryFeedItem Zahl im Satz', () => {
  it('jede Zahl steht einmal, fett im Satz, keine zweite Zahl rechts', () => {
    const host = mount({ id: 'cost_trend', question: 1, relevance: 40, sentiment: 'good', params: { diff: -3, current: 15.2, previous: 18.1 } })
    expect(host.querySelector('[data-testid="story-feed-number"]')).toBeNull()
    const b = host.querySelector('b')!
    expect(b.textContent!.replace(/\s/g, ' ')).toBe('3 €')
    expect(b.className).toContain('text-green-700')
    expect(host.textContent).not.toContain('Frage 1')
  })

  it('Temperatur-Effekt: Zahl in Amber, nie Rot', () => {
    const host = mount({ id: 'temperature_effect', question: 2, relevance: 40, sentiment: 'warn', params: { deltaPercent: 43.75, cold: 23, mild: 16 } })
    const b = host.querySelector('b')!
    expect(b.textContent!.replace(/\s/g, ' ')).toBe('44')
    expect(b.className).toContain('text-amber-700')
    expect(b.className).not.toContain('rose')
  })

  it('höhere Kosten: Zahl schwarz, Amber nur beim Verbrauch', () => {
    const host = mount({ id: 'cost_trend', question: 1, relevance: 40, sentiment: 'warn', params: { diff: 102, current: 120, previous: 18 } })
    expect(host.querySelector('b')!.className).not.toContain('amber')
  })

  it('Zeile ohne Ziel: kein Pfeil, kein Knopf', () => {
    const host = mount({ id: 'cost_trend', question: 1, relevance: 40, sentiment: 'neutral', params: { diff: 5, current: 20, previous: 15 } }, { sheets: [] })
    expect(host.querySelector('[data-testid="story-feed-chevron"]')).toBeNull()
    expect(host.querySelector('button')).toBeNull()
  })

  it('Zeile mit Sheet dahinter ist ein Knopf und meldet die Kachel', () => {
    const opened: string[] = []
    const host = mount({ id: 'cost_trend', question: 1, relevance: 40, sentiment: 'neutral', params: { diff: 5, current: 20, previous: 15 } }, { sheets: ['cost'], onOpen: (id: string) => opened.push(id) })
    expect(host.querySelector('[data-testid="story-feed-chevron"]')).not.toBeNull()
    host.querySelector('button')!.click()
    expect(opened).toEqual(['cost'])
    app!.unmount(); app = null
    const plain = mount({ id: 'cost_trend', question: 1, relevance: 40, sentiment: 'neutral', params: { diff: 5, current: 20, previous: 15 } }, { sheets: ['wltp'] })
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
    expect(host.querySelector('b')!.className).toContain('text-green-700')
    expect(bars(host)).toEqual(['Du 59 €', 'Andere ca. 95 €'])
  })

  it('Preisvergleich: mehr als andere bleibt schwarz, kein Amber', () => {
    const host = mount({ id: 'peer_cost_eur', question: 3, relevance: 60, sentiment: 'neutral', params: { diffEur: 12.2, mineEur: 59.2, theirsEur: 47, cars: 40 } })
    expect(text(host)).toContain('12 € mehr als andere Model 3-Fahrer')
    expect(host.querySelector('b')!.className).not.toContain('amber')
  })

  it('Kostentrend: Satz bleibt, darunter Balken je Monat mit Strecke und Betrag, der Monat hervorgehoben', () => {
    const host = mount({ id: 'cost_trend', question: 1, relevance: 60, sentiment: 'warn', params: { diff: 75, current: 96.4, previous: 21.6 },
      months: [{ month: 9, eur: 21.6, km: 310 }, { month: 10, eur: 96.4, km: 940 }] })
    expect(text(host)).toContain('Du hast 75 € mehr ausgegeben als im Vormonat.')
    expect(bars(host)).toEqual(['September 310 km 22 €', 'Oktober 940 km 96 €'])
    const fills = [...host.querySelectorAll('[data-testid="story-insight-bars"] i')]
    expect(fills[1].className).toContain('bg-sky-500')
    expect(fills[0].className).not.toContain('bg-sky-500')
  })

  it('Kostentrend mit nur einem Monat: keine Balken, ein Balken allein vergleicht nichts', () => {
    const host = mount({ id: 'cost_trend', question: 1, relevance: 60, sentiment: 'warn', params: { diff: 75, current: 96.4, previous: 21.6 },
      months: [{ month: 10, eur: 96.4, km: 940 }] })
    expect(host.querySelector('[data-testid="story-insight-bars"]')).toBeNull()
  })

  const benchmark = { cpoName: 'Stadtwerke A', chargingType: 'DC' as const, cards: [] }
  const cpo = (card: Record<string, unknown>) => ({ id: 'cpo_price' as const, question: 1 as const, relevance: 90, sentiment: 'neutral' as const, params: { extraEur: 9.7 },
    operator: { cpoName: 'Stadtwerke A', chargingType: 'DC' as const, pricePerKwh: 0.5, extraEur: 9.7, benchmark,
      card: { name: 'Karte X', variant: null, pricePerKwh: 0.33, monthlyFeeEur: 0, verifiedOn: null, direct: false, ...card } } })

  it('teurer Betreiber: Ersparnis fett, dein Preis, Tarif mit Grundgebühr; Tipp öffnet das Preis-Sheet', () => {
    const opened: unknown[] = []
    const host = mount(cpo({ variant: 'Basis', monthlyFeeEur: 4.99 }), { onPrices: (b: unknown, p: unknown) => opened.push([b, p]) })
    expect(text(host)).toContain('Bei Stadtwerke A (Schnellladen) hättest du 9 € sparen können, du zahlst dort 50 ct/kWh.')
    expect(host.querySelector('b')!.textContent!.replace(/\s/g, ' ')).toBe('9 €')
    expect(text(host)).toContain('Mit Karte X Basis kostet es dort 33 ct/kWh, dazu 4,99 € Grundgebühr im Monat.')
    expect(text(host)).not.toContain('Fahrer')
    host.querySelector('button')!.click()
    expect(opened).toEqual([[benchmark, 0.5]])
  })

  it('der Betreiber selbst heißt "direkt", ohne Grundgebühr ohne Zusatz', () => {
    const host = mount(cpo({ name: 'Stadtwerke A', direct: true }))
    expect(text(host)).toContain('Mit Stadtwerke A direkt kostet es dort 33 ct/kWh.')
  })

  it('mehr Standverlust: sachlicher Satz mit beiden Tageswerten, mögliche Ursachen darunter, kein Amber', () => {
    const host = mount({ id: 'standby_change', question: 2, relevance: 70, sentiment: 'neutral', params: { perDay: 0.9, usualPerDay: 0.4 } })
    expect(text(host)).toContain('Dein Auto hat im Stand mehr Strom verbraucht als sonst: 0,9 kWh statt 0,4 kWh am Tag.')
    expect(text(host)).toContain('Oft steckt Kamera-Überwachung, Vorklimatisieren oder eine App dahinter, die das Auto weckt.')
    expect(host.querySelector('b')!.className).not.toContain('amber')
  })

  it('Heimlade-Ersparnis: Betrag vorn im Satz, Basis "12 Monate", keine Balken', () => {
    const host = mount({ id: 'home_savings', question: 1, relevance: 100, sentiment: 'good', params: { eur: 747.3, monthsRemaining: 14 } })
    expect(text(host)).toMatch(/^747\s€ gespart, weil du meist zu Hause lädst/)
    expect(text(host)).toContain('In den letzten 12 Monaten, gegenüber öffentlichem Laden. Die Wallbox ist in 14 Monaten bezahlt.')
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

