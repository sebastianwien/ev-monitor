// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryNewsList from '../StoryNewsList.vue'
import type { Moment, MomentSeen, ScoredMoment } from '../../../composables/storyMoments'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }
const NOW = new Date(2026, 9, 24, 12).getTime()
const frost: Moment = { kind: 'first_frost', key: 'first_frost:2026', at: NOW, lifetimeMs: 3 * 86_400_000, actionable: false, dismissible: false, tempC: 3, winterKm: 330, summerKm: 430 }
const milestone: Moment = { kind: 'milestone', key: 'milestone:km:20000', at: NOW, lifetimeMs: 3 * 86_400_000, actionable: false, dismissible: false, metric: 'km', value: 20000, charges: 400, km: 20000, co2Kg: 2400 }
const silence: Moment = { kind: 'silence', key: 'silence:l', at: NOW - 86_400_000, lifetimeMs: Infinity, actionable: true, dismissible: true, days: 15, kwh: 20, autoSync: true }
const silence2: Moment = { ...silence, key: 'silence:m' }
const scored = (...ms: Moment[]): ScoredMoment[] => ms.map((moment, i) => ({ moment, score: 100 - i }))
const NONE: MomentSeen = { shown: {}, dismissed: {} }

function mount(cards: ScoredMoment[], seen: MomentSeen = NONE, events: unknown[][] = []) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryNewsList, {
    cards, carId: 'c1', carName: 'Tesla Model 3', seen,
    onAct: (m: Moment, id: string) => events.push(['act', m.kind, id]),
    onDismiss: (m: Moment) => events.push(['dismiss', m.kind]),
    onShown: (m: Moment) => events.push(['shown', m.kind]),
  })
  app.component('router-link', RouterLink)
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}
const all = (sel: string) => [...document.querySelectorAll<HTMLElement>(sel)]

describe('StoryNewsList (Desktop, in der Spalte)', () => {
  it('ohne Karten nichts', () => {
    mount([])
    expect(document.querySelector('[data-testid="story-news-inline"]')).toBeNull()
  })

  it('zeigt die Karten untereinander, ohne Abdunkeln, und meldet jede als gezeigt', async () => {
    const events: unknown[][] = []
    mount(scored(frost, silence), NONE, events)
    await nextTick()
    expect(all('[data-testid="story-news-inline"] article')).toHaveLength(2)
    expect(document.querySelector('[data-testid="story-news-backdrop"]')).toBeNull()
    expect(events).toEqual([['shown', 'first_frost'], ['shown', 'silence']])
  })

  it('× meldet erledigt für genau diese Karte', async () => {
    const events: unknown[][] = []
    mount(scored(frost, silence), NONE, events)
    all('[data-testid="story-news-dismiss"]')[1].click()
    await nextTick()
    expect(events).toContainEqual(['dismiss', 'silence'])
  })

  it('mehr als drei: "Alle zeigen" klappt den Rest auf und meldet ihn erst dann als gezeigt', async () => {
    const events: unknown[][] = []
    mount(scored(frost, silence, milestone, silence2), { shown: { [milestone.key]: NOW }, dismissed: {} }, events)
    await nextTick()
    expect(all('[data-testid="story-news-inline"] article')).toHaveLength(3)
    expect(events.filter(e => e[0] === 'shown')).toHaveLength(3)
    document.querySelector<HTMLElement>('[data-testid="story-news-all"]')!.click()
    await nextTick()
    expect(all('[data-testid="story-news-inline"] article')).toHaveLength(4)
    expect(events.filter(e => e[0] === 'shown')).toHaveLength(4)
  })

  it('Meilenstein zum ersten Mal: Konfetti, schon gesehen: keins', async () => {
    mount(scored(milestone))
    await nextTick()
    expect(document.querySelector('[data-testid="story-confetti"]')).not.toBeNull()
    app!.unmount(); app = null; document.body.innerHTML = ''
    mount(scored(milestone), { shown: { [milestone.key]: NOW }, dismissed: {} })
    await nextTick()
    expect(document.querySelector('[data-testid="story-confetti"]')).toBeNull()
  })
})
