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
    expect(q('[data-testid="story-news-position"]')).toBeNull()
    expect(text(q('[data-testid="story-news-first_frost"]'))).toContain('Es wird kalt')
    expect(events).toEqual([['shown', 'first_frost']])
  })

  it('mehrere Karten: "1 von 2", Weiter blättert, Alle zeigen klappt die Liste auf, Alle erledigt räumt', async () => {
    const events: unknown[][] = []
    mount(scored(frost, silence), NONE, events)
    await nextTick()
    expect(text(q('[data-testid="story-news-position"]'))).toContain('1 von 2')
    q('[data-testid="story-news-next"]')!.click()
    await nextTick()
    expect(text(q('[data-testid="story-news-position"]'))).toContain('2 von 2')
    expect(q('[data-testid="story-news-silence"]')).not.toBeNull()
    q('[data-testid="story-news-all"]')!.click()
    await nextTick()
    expect(text(q('[data-testid="story-news-list"]'))).toContain('Es wird kalt')
    expect(text(q('[data-testid="story-news"]'))).toContain('Neu für dich (2)')
    q('[data-testid="story-news-all-done"]')!.click()
    expect(events.filter(e => e[0] === 'dismiss')).toEqual([['dismiss', 'first_frost'], ['dismiss', 'silence']])
    expect(events.filter(e => e[0] === 'shown').map(e => e[1])).toEqual(['first_frost', 'silence'])
    expect(events).toContainEqual(['list'])
  })

  it('× meldet erledigt, Tipp daneben klappt zum Reiter ein, der Reiter klappt wieder auf', async () => {
    const events: unknown[][] = []
    mount(scored(frost, silence), NONE, events)
    await nextTick()
    q('[data-testid="story-news-dismiss"]')!.click()
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
