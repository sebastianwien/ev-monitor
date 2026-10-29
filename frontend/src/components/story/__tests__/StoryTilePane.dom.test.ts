// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryTilePane from '../StoryTilePane.vue'
import type { StoryTile } from '../../../composables/storyModel'
import type { StorySheetContext } from '../StoryTileDetail.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

// Ohne Fahrtmonat landet die Kachel im allgemeinen Detail (Spark), das braucht keine Daten.
const tile: StoryTile = { id: 'trips', value: 18.2, delta: null, deltaPercent: null, sentiment: 'neutral', spark: [17, 19, 18.2], sparkVariant: 'line' }
const context: StorySheetContext = {
  stats: null, monthLabels: ['Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt'], monthName: 'Oktober', runningMonth: true,
  modelPeers: null, modelName: 'Model 3', temperature: null, sohHistory: [], effectiveCapacityKwh: 60,
  hasFixedCosts: false, loadMonthlyCosts: async () => null,
}

function mount(events: unknown[][] = []) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryTilePane, {
    tile, context, backLabel: 'Diesen Monat',
    onClose: () => events.push(['close']),
    onStep: (d: number) => events.push(['step', d]),
  })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}
const key = (k: string, target: EventTarget = document) =>
  target.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true }))

describe('StoryTilePane (Desktop, rechte Spalte)', () => {
  it('zeigt Titel und Rückweg ohne Abdunkeln und nimmt den Fokus auf den Titel', async () => {
    const host = mount()
    await nextTick()
    expect(host.querySelector('[data-testid="story-pane"] h3')?.textContent).toBeTruthy()
    expect(host.querySelector('[data-testid="story-pane-back"]')?.textContent).toContain('Diesen Monat')
    expect(document.querySelector('[aria-modal="true"]')).toBeNull()
    expect(document.activeElement?.tagName).toBe('H3')
  })

  it('Zurück und × schließen', () => {
    const events: unknown[][] = []
    const host = mount(events)
    host.querySelector<HTMLElement>('[data-testid="story-pane-back"]')!.click()
    host.querySelector<HTMLElement>('[data-testid="story-pane-close"]')!.click()
    expect(events).toEqual([['close'], ['close']])
  })

  it('Escape schließt, Pfeiltasten blättern zur nächsten oder vorigen Kachel', () => {
    const events: unknown[][] = []
    mount(events)
    key('ArrowRight'); key('ArrowLeft'); key('Escape')
    expect(events).toEqual([['step', 1], ['step', -1], ['close']])
  })

  it('Tasten gehören einem offenen Dialog oder einem Eingabefeld, nicht der Spalte', () => {
    const events: unknown[][] = []
    mount(events)
    const input = document.createElement('input')
    document.body.appendChild(input)
    key('ArrowRight', input)
    const dialog = document.createElement('div')
    dialog.setAttribute('aria-modal', 'true')
    document.body.appendChild(dialog)
    key('Escape')
    expect(events).toEqual([])
  })
})
