// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import StoryLiveHero from '../StoryLiveHero.vue'
import type { LiveChargingData } from '../../../composables/useChargingLive'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const live = (over: Partial<LiveChargingData> = {}): LiveChargingData => ({
  isActive: true, powerKw: 11, socPercent: 63, socAtSessionStart: 40, chargeLimitSoc: 80,
  timeToFullMinutes: 50, estRangeKm: 280, chargingType: 'AC', ...over,
} as LiveChargingData)

function mount(data: LiveChargingData) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(StoryLiveHero, { live: data })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}

describe('StoryLiveHero', () => {
  it('zeigt den Ring mit dem gemeldeten Ladestand', () => {
    const host = mount(live())
    const ring = host.querySelector('[role="progressbar"]')
    expect(ring?.getAttribute('aria-valuenow')).toBe('63')
    expect(ring?.textContent).toContain('63')
  })

  it('ohne Ladestand gibt es keinen Ring, die Leistung bleibt', () => {
    const host = mount(live({ socPercent: null, socAtSessionStart: null }))
    expect(host.querySelector('[role="progressbar"]')).toBeNull()
    expect(host.textContent).not.toContain('0%')
    expect(host.textContent).toContain('kW')
  })
})
