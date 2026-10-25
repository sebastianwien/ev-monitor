// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
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
function days(withData: number[], gap: [number, number] | null, today = 31): DayCoverage[] {
  return Array.from({ length: 31 }, (_, i) => {
    const day = i + 1
    return { day, hasData: withData.includes(day), inLongestGap: !!gap && day >= gap[0] && day <= gap[1], future: day > today }
  })
}
const quality = (over: Partial<DataQuality> = {}): DataQuality =>
  ({ estimated: 0, total: 12, estimatedShare: 0, priceless: 0, pricelessOpen: 0, severity: 'ok', ...over })

function mount(d: DayCoverage[], q: DataQuality | null) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(DataQualityLine, { days: d, quality: q })
  app.component('router-link', RouterLink)
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}
const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim() ?? ''

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
})
