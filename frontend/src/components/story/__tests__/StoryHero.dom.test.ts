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
  layers: null,
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
  it('Monat in km und Euro als Satz, darunter Euro je 100 km und Benzin in einer grauen Zeile', () => {
    const host = mount()
    expect(text(host.querySelector('[data-testid="story-hero-value"]'))).toBe('Oktober: 274 km für 33 €.')
    const meta = host.querySelector('[data-testid="story-hero-meta"]')
    expect(text(meta)).toBe('12,19 € je 100 km · Ein Benziner hätte 51 € gekostet.')
    expect(meta?.className).toContain('text-gray-500')
    expect(host.querySelector('[data-testid="story-hero-petrol"]')).toBeNull()
  })

  it('Euro je 100 km aus dem Satz gerechnet: 17,73 € für 274,1 km sind 6,47 €', () => {
    const host = mount({ distanceKm: 274.1, energyCostEur: 17.73, petrolEur: 43.8 })
    expect(text(host.querySelector('[data-testid="story-hero-value"]'))).toBe('Oktober: 274 km für 18 €.')
    expect(text(host.querySelector('[data-testid="story-hero-meta"]'))).toContain('6,47 € je 100 km')
  })

  it('ohne km: Euro für Ladungen, ohne Benzin und ohne Euro je 100 km', () => {
    const host = mount({ distanceKm: 0, petrolEur: null as never })
    expect(text(host.querySelector('[data-testid="story-hero-value"]'))).toBe('Oktober: 33 € für 6 Ladungen.')
    expect(host.querySelector('[data-testid="story-hero-meta"]')).toBeNull()
  })

  it('ohne Preise: km und Ladungen, keine 0,00 € je 100 km', () => {
    const host = mount({ distanceKm: 6.8, energyCostEur: 0, charges: 2 })
    expect(text(host.querySelector('[data-testid="story-hero-value"]'))).toBe('Oktober: 7 km, 2 Ladungen.')
    expect(host.querySelector('[data-testid="story-hero-meta"]')).toBeNull()
    const raw = host.querySelector('[data-testid="story-hero-value"]')!.textContent!
    expect(raw).toContain('7\u00a0km')
    expect(raw).toContain('2\u00a0Ladungen')
  })

  it('kein Benzin-Satz, wenn Benzin nicht teurer gewesen wäre', () => {
    const host = mount({ petrolEur: 30 })
    expect(text(host.querySelector('[data-testid="story-hero-meta"]'))).toBe('12,19 € je 100 km')
  })

  it('Monatsname klein geschrieben (nb, sv) beginnt den Satz groß', () => {
    const host = mount({ monthLabel: 'oktober' })
    expect(text(host.querySelector('[data-testid="story-hero-value"]'))).toMatch(/^Oktober:/)
  })

  it('bricht nur nach dem Monat um: der Rest steht zusammen', () => {
    const host = mount()
    const rest = host.querySelector('[data-testid="story-hero-value"] .whitespace-nowrap')
    expect(text(rest)).toBe('274 km für 33 €.')
  })

  it('Teilen-Knopf rechts meldet den Satz', () => {
    const host = mount()
    const share = host.querySelector('[data-testid="story-hero-share"]') as HTMLButtonElement
    expect(share.getAttribute('aria-label')).toBe('Teilen')
  })

  it('mit Fixkosten ist der Satz ein Knopf, der die Kostenschichten aufklappt', () => {
    const host = mount({ layers: [{ key: 'energy', eur: 33.4 }] as never })
    const value = host.querySelector('[data-testid="story-hero-value"]')
    expect(value?.tagName).toBe('BUTTON')
    expect(value?.getAttribute('aria-expanded')).toBe('false')
  })
})
