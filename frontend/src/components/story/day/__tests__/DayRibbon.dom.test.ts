// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { i18n } from '../../../../i18n'
import DayRibbon from '../DayRibbon.vue'
import { eventsOfDay, type DayTrip } from '../../../../composables/storyDay'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.replaceChildren() })

const at = (h: number, m = 0) => new Date(2026, 7, 16, h, m).toISOString()
const trip = (id: string, h: number, socStart: number, socEnd: number): DayTrip => ({
  id, tripStartedAt: at(h), tripEndedAt: at(h, 40), distanceKm: 10, socStart, socEnd,
})

async function mount(highlightIds: string[] | null) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const events = eventsOfDay('2026-08-16', [trip('a', 9, 80, 76), trip('b', 15, 76, 70), trip('c', 20, 70, 66)], [])
  app = createApp(DayRibbon, { events, selectedId: null, interactive: false, highlightIds })
  app.use(i18n)
  app.mount(host)
  await nextTick()
  return [...host.querySelectorAll<HTMLElement>('[data-testid="day-bar"][data-kind="trip"]')]
}

describe('DayRibbon Hervorheben (Hover im Logbuch)', () => {
  it('die Balken des Eintrags bleiben kräftig, die übrigen treten zurück', async () => {
    const bars = await mount(['a'])
    const dimmed = bars.map(b => b.classList.contains('opacity-25'))
    // a (9:00 bis 9:40) liegt auf den ersten beiden Fahrt-Balken, b und c danach
    expect(dimmed).toEqual([false, false, true, true, true, true])
  })

  it('ein Bündel hebt die Balken aller seiner Teile hervor', async () => {
    const bars = await mount(['a', 'c'])
    expect(bars.map(b => b.classList.contains('opacity-25'))).toEqual([false, false, true, true, false, false])
  })

  it('ohne Hover tritt nichts zurück', async () => {
    const bars = await mount(null)
    expect(bars.some(b => b.classList.contains('opacity-25'))).toBe(false)
  })
})
