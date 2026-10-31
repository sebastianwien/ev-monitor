// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryNewsStack from '../StoryNewsStack.vue'
import type { Moment, MomentSeen, ScoredMoment } from '../../../composables/storyMoments'

let app: App | null = null
beforeEach(() => { sessionStorage.clear() })
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }
const NOW = new Date(2026, 9, 24, 12).getTime()
const frost: Moment = { kind: 'first_frost', key: 'first_frost:2026', at: NOW, lifetimeMs: 3 * 86_400_000, actionable: false, dismissible: false, tempC: 3, winterKm: 330, summerKm: 430 }
const milestone: Moment = { kind: 'milestone', key: 'milestone:km:20000', at: NOW, lifetimeMs: 3 * 86_400_000, actionable: false, dismissible: false, metric: 'km', value: 20000, charges: 400, km: 20000, co2Kg: 2400 }
const silence: Moment = { kind: 'silence', key: 'silence:l', at: NOW - 86_400_000, lifetimeMs: Infinity, actionable: true, dismissible: true, days: 15, kwh: 20, autoSync: true }
const scored = (...ms: Moment[]): ScoredMoment[] => ms.map((moment, i) => ({ moment, score: 100 - i }))
const NONE: MomentSeen = { shown: {}, dismissed: {} }

function mount(cards: ScoredMoment[], seen: MomentSeen = NONE, events: unknown[][] = []) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryNewsStack, {
    cards, carId: 'c1', carName: 'Tesla Model 3', seen,
    onAct: (m: Moment, id: string) => events.push(['act', m.kind, id]),
    onDismiss: (m: Moment) => events.push(['dismiss', m.kind]),
    onShown: (m: Moment) => events.push(['shown', m.kind]),
    onCollapsed: () => events.push(['collapsed']),
    onExpanded: () => events.push(['expanded']),
    onList: () => events.push(['list']),
  })
  app.component('router-link', RouterLink)
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}
const q = (sel: string) => document.querySelector<HTMLElement>(sel)
const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim() ?? ''
/** Transition-Leave in jsdom: ein Frame, bis das Element weg ist. */
const settle = () => new Promise(r => setTimeout(r, 80))
/** Wegfliegen der Karte abwarten (200 ms). */
const flown = () => new Promise(r => setTimeout(r, 260))
/** Finger-Zug als Pointer Events; jsdom kennt kein PointerEvent, Vue hört nur auf den Typ. */
function drag(el: Element, path: [number, number][]) {
  const fire = (type: string, [x, y]: [number, number]) => {
    const e = new MouseEvent(type, { bubbles: true, cancelable: true, clientX: x, clientY: y, button: 0 })
    Object.defineProperties(e, { isPrimary: { value: true }, pointerId: { value: 1 } })
    el.dispatchEvent(e)
  }
  fire('pointerdown', path[0])
  for (const p of path.slice(1)) fire('pointermove', p)
  fire('pointerup', path[path.length - 1])
}

