// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, defineComponent, h, type App } from 'vue'
import { i18n } from '../../../i18n'
import CardStrip, { type CommunityPrice } from '../CardStrip.vue'
import type { ChargingProvider } from '../../../composables/useChargingProviders'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const card = (id: string, name: string): ChargingProvider => ({
  id, providerName: name, label: null, acPricePerKwh: 0.39, dcPricePerKwh: 0.59,
  monthlyFeeEur: 0, sessionFeeEur: 0, activeFrom: '2024-01-01', activeUntil: null, isPrivate: false,
})

function mount(community: CommunityPrice | null, selected: string | null) {
  const host = document.createElement('div'); document.body.appendChild(host)
  app = createApp(defineComponent({
    setup: () => () => h(CardStrip, {
      providers: [card('enbw', 'EnBW mobility+'), card('ionity', 'Ionity')], isPublic: true, chargingType: 'AC',
      community, selected, priceLabel: (e: number) => `${e} €`,
    }),
  }))
  app.use(i18n); app.mount(host)
  return host
}

describe('CardStrip', () => {
  it('letzter Preis mit bekannter Karte: die Karte ist markiert, keine extra Kachel "Zuletzt hier"', () => {
    const host = mount({ eurPerKwh: 0.42, providerId: 'enbw' }, 'enbw')
    expect(host.querySelectorAll('[aria-pressed]').length).toBe(3)
    expect(host.querySelector('[data-testid="card-enbw"]')?.getAttribute('aria-pressed')).toBe('true')
  })
  it('letzter Preis ohne Karte: Kachel "Zuletzt hier" steht vorn', () => {
    const host = mount({ eurPerKwh: 0.42, providerId: null }, 'community')
    const first = host.querySelector('[aria-pressed]')!
    expect(first.getAttribute('aria-pressed')).toBe('true')
    expect(first.textContent).toContain('0.42 €')
  })
})
