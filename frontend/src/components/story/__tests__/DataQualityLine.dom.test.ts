// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import DataQualityLine from '../DataQualityLine.vue'
import type { DayCoverage } from '../../../composables/storyModel'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }

/** Monat mit 31 Tagen: Ladungen an `withData`, Zukunft ab `today + 1`, Fahrten an `tripDays`. */
function days(withData: number[], today = 31, tripDays: number[] = []): DayCoverage[] {
  return Array.from({ length: 31 }, (_, i) => {
    const day = i + 1
    const charge = withData.includes(day)
    const trip = tripDays.includes(day)
    return { day, hasData: charge || trip, inLongestGap: false, future: day > today, charges: charge ? 1 : 0, kwh: charge ? 20 : 0, trips: trip ? 2 : 0, tripKm: trip ? 42 : 0 }
  })
}

function mount(d: DayCoverage[], onOpenDay?: (day: number) => void) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(DataQualityLine, { days: d, onOpenDay })
  app.component('router-link', RouterLink)
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}
const text = (host: HTMLElement) => host.textContent!.replace(/\s+/g, ' ').trim()
const tip = (host: HTMLElement) => host.querySelector('[data-testid="story-day-tip"]')?.textContent?.replace(/\s+/g, ' ').trim()
const settle = async () => { for (let i = 0; i < 3; i++) await Promise.resolve() }