describe('StoryNewsStack', () => {
  it('ohne Karten nichts, mit einer Karte der Stapel ohne Punkte, Hintergrund abgedunkelt', async () => {
    mount([])
    await nextTick()
    expect(q('[data-testid="story-news"]')).toBeNull()
    expect(q('[data-testid="story-news-tab"]')).toBeNull()
    app!.unmount(); app = null
    const events: unknown[][] = []
    mount(scored(frost), NONE, events)
    await nextTick()
    expect(q('[data-testid="story-news"]')).not.toBeNull()
    expect(q('[data-testid="story-news-backdrop"]')).not.toBeNull()
    expect(document.querySelectorAll('[data-testid="story-news-peek"]')).toHaveLength(0)
    expect(q('[data-testid="story-news-all"]')).toBeNull()
    // Gesten auf Karte und Abdunkelung gehören dem Stapel, nicht dem Seiten-Pager oder der Zurück-Geste
    expect(q('[data-testid="story-news"]')!.hasAttribute('data-no-swipe')).toBe(true)
    expect(q('[data-testid="story-news-backdrop"]')!.hasAttribute('data-no-swipe')).toBe(true)
    expect(text(q('[data-testid="story-news-first_frost"]'))).toContain('Es wird kalt')
    expect(events).toEqual([['shown', 'first_frost']])
  })

  it('mehrere Karten als Stapel: Kanten dahinter, kein Weiter, Alle zeigen klappt die Liste auf, Alle erledigt räumt', async () => {
    const events: unknown[][] = []
    mount(scored(frost, silence, milestone), NONE, events)
    await nextTick()
    expect(document.querySelectorAll('[data-testid="story-news-peek"]')).toHaveLength(2)
    expect(q('[data-testid="story-news-next"]')).toBeNull()
    expect(q('[data-testid="story-news-position"]')).toBeNull()
    // Sagt, was dahinter liegt: alle Neuigkeiten, nicht alle Fahrten; steht in der Karte bei den Aktionen
    expect(text(q('[data-testid="story-news-all"]'))).toBe('Alle Neuigkeiten zeigen')
    expect(q('[data-testid="story-news-sentence"]')!.closest('article')!.contains(q('[data-testid="story-news-all"]'))).toBe(true)
    q('[data-testid="story-news-all"]')!.click()
    await nextTick()
    expect(text(q('[data-testid="story-news-list"]'))).toContain('Es wird kalt')
    expect(text(q('[data-testid="story-news"]'))).toContain('Neu für dich (3)')
    q('[data-testid="story-news-all-done"]')!.click()
    expect(events.filter(e => e[0] === 'dismiss')).toEqual([['dismiss', 'first_frost'], ['dismiss', 'silence'], ['dismiss', 'milestone']])
    expect(events.filter(e => e[0] === 'shown').map(e => e[1])).toEqual(['first_frost'])
    expect(events).toContainEqual(['list'])
  })

  it('seitlich wischen fliegt die oberste Karte raus und meldet erledigt, ein kurzer Zug federt zurück', async () => {
    const events: unknown[][] = []
    mount(scored(frost, silence), NONE, events)
    await nextTick()
    const stack = q('[data-testid="story-news-drag"]')!
    drag(stack, [[100, 300], [104, 301]])
    await flown()
    expect(events.filter(e => e[0] === 'dismiss')).toEqual([])
    drag(stack, [[100, 300], [130, 302], [320, 305]])
    await flown()
    expect(events.filter(e => e[0] === 'dismiss')).toEqual([['dismiss', 'first_frost']])
  })

  it('nach unten ziehen klappt zum Reiter ein', async () => {
    const events: unknown[][] = []
    mount(scored(frost, silence), NONE, events)
    await nextTick()
    drag(q('[data-testid="story-news-drag"]')!, [[100, 300], [102, 330], [103, 450]])
    await settle()
    expect(q('[data-testid="story-news"]')).toBeNull()
    expect(text(q('[data-testid="story-news-tab"]'))).toBe('2 Neuigkeiten')
    expect(events).toContainEqual(['collapsed'])
  })

  it('× meldet erledigt, Tipp daneben klappt zum Reiter ein, der Reiter klappt wieder auf', async () => {
    const events: unknown[][] = []
    mount(scored(frost, silence), NONE, events)
    await nextTick()
    q('[data-testid="story-news-dismiss"]')!.click()
    await flown()
    expect(events).toContainEqual(['dismiss', 'first_frost'])
    q('[data-testid="story-news-backdrop"]')!.click()
    await settle()
    expect(q('[data-testid="story-news"]')).toBeNull()
    expect(text(q('[data-testid="story-news-tab"]'))).toBe('2 Neuigkeiten')
    expect(sessionStorage.getItem('story_news_collapsed')).toBe('first_frost:2026|silence:l')
    q('[data-testid="story-news-tab"]')!.click()
    await nextTick()
    expect(q('[data-testid="story-news"]')).not.toBeNull()
    expect(events).toContainEqual(['collapsed'])
    expect(events).toContainEqual(['expanded'])
  })

  it('bleibt in diesem Tab eingeklappt, bis etwas Neues dazukommt; Escape klappt ein', async () => {
    sessionStorage.setItem('story_news_collapsed', 'first_frost:2026')
    mount(scored(frost))
    await nextTick()
    expect(q('[data-testid="story-news"]')).toBeNull()
    expect(text(q('[data-testid="story-news-tab"]'))).toBe('1 Neuigkeit')
    app!.unmount(); app = null
    mount(scored(frost, silence))
    await nextTick()
    expect(q('[data-testid="story-news"]')).not.toBeNull()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await settle()
    expect(q('[data-testid="story-news"]')).toBeNull()
  })

  it('Konfetti beim ersten Meilenstein, nicht bei einem schon gezeigten', async () => {
    mount(scored(milestone))
    await nextTick()
    expect(q('[data-testid="story-confetti"]')).not.toBeNull()
    app!.unmount(); app = null
    mount(scored(milestone), { shown: { 'milestone:km:20000': NOW - 1000 }, dismissed: {} })
    await nextTick()
    expect(q('[data-testid="story-confetti"]')).toBeNull()
  })
})
