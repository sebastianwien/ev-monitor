// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryHero from '../StoryHero.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const base = {
  monthLabel: 'Oktober',
  distanceKm: 274,
  energyCostEur: 33.4,
  charges: 6,
  petrolEur: 51.2,
  petrolPricePerLitre: 1.74,
  petrolPriceLive: true,
  days: [] as Array<{ day: number; hasData: boolean; inLongestGap: boolean; future: boolean }>,
  layers: null,
  quality: null,
}

function mount(props: Partial<typeof base> = {}) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryHero, { ...base, ...props })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}

const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim() ?? ''

describe('StoryHero', () => {
  it('Tagesleiste zählt nur Tage bis heute, Tage danach sind keine Lücke', () => {
    // 14.10.: Daten an 5 Tagen, 13. und 14. leer, ab 15. Zukunft
    const days = Array.from({ length: 31 }, (_, i) => {
      const day = i + 1
      return { day, hasData: [1, 3, 6, 9, 12].includes(day), inLongestGap: false, future: day > 14 }
    })
    const host = mount({ days })
    expect(text(host)).toContain('14 Tage, 5 mit Daten.')
    expect(text(host)).not.toContain('nichts erfasst')
  })

  it('Monat in km und Euro groß, Benzin-Satz grün, Euro je 100 km klein', () => {
    const host = mount()
    expect(text(host.querySelector('[data-testid="story-hero-value"]'))).toBe('Oktober: 274 km für 33 €.')
    const petrol = host.querySelector('[data-testid="story-hero-petrol"]')
    expect(text(petrol)).toBe('Mit Benzin wären es 51 €.')
    expect(petrol?.className).toContain('text-story-accent')
    expect(text(host.querySelector('[data-testid="story-hero-meta"]'))).toContain('12,19 €/100km')
  })

  it('Euro je 100 km aus dem Satz gerechnet: 17,73 € für 274,1 km sind 6,47 €', () => {
    const host = mount({ distanceKm: 274.1, energyCostEur: 17.73, petrolEur: 43.8 })
    expect(text(host.querySelector('[data-testid="story-hero-value"]'))).toBe('Oktober: 274 km für 18 €.')
    expect(text(host.querySelector('[data-testid="story-hero-meta"]'))).toContain('6,47 €/100km')
  })

  it('ohne km: Euro für Ladungen, ohne Benzin-Satz und ohne Euro je 100 km', () => {
    const host = mount({ distanceKm: 0, petrolEur: null as never })
    expect(text(host.querySelector('[data-testid="story-hero-value"]'))).toBe('Oktober: 33 € für 6 Ladungen.')
    expect(host.querySelector('[data-testid="story-hero-petrol"]')).toBeNull()
    expect(text(host.querySelector('[data-testid="story-hero-meta"]'))).not.toContain('/100km')
  })

  it('ohne Preise: km und Ladungen, keine 0,00 € je 100 km', () => {
    const host = mount({ distanceKm: 6.8, energyCostEur: 0, charges: 2 })
    expect(text(host.querySelector('[data-testid="story-hero-value"]'))).toBe('Oktober: 7 km, 2 Ladungen.')
    expect(text(host.querySelector('[data-testid="story-hero-meta"]'))).not.toContain('/100km')
    // Der große Satz bricht nie zwischen Zahl und Einheit oder Zahl und Wort um
    const raw = host.querySelector('[data-testid="story-hero-value"]')!.textContent!
    expect(raw).toContain('7 km')
    expect(raw).toContain('2 Ladungen')
  })

  it('kein Benzin-Satz, wenn Benzin nicht teurer gewesen wäre', () => {
    const host = mount({ petrolEur: 30 })
    expect(host.querySelector('[data-testid="story-hero-petrol"]')).toBeNull()
  })

  it('Monatsname klein geschrieben (nb, sv) beginnt den Satz groß', () => {
    const host = mount({ monthLabel: 'oktober' })
    expect(text(host.querySelector('[data-testid="story-hero-value"]'))).toMatch(/^Oktober:/)
  })

  it('mit Fixkosten ist der Satz ein Knopf, der die Kostenschichten aufklappt', () => {
    const host = mount({ layers: [{ key: 'energy', eur: 33.4 }] as never })
    const value = host.querySelector('[data-testid="story-hero-value"]')
    expect(value?.tagName).toBe('BUTTON')
    expect(value?.getAttribute('aria-expanded')).toBe('false')
  })
})
