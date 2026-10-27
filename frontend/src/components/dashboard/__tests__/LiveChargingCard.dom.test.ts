// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi, beforeEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createI18n } from 'vue-i18n'
import { createPinia } from 'pinia'
import de from '../../../locales/de.yaml'

vi.mock('../../../api/axios', () => ({ default: { get: vi.fn() } }))
import api from '../../../api/axios'
import LiveChargingCard from '../LiveChargingCard.vue'

const base = {
  isActive: true, chargingType: null, powerKw: null, socPercent: 45, energyRemainingKwh: null,
  timeToFullMinutes: null, estRangeKm: null, chargeAmps: null,
  sessionStartedAt: new Date(Date.now() - 30 * 60_000).toISOString(), sessionEndedAt: null,
  socAtSessionStart: 20, chargeLimitSoc: null, lastUpdatedAt: new Date().toISOString(),
  energyAddedKwh: null, updateIntervalSeconds: 30,
}

function mockApi(live: object, points: object[] = []) {
  vi.mocked(api.get).mockImplementation((url: string) => {
    if (String(url).includes('/history')) return Promise.resolve({ data: { points } }) as never
    return Promise.resolve({ data: live }) as never
  })
}

async function flush() {
  for (let i = 0; i < 15; i++) await Promise.resolve()
  await nextTick()
}

/** Ein Live-Ladevorgang erscheint bei jeder Marke mit Live-Quelle; gezeigt wird nur, was die Quelle liefert. */
describe('LiveChargingCard', () => {
  let app: App | null = null

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })
  afterEach(() => {
    app?.unmount()
    app = null
    document.body.innerHTML = ''
  })

  async function mount(brand: string) {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const i18n = createI18n({ legacy: false, locale: 'de', messages: { de }, missingWarn: false, fallbackWarn: false })
    app = createApp(LiveChargingCard, { carId: 'car-1', carDisplayName: 'Mein Auto', licensePlate: null, brand })
    app.use(createPinia())
    app.use(i18n)
    app.mount(host)
    await flush()
    return host
  }

  const q = (id: string) => document.querySelector(`[data-testid="${id}"]`)

  it('Smartcar without power: SoC curve instead of power curve, only tiles with values', async () => {
    mockApi({ ...base, energyAddedKwh: 12.4, updateIntervalSeconds: 300 }, [
      { timestamp: new Date(Date.now() - 20 * 60_000).toISOString(), powerKw: null, socPercent: 30 },
      { timestamp: new Date(Date.now() - 10 * 60_000).toISOString(), powerKw: null, socPercent: 40 },
    ])
    await mount('VW')

    expect(q('live-soc-chart')).not.toBeNull()
    expect(q('live-power-chart')).toBeNull()
    expect(q('live-metric-energy')?.textContent).toContain('12,4')
    expect(q('live-metric-duration')).not.toBeNull()
    expect(q('live-metric-time')).toBeNull()
    expect(q('live-metric-range')).toBeNull()
    expect(q('live-metric-amps')).toBeNull()
  })

  it('Polestar with power: power curve, remaining time without a made-up target', async () => {
    mockApi({ ...base, chargingType: 'AC', powerKw: 8.95, timeToFullMinutes: 240, estRangeKm: 320,
      chargeAmps: 13, updateIntervalSeconds: 120 }, [
      { timestamp: new Date(Date.now() - 2 * 60_000).toISOString(), powerKw: 10.9, socPercent: 44 },
      { timestamp: new Date(Date.now() - 60_000).toISOString(), powerKw: 8.95, socPercent: 45 },
    ])
    await mount('Polestar')

    expect(q('live-power-chart')).not.toBeNull()
    expect(q('live-soc-target')).toBeNull()
    const time = q('live-metric-time')?.textContent ?? ''
    expect(time).toContain('Voll in')
    expect(time).not.toContain('80')
  })

  it('Tesla with a charge limit keeps the target', async () => {
    mockApi({ ...base, chargingType: 'DC', powerKw: 120, timeToFullMinutes: 25, chargeLimitSoc: 90 })
    await mount('Tesla')

    expect(q('live-soc-target')).not.toBeNull()
    expect(q('live-metric-time')?.textContent).toContain('90')
  })

  it('stale only after twice the source cadence - a 4-minute-old Smartcar value is fresh', async () => {
    mockApi({ ...base, updateIntervalSeconds: 300, lastUpdatedAt: new Date(Date.now() - 240_000).toISOString() })
    await mount('VW')

    expect(document.body.textContent).not.toContain('Verbindung unterbrochen')
  })

  it('brand without live source: no request at all', async () => {
    mockApi(base)
    await mount('Zeekr')

    expect(api.get).not.toHaveBeenCalled()
  })
})
