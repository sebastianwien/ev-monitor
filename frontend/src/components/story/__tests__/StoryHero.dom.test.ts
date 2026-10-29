// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useCountryStore } from '../../../stores/country'
import { i18n } from '../../../i18n'
import StoryHero from '../StoryHero.vue'

let app: App | null = null
/** Land zurück auf den Standard, damit Folgetests nicht in Meilen rechnen. */
function afterReset() { try { localStorage.removeItem('ev-country') } catch { /* egal */ } }
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; afterReset() })

const base = {
  monthLabel: 'Oktober',
  distanceKm: 274,
  energyCostEur: 33.4,
  charges: 6,
  petrolEur: 51.2,
  layers: null,
}

function mount(props: Partial<typeof base> & Record<string, unknown> = {}, country?: 'GB') {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const pinia = createPinia()
  setActivePinia(pinia)
  if (country) useCountryStore().setCountry(country)
  app = createApp(StoryHero, { ...base, ...props })
  app.use(pinia).use(i18n)
  app.mount(host)
  return host
}

const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim() ?? ''

describe('StoryHero', () => {
  it('Monat in km und Euro als Satz, darunter Euro je 100 km und Benzin in einer grauen, halbfetten Zeile', () => {
    const host = mount()
    expect(text(host.querySelector('[data-testid="story-hero-value"]'))).toBe('Oktober: 274 km für 33 €.')
    const meta = host.querySelector('[data-testid="story-hero-meta"]')
    expect(text(meta)).toBe('12,19 € je 100 km · Ein Benziner hätte 51 € gekostet.')
    expect(meta?.className).toContain('text-gray-600')
    expect(meta?.className).toContain('font-semibold')
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

  it('kein Teilen-Knopf: rechts neben dem Monat steht der Slot "nav", außerhalb der Überschrift', () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp({ render: () => h(StoryHero, base, { nav: () => h('span', { 'data-testid': 'nav-probe' }, '‹ ›') }) })
    app.use(createPinia()).use(i18n)
    app.mount(host)
    expect(host.querySelector('[data-testid="story-hero-share"]')).toBeNull()
    const probe = host.querySelector('[data-testid="nav-probe"]')!
    expect(probe.closest('h1')).toBeNull()
    // Schwebt rechts vor der Überschrift: nur die Monatszeile weicht aus, der Satz darunter hat die volle Breite.
    expect(probe.parentElement!.className).toContain('float-right')
    expect(probe.parentElement!.nextElementSibling?.tagName).toBe('H1')
  })

  it('ohne Slot kein leerer Platzhalter', () => {
    const host = mount()
    expect(host.querySelector('.float-right')).toBeNull()
  })

  it('Meilen: Kosten je 100 mi, passend zum Satz', () => {
    const host = mount({}, 'GB')
    afterReset()
    const meta = text(host.querySelector('[data-testid="story-hero-meta"]'))
    expect(meta).toMatch(/per 100 mi|je 100 mi/)
    expect(meta).not.toContain('100 km')
  })

  const layers = [
    { kind: 'energy', eur: 33.4 }, { kind: 'fixed', eur: 162.9 }, { kind: 'net', eur: 196.3 },
  ]

  it('mit Fixkosten: Knopf "Alle Kosten" mit der Summe der Schichten, der Satz bleibt Text', async () => {
    const host = mount({ layers: layers as never })
    expect(host.querySelector('[data-testid="story-hero-value"]')?.tagName).toBe('SPAN')
    expect(host.querySelector('h1 button')).toBeNull()
    const toggle = host.querySelector('[data-testid="story-hero-layers"]') as HTMLButtonElement
    expect(toggle.tagName).toBe('BUTTON')
    expect(text(toggle)).toBe('Alle Kosten 196 €')
    expect(toggle.className).toContain('min-h-11')
    expect(toggle.className).not.toMatch(/green|accent/)
    expect(text(host.querySelector('[data-testid="story-hero-meta"]'))).not.toContain('Tippen')
  })

  it('der Knopf klappt die Kostenschichten auf und meldet das, aria-expanded wechselt', async () => {
    const opened: boolean[] = []
    const host = mount({ layers: layers as never, onLayers: (o: boolean) => opened.push(o) })
    const toggle = host.querySelector('[data-testid="story-hero-layers"]') as HTMLButtonElement
    const panel = document.getElementById(toggle.getAttribute('aria-controls')!)
    expect(panel?.querySelector('[data-testid="story-cost-layers"]')).not.toBeNull()
    expect(toggle.getAttribute('aria-expanded')).toBe('false')
    toggle.click()
    await nextTick()
    expect(toggle.getAttribute('aria-expanded')).toBe('true')
    expect(toggle.querySelector('svg')?.getAttribute('class')).toContain('rotate-180')
    toggle.click()
    await nextTick()
    expect(toggle.getAttribute('aria-expanded')).toBe('false')
    expect(opened).toEqual([true, false])
  })

  it('die Schichten stehen in Euro des Monats, Netto gleich der Summe auf dem Knopf', () => {
    const host = mount({ layers: layers as never })
    const rows = [...host.querySelectorAll('[data-testid="story-cost-layers"] li')].map(li => [...li.querySelectorAll('span')].map(text).join(' ') || text(li))
    expect(rows.slice(0, 3)).toEqual(['Strom 33,40 €', 'Fixkosten 162,90 €', 'Netto 196,30 €'])
    expect(rows.join(' ')).not.toContain('100')
  })

  it('ohne Fixkosten und Einnahmen kein Knopf', () => {
    const host = mount()
    expect(host.querySelector('[data-testid="story-hero-layers"]')).toBeNull()
    expect(host.querySelector('[data-testid="story-cost-layers"]')).toBeNull()
  })

  it('Knopf auch ohne graue Zeile, etwa ohne Strecke', () => {
    const host = mount({ distanceKm: 0, petrolEur: null as never, layers: layers as never })
    expect(host.querySelector('[data-testid="story-hero-meta"]')).toBeNull()
    expect(text(host.querySelector('[data-testid="story-hero-layers"]'))).toBe('Alle Kosten 196 €')
  })
})
