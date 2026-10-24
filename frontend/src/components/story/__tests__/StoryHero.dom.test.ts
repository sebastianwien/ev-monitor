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
  costPer100km: 12.19,
  petrolEur: 51.2,
  petrolPricePerLitre: 1.74,
  petrolPriceLive: true,
  days: [],
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
  it('Monat in km und Euro groß, Benzin-Satz grün, Euro je 100 km klein', () => {
    const host = mount()
    expect(text(host.querySelector('[data-testid="story-hero-value"]'))).toBe('Oktober: 274 km für 33 €.')
    const petrol = host.querySelector('[data-testid="story-hero-petrol"]')
    expect(text(petrol)).toBe('Mit Benzin wären es 51 €.')
    expect(petrol?.className).toContain('text-story-accent')
    expect(text(host.querySelector('[data-testid="story-hero-meta"]'))).toContain('12,19 €/100km')
  })

  it('ohne km: Euro für Ladungen, ohne Benzin-Satz und ohne Euro je 100 km', () => {
    const host = mount({ distanceKm: 0, costPer100km: null as never, petrolEur: null as never })
    expect(text(host.querySelector('[data-testid="story-hero-value"]'))).toBe('Oktober: 33 € für 6 Ladungen.')
    expect(host.querySelector('[data-testid="story-hero-petrol"]')).toBeNull()
    expect(text(host.querySelector('[data-testid="story-hero-meta"]'))).not.toContain('/100km')
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