describe('DataQualityLine', () => {
  it('beim Öffnen ist der letzte Ladetag gewählt, die Anzeige nennt ihn', async () => {
    const host = mount(days([3, 15, 24], 28))
    await settle()
    expect(tip(host)).toBe('24.: 1 Ladung, 20,0 kWh')
    expect(host.querySelectorAll('[data-role="day"]')[23].className).toContain('scale-y-[1.4]')
  })

  it('ohne Ladung im Monat keine Anzeige, keine Zeile über Tage oder Lücken', async () => {
    const host = mount(days([], 14))
    await settle()
    expect(host.querySelector('[data-testid="story-day-tip"]')).toBeNull()
    expect(text(host)).not.toContain('Tage')
    expect(text(host)).not.toContain('nichts erfasst')
  })

  it('kein Gelb: leere Tage grau, Tage nach heute heller', () => {
    const host = mount(days([1], 14))
    const cells = host.querySelectorAll('[data-role="day"]')
    expect(cells[0].className).toContain('bg-green-700')
    expect(cells[5].className).toContain('bg-gray-200')
    expect(cells[20].className).toContain('bg-gray-100')
    expect(host.innerHTML).not.toContain('amber')
    expect(text(host)).not.toContain('nichts erfasst')
  })

  it('Achse: Tage 1, 8, 15, 22 und 29 unter der Leiste', () => {
    const host = mount(days([1]))
    expect([...host.querySelectorAll('[data-role="tick"]')].map(x => x.textContent)).toEqual(['1.', '8.', '15.', '22.', '29.'])
  })

  it('Tagesanzeige per Tastatur: Pfeile wandern, Satz nennt Ladungen und kWh; Blur kehrt zum Ladetag zurück', async () => {
    const host = mount(days([1, 15], 25))
    const bar = host.querySelector('[data-testid="story-days"]') as HTMLElement
    bar.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home' }))
    await settle()
    expect(tip(host)).toBe('1.: 1 Ladung, 20,0 kWh')
    bar.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }))
    await settle()
    expect(tip(host)).toBe('2.: nichts erfasst')
    bar.dispatchEvent(new KeyboardEvent('keydown', { key: 'End' }))
    await settle()
    expect(tip(host)).toBe('25.: nichts erfasst')
    bar.dispatchEvent(new Event('blur'))
    await settle()
    expect(tip(host)).toBe('15.: 1 Ladung, 20,0 kWh')
  })

  it('Enter öffnet den gewählten Tag, Tage nach heute nicht', async () => {
    const opened: number[] = []
    const host = mount(days([1, 15], 20), d => opened.push(d))
    const bar = host.querySelector('[data-testid="story-days"]') as HTMLElement
    bar.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }))
    bar.dispatchEvent(new KeyboardEvent('keydown', { key: 'End' }))
    for (let i = 0; i < 5; i++) bar.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }))
    bar.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }))
    await settle()
    expect(opened).toEqual([15])
    expect(tip(host)).toBe('25.: kommt noch')
  })

  it('ein geöffneter Tag bleibt gewählt, wenn das Tagesblatt den Fokus nimmt und wieder schließt', async () => {
    const spy = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
      const w = this.dataset.testid === 'story-days' ? 310 : 0
      return { width: w, height: 10, left: 0, top: 0, right: w, bottom: 10, x: 0, y: 0, toJSON: () => ({}) } as DOMRect
    })
    const opened: number[] = []
    const host = mount(days([3, 24], 28), d => opened.push(d))
    const bar = host.querySelector('[data-testid="story-days"]') as HTMLElement
    // Tipp auf Tag 6: 10 px je Tag
    bar.dispatchEvent(new MouseEvent('click', { clientX: 55 }))
    bar.dispatchEvent(new Event('blur'))
    bar.dispatchEvent(new Event('pointerleave'))
    await settle()
    expect(opened).toEqual([6])
    expect(tip(host)).toBe('6.: nichts erfasst')
    spy.mockRestore()
  })

  it('Fahrtage als Säule nach km, Ladetage grün, Legende nennt nur, was vorkommt', () => {
    const plain = mount(days([1, 15], 25))
    expect(plain.querySelectorAll('[data-role="km"]')).toHaveLength(0)
    const legend = (h: HTMLElement) => [...h.querySelectorAll('[data-testid="story-days-legend"] > span')].map(x => x.textContent!.trim())
    expect(legend(plain)).toEqual(['geladen'])
    app!.unmount(); app = null
    const host = mount(days([1, 15], 25, [16, 17]))
    const km = host.querySelectorAll('[data-role="km"]')
    expect(km).toHaveLength(2)
    expect((km[0] as HTMLElement).style.height).toBe('100%')
    expect(legend(host)).toEqual(['geladen', 'gefahren'])
    expect(text(host)).toContain('Tag antippen für Details')
  })

  it('Tagesanzeige nennt Fahrten mit km', async () => {
    const host = mount(days([16], 25, [16]))
    await settle()
    expect(tip(host)).toBe('16.: 1 Ladung, 20,0 kWh · 2 Fahrten, 42 km')
  })

  it('Sprechblase bleibt am Rand in der Leiste, die Spitze zeigt auf den Tag', async () => {
    // jsdom misst nichts: Leiste 350 px, Anzeige 120 px gestellt.
    const widths: Record<string, number> = { 'story-days': 350, 'story-day-tip': 120 }
    const spy = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
      const w = widths[this.dataset.testid ?? ''] ?? 0
      return { width: w, height: 10, left: 0, top: 0, right: w, bottom: 10, x: 0, y: 0, toJSON: () => ({}) } as DOMRect
    })
    const host = mount(days([1], 28))
    const bar = host.querySelector('[data-testid="story-days"]') as HTMLElement
    const tipEl = () => host.querySelector('[data-testid="story-day-tip"]') as HTMLElement
    const arrow = () => host.querySelector('[data-role="tip-arrow"]') as HTMLElement
    await settle()
    // Tag 1: Mitte bei 5,6 px, Blase am linken Rand, Spitze bei 6 px
    expect(tipEl().style.left).toBe('0px')
    expect(arrow().style.left).toBe('6px')
    for (let i = 0; i < 24; i++) bar.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }))
    await settle()
    // Tag 25 von 31: Mitte bei 277 px, mittig wäre 217 px, die Spitze bleibt mittig in der Blase
    expect(tipEl().style.left).toBe('217px')
    expect(arrow().style.left).toBe('60px')
    bar.dispatchEvent(new KeyboardEvent('keydown', { key: 'End' }))
    await settle()
    // Tag 28: Mitte bei 310 px, Blase höchstens bei 230 px, Spitze bei 80 px
    expect(tipEl().style.left).toBe('230px')
    expect(arrow().style.left).toBe('80px')
    spy.mockRestore()
  })
})
