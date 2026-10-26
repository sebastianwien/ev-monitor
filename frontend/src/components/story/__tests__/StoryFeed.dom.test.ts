// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryFeed from '../StoryFeed.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }

function mount(on: Record<string, unknown>) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryFeed, {
    feed: [{ id: 'cost_trend', question: 1, relevance: 40, sentiment: 'good', params: { diff: -3, current: 15, previous: 18 } }],
    upsellTo: '/upgrade', modelName: 'Model 3', carId: 'c1', carName: 'Tesla Model 3', sheets: ['cost'], ...on,
  })
  app.component('router-link', RouterLink)
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}

describe('StoryFeed', () => {
  it('Überschrift "Diesen Monat", Zeile öffnet die Kachel, Karte meldet sich', () => {
    const opened: string[] = []
    let map = 0
    const host = mount({ onOpen: (id: unknown) => opened.push(id as string), onMap: () => { map++ } })
    expect(host.querySelector('h2')!.textContent).toBe('Diesen Monat')
    ;(host.querySelector('[data-testid="story-feed-cost_trend"]') as HTMLButtonElement).click()
    ;(host.querySelector('[data-testid="story-map-open"]') as HTMLButtonElement).click()
    expect(opened).toEqual(['cost'])
    expect(map).toBe(1)
  })

  it('nennt einen vergangenen Monat beim Namen statt "Diesen Monat"', () => {
    const host = mount({ runningMonth: false, monthName: 'September' })
    expect(host.querySelector('h2')!.textContent).toBe('Im September')
  })
})
