// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, ref, type App } from 'vue'
import { i18n } from '../../../i18n'
import StoryMonthSwitch from '../StoryMonthSwitch.vue'
import { monthAt, type StoryMonth } from '../../../composables/useDashboardStory'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const NAMES = ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez']
const labelOf = (m: StoryMonth) => NAMES[m.month - 1]
const key = (o: number) => { const m = monthAt(o); return `${m.year}-${m.month}` }

function mount(offset: number, minOffset: number, dataOffsets: number[]) {
  const picked: number[] = []
  const current = ref(offset)
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({
    components: { StoryMonthSwitch },
    setup: () => ({
      months: [-2, -1, 0].map(o => monthAt(o)), current, minOffset, labelOf,
      dataMonths: new Set(dataOffsets.map(key)),
      onSelect: (o: number) => { picked.push(o); current.value = o },
    }),
    template: '<StoryMonthSwitch :months="months" :offset="current" :label-of="labelOf" :min-offset="minOffset" :data-months="dataMonths" @select="onSelect" />',
  })
  app.use(i18n)
  app.mount(host)
  return { host, picked }
}

describe('StoryMonthSwitch', () => {
  it('ohne ältere Ladungen kein Kalender-Segment', () => {
    const { host } = mount(0, -2, [0])
    expect(host.querySelector('[data-testid="story-month-older"]')).toBeNull()
  })

  it('der Kalender liegt links außerhalb; ein Klick dehnt den Rand auf ihn aus und öffnet zugleich das Raster', async () => {
    const { host, picked } = mount(0, -9, [0, -5, -9])
    const frame = host.querySelector('[data-testid="story-month-frame"]')!
    const older = host.querySelector<HTMLButtonElement>('[data-testid="story-month-older"]')!
    expect(frame.getAttribute('data-expanded')).toBe('false')
    older.click()
    await nextTick()
    expect(frame.getAttribute('data-expanded')).toBe('true')
    expect(older.getAttribute('aria-expanded')).toBe('true')
    const panel = document.querySelector('[data-testid="story-month-grid-panel"]')!
    expect(panel.getAttribute('role')).toBe('dialog')
    const cell = (o: number) => panel.querySelector<HTMLButtonElement>(`[data-testid="story-month-pick-${o}"]`)!
    expect(cell(-5).disabled).toBe(false)
    expect(cell(-4).disabled).toBe(true)
    expect(cell(0).getAttribute('aria-current')).toBe('true')
    cell(-5).click()
    await nextTick()
    expect(picked).toEqual([-5])
    expect(older.getAttribute('aria-expanded')).toBe('false')
  })

  it('zu ohne älteren Monat: Raster und Rand gehen zugleich zurück', async () => {
    const { host } = mount(0, -9, [0, -5])
    const frame = host.querySelector('[data-testid="story-month-frame"]')!
    host.querySelector<HTMLButtonElement>('[data-testid="story-month-older"]')!.click()
    await nextTick()
    document.querySelector('[data-testid="story-month-grid-panel"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    expect(frame.getAttribute('data-expanded')).toBe('false')
  })

  it('das Raster zeigt ein Jahr, mit ‹ und › durchschaltbar, nicht vor die erste Ladung und nicht in die Zukunft', async () => {
    const { host } = mount(0, -19, [0, -19])
    host.querySelector<HTMLButtonElement>('[data-testid="story-month-older"]')!.click()
    await nextTick()
    const panel = document.querySelector('[data-testid="story-month-grid-panel"]')!
    const year = () => panel.querySelector('[data-testid="story-month-year"]')!.textContent!.trim()
    const prev = panel.querySelector<HTMLButtonElement>('[data-testid="story-month-year-prev"]')!
    const next = panel.querySelector<HTMLButtonElement>('[data-testid="story-month-year-next"]')!
    const nowYear = new Date().getFullYear()
    expect(year()).toBe(String(nowYear))
    expect(panel.querySelectorAll('[data-month-cell]')).toHaveLength(12)
    expect(next.disabled).toBe(true)
    const firstYear = monthAt(-19).year
    while (Number(year()) > firstYear) { prev.click(); await nextTick() }
    expect(prev.disabled).toBe(true)
    // Das Raster gleitet erst aus, dann das neue Jahr herein
    await vi.waitFor(() => expect(panel.querySelector('[data-testid="story-month-pick--19"]')).not.toBeNull())
    expect(panel.querySelector<HTMLButtonElement>('[data-testid="story-month-pick--19"]')!.disabled).toBe(false)
    // Der Fokus steht im hereingeglittenen Jahr, nicht auf einem entfernten Monat des alten
    expect((document.activeElement as HTMLElement).hasAttribute('data-month-cell')).toBe(true)
    expect(panel.contains(document.activeElement)).toBe(true)
  })

  it('ein älterer Monat steht im Kalender-Segment, und das ist aktiv', () => {
    const { host } = mount(-5, -9, [0, -5])
    const older = host.querySelector<HTMLButtonElement>('[data-testid="story-month-older"]')!
    const m = monthAt(-5)
    expect(older.textContent).toContain(NAMES[m.month - 1])
    expect(older.getAttribute('data-active')).toBe('true')
    expect(host.querySelector('[data-testid="story-month-frame"]')!.getAttribute('data-expanded')).toBe('true')
    expect(host.querySelector('[data-testid="story-month-0"]')!.getAttribute('data-active')).toBe('false')
  })
})
