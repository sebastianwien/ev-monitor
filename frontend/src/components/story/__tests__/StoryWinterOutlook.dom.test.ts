// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryWinterOutlook from '../StoryWinterOutlook.vue'
import type { OutlookDay } from '../../../composables/storyMoments'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const range = { winterKm: 278, summerKm: 351 }
// Montag bis Sonntag
const days: OutlookDay[] = [
  { date: '2026-11-02', minC: 3, maxC: 10, kind: 'sun' },
  { date: '2026-11-03', minC: 2, maxC: 9, kind: 'partly' },
  { date: '2026-11-04', minC: 1, maxC: 7, kind: 'cloudy' },
  { date: '2026-11-05', minC: 1, maxC: 6, kind: 'rain' },
  { date: '2026-11-06', minC: 0, maxC: 5, kind: 'cloudy' },
  { date: '2026-11-07', minC: -2, maxC: 7, kind: 'snow' },
  { date: '2026-11-08', minC: 3, maxC: 8, kind: 'wind' },
]

function mount(props: Record<string, unknown>) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryWinterOutlook, { eyebrow: 'Ausblick November', range: null, week: null, ...props })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}
const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim() ?? ''
const q = (host: Element, id: string) => host.querySelector(`[data-testid="${id}"]`)

describe('StoryWinterOutlook', () => {
  it('zeigt die Reichweite als zwei beschriftete Balken, Sommer als Bezug, auf 10 km abgerundet', () => {
    const host = mount({ range })
    const bars = [...host.querySelectorAll<HTMLElement>('[data-bar]')]
    expect(bars.map(b => b.dataset.bar)).toEqual(['summer', 'winter'])
    expect(bars[0].style.width).toBe('100%')
    expect(parseFloat(bars[1].style.width)).toBeCloseTo(79.2, 1)
    const grid = host.querySelector('[data-testid="outlook-range"] [aria-hidden="true"]')!
    expect([...grid.children].map(text)).toEqual(['Sommer', '', '~350 km', 'Winter', '', '~270 km'])
    expect(text(host.querySelector('[data-testid="outlook-range"] h3'))).toBe('Reichweite mit einer Akkuladung')
    // Vorleser bekommen den Satz statt der Balken
    expect(host.querySelector('[data-testid="outlook-range"] .sr-only')?.textContent).toBe('Im Winter kommst du mit einer Akkuladung etwa 270 km weit, im Sommer 350 km.')
    expect(q(host, 'outlook-week')).toBeNull()
  })

  it('zeigt die nächsten Tage als Leiste mit Wochentag, Wetter, Höchst- und Tiefstwert', () => {
    const host = mount({ week: { days, deltaC: -4 } })
    expect(text(host.querySelector('[data-testid="outlook-week"] h3'))).toBe('Nächste 7 Tage')
    expect(text(q(host, 'outlook-trend'))).toBe('4° kälter als letzte Woche')
    const cells = [...host.querySelectorAll('[data-testid="outlook-day"]')]
    expect(cells).toHaveLength(7)
    expect(cells.map(c => c.querySelector('[data-weather]')?.getAttribute('data-weather'))).toEqual(['sun', 'partly', 'cloudy', 'rain', 'cloudy', 'snow', 'wind'])
    const visible = (c: Element) => [...c.querySelector('[aria-hidden="true"]')!.children].map(text).filter(Boolean)
    // Wochentag wie Intl ihn in der Umgebung schreibt (Node "Mo", Chrome "Mo.")
    const wd = (day: number) => new Intl.DateTimeFormat('de', { weekday: 'short' }).format(new Date(2026, 10, day, 12))
    expect(visible(cells[0])).toEqual([wd(2), '10°', '3°'])
    expect(visible(cells[5])).toEqual([wd(7), '7°', '-2°'])
    expect(cells[0].querySelector('.sr-only')?.textContent).toBe(`${wd(2)}: Sonnig, 3 bis 10 °C`)
    expect(q(host, 'outlook-range')).toBeNull()
  })

  it('ohne Tendenz nur die Überschrift, wärmer heißt wärmer', () => {
    expect(q(mount({ week: { days, deltaC: null } }), 'outlook-trend')).toBeNull()
    app?.unmount(); document.body.innerHTML = ''
    expect(text(q(mount({ week: { days, deltaC: 3 } }), 'outlook-trend'))).toBe('3° wärmer als letzte Woche')
  })

  it('so viele Spalten wie Tage', () => {
    const host = mount({ week: { days: days.slice(0, 5), deltaC: null } })
    expect((host.querySelector('[data-testid="outlook-week"] ol') as HTMLElement).style.gridTemplateColumns).toBe('repeat(5, minmax(0, 1fr))')
    expect(text(host.querySelector('[data-testid="outlook-week"] h3'))).toBe('Nächste 5 Tage')
  })
})
