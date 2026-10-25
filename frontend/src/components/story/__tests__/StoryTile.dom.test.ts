// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryTile from '../StoryTile.vue'
import type { StoryTile as Tile } from '../../../composables/storyModel'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const cost = (over: Partial<Tile> = {}): Tile => ({
  id: 'cost', value: 0, delta: null, deltaPercent: null, sentiment: 'neutral', spark: [0, 18, 0], sparkVariant: 'line', ...over,
})

function mount(tile: Tile) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryTile, { tile })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host.textContent!.replace(/\s+/g, ' ')
}

describe('StoryTile Kosten', () => {
  it('Ladungen ohne Preis: nennt ihre Zahl statt eines Vergleichs mit dem Vormonat', () => {
    const text = mount(cost({ pricelessCharges: 2, sentiment: 'warn' }))
    expect(text).toContain('2 ohne Preis')
    expect(text).not.toContain('Vormonat')
  })

  it('Vormonat unvollständig: kein Vergleich, auch nicht "kein Vormonat"', () => {
    const text = mount(cost({ value: 21, previousIncomplete: true }))
    expect(text).not.toContain('Vormonat')
  })

  it('laufender Monat unter der Strommenge des Vormonats: dezente Prognose statt Ersparnis', () => {
    const text = mount(cost({ value: 60, projection: { eur: 120.4, previousEur: 100 } }))
    expect(text).toMatch(/Prognose ~120\s€, Vormonat 100\s€/)
    expect(text).not.toContain('zum Vormonat')
  })

  it('ohne Vormonat bleibt "kein Vormonat"', () => {
    expect(mount(cost({ value: 21 }))).toContain('kein Vormonat')
  })
})
