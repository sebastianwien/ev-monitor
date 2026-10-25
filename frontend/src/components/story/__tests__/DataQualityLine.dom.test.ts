// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import DataQualityLine from '../DataQualityLine.vue'
import type { DayCoverage } from '../../../composables/storyModel'
import type { DataQuality } from '../../../composables/storyInsights'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }

/** Monat mit 31 Tagen: Daten an `withData`, Lücke `gap` (inklusive), Zukunft ab `today + 1`. */
function days(withData: number[], gap: [number, number] | null, today = 31, tripDays: number[] = []): DayCoverage[] {
  return Array.from({ length: 31 }, (_, i) => {
    const day = i + 1
    const hasData = withData.includes(day) || tripDays.includes(day)
    const charge = withData.includes(day)
    const trip = tripDays.includes(day)
    return { day, hasData, inLongestGap: !!gap && day >= gap[0] && day <= gap[1], future: day > today, charges: charge ? 1 : 0, kwh: charge ? 20 : 0, trips: trip ? 2 : 0, tripKm: trip ? 42 : 0 }
  })
}
const quality = (over: Partial<DataQuality> = {}): DataQuality =>
  ({ estimated: 0, total: 12, estimatedShare: 0, priceless: 0, pricelessOpen: 0, severity: 'ok', ...over })

function mount(d: DayCoverage[], q: DataQuality | null, onOpenDay?: (day: number) => void) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(DataQualityLine, { days: d, quality: q, onOpenDay })
  app.component('router-link', RouterLink)
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}
/** Nur der Satz unter der Leiste, ohne Achse. */
const text = (el: Element | null) => el?.querySelector('p')?.textContent?.replace(/\s+/g, ' ').trim() ?? ''

describe('DataQualityLine', () => {
  it('Tagesleiste: ein Feld je Tag, Daten grün, Amber nur für die genannte Lücke, sonst grau', () => {
    const host = mount(days([1, 15], [2, 14], 25), quality())
    const cells = host.querySelectorAll('[data-role="day"]')
    expect(cells).toHaveLength(31)
    expect(cells[0].className).toContain('bg-green-700')
    expect(cells[1].className).toContain('bg-amber-400')
    expect(cells[13].className).toContain('bg-amber-400')
    expect(cells[17].className).toContain('bg-gray-200')
    expect(cells[27].className).toContain('bg-gray-100')
  })

  it('kurze Lücke im laufenden Monat: kein Amber in der Leiste', () => {
    const host = mount(days([1, 9], [2, 8], 14), quality())
    expect(host.querySelectorAll('[data-role="day"].bg-amber-400')).toHaveLength(0)
  })

  it('ein Satz: Tage bis heute, Tage mit Daten, gemessene Ladungen', () => {
    const host = mount(days([1, 3], null, 14), quality({ total: 2 }))
    expect(text(host)).toBe('14 Tage, 2 mit Daten. 2 von 2 Ladungen gemessen.')
  })

  it('Lücke im laufenden Monat nur, wenn sie 7 Tage überschreitet', () => {
    const short = mount(days([1, 9], [2, 8], 14), quality())
    expect(text(short)).not.toContain('nichts erfasst')
    app!.unmount(); app = null
    const long = mount(days([1, 10], [2, 9], 14), quality())
    expect(text(long)).toContain('Vom 2. bis 9. nichts erfasst.')
  })

  it('im abgeschlossenen Monat zählt jede Lücke ab 3 Tagen', () => {
    const host = mount(days([1, 5, 20], [6, 19]), quality())
    expect(text(host)).toContain('Vom 6. bis 19. nichts erfasst.')
    app!.unmount(); app = null
    const tiny = mount(days([1, 3, 5, 7], [1, 2]), quality())
    expect(text(tiny)).not.toContain('nichts erfasst')
  })

  it('Ladungen ohne Preis mit Link zum Nachtragen, geschätzte nur als Zahl', () => {
    const host = mount(days([1], null), quality({ total: 5, estimated: 1, priceless: 2, pricelessOpen: 1, severity: 'warn' }))
    expect(text(host)).toContain('4 von 5 Ladungen gemessen, 1 geschätzt, 2 ohne Preis nachtragen')
    expect(host.querySelector('a')?.getAttribute('href')).toBe('/logs')
  })

  it('Achse: Tage 1, 8, 15, 22 und 29 unter der Leiste', () => {
    const host = mount(days([1], null), quality())
    expect([...host.querySelectorAll('[data-role="tick"]')].map(x => x.textContent)).toEqual(['1.', '8.', '15.', '22.', '29.'])
  })

  it('Tagesanzeige per Tastatur: Pfeile wandern, Satz nennt Ladungen und kWh', async () => {
    const host = mount(days([1, 15], [2, 14], 25), quality())
    const bar = host.querySelector('[data-testid="story-days"]') as HTMLElement
    bar.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home' }))
    await Promise.resolve()
    const tip = () => host.querySelector('[data-testid="story-day-tip"]')?.textContent?.replace(/\s+/g, ' ').trim()
    expect(tip()).toBe('1.: 1 Ladung, 20,0 kWh')
    bar.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }))
    await Promise.resolve()
    expect(tip()).toBe('2.: nichts erfasst')
    bar.dispatchEvent(new KeyboardEvent('keydown', { key: 'End' }))
    await Promise.resolve()
    expect(tip()).toBe('25.: nichts erfasst')
  })

  it('Fahrtage als Säule nach km, Ladetage grün, Legende nennt nur, was vorkommt', () => {
    const plain = mount(days([1, 15], null, 25), quality())
    expect(plain.querySelectorAll('[data-role="km"]')).toHaveLength(0)
    const legend = (h: HTMLElement) => [...h.querySelectorAll('[data-testid="story-days-legend"] > span')].map(x => x.textContent!.trim())
    expect(legend(plain)).toEqual(['geladen'])
    app!.unmount(); app = null
    const host = mount(days([1, 15], [2, 14], 25, [16, 17]), quality())
    const km = host.querySelectorAll('[data-role="km"]')
    expect(km).toHaveLength(2)
    expect((km[0] as HTMLElement).style.height).toBe('100%')
    expect(legend(host)).toEqual(['geladen', 'gefahren', 'nichts erfasst'])
  })

  it('Tagesanzeige nennt Fahrten mit km', async () => {
    const host = mount(days([16], null, 25, [16]), quality())
    const bar = host.querySelector('[data-testid="story-days"]') as HTMLElement
    for (let i = 0; i < 16; i++) bar.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }))
    await Promise.resolve()
    expect(host.querySelector('[data-testid="story-day-tip"]')!.textContent!.replace(/\s+/g, ' ').trim()).toBe('16.: 1 Ladung, 20,0 kWh · 2 Fahrten, 42 km')
  })
})

describe('DataQualityLine - Tag öffnen', () => {
  // 31 Felder auf 310 px: Feld i liegt bei 10*i bis 10*i+10
  const bar = (host: Element) => {
    const el = host.querySelector<HTMLElement>('[data-testid="story-days"]')!
    el.getBoundingClientRect = () => ({ left: 0, width: 310, top: 0, height: 20, right: 310, bottom: 20, x: 0, y: 0, toJSON: () => ({}) })
    return el
  }
  const click = (el: HTMLElement, x: number) => el.dispatchEvent(new MouseEvent('click', { clientX: x, bubbles: true }))

  it('Klick auf einen vergangenen Tag öffnet ihn', () => {
    const open = vi.fn()
    const host = mount(days([2], null, 20), quality(), open)
    click(bar(host), 15)
    expect(open).toHaveBeenCalledWith(2)
  })

  it('Tage nach heute öffnen nichts', () => {
    const open = vi.fn()
    const host = mount(days([2], null, 20), quality(), open)
    click(bar(host), 255)
    expect(open).not.toHaveBeenCalled()
  })

  it('Enter öffnet den mit Pfeilen gewählten Tag', () => {
    const open = vi.fn()
    const host = mount(days([2], null, 20), quality(), open)
    const el = bar(host)
    el.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }))
    el.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }))
    el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    expect(open).toHaveBeenCalledWith(2)
  })
})
