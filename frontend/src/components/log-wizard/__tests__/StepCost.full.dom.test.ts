// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { computed, createApp, defineComponent, h, nextTick, ref, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'

vi.mock('../../../api/axios', () => ({ default: { get: vi.fn(() => Promise.resolve({ data: { count: 0 } })) } }))
import StepCost from '../StepCost.vue'
import { useCostInput } from '../../../composables/useCostInput'

/** Bearbeiten-Dialog: derselbe Kartenstreifen wie im Wizard statt Text-Chips. */
let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
beforeEach(() => { localStorage.clear(); i18n.global.locale.value = 'de' })

const card = (id: string, acPrice: number | null) => ({ id, providerName: id, label: null, isPrivate: false, acPricePerKwh: acPrice, dcPricePerKwh: null })

async function mount(providers: any[]) {
  const form = ref<any>({ kwhCharged: 30, kwhAtVehicle: null, costEur: null, costExchangeRate: null, costCurrency: null,
    chargingProviderId: null, chargingType: 'AC', isPublicCharging: true, latitude: null, longitude: null, cpoName: null, applyTariffToLocation: false })
  const cost = useCostInput(form, { isEurCountry: computed(() => true), exchangeRate: computed(() => 1), localCurrency: computed(() => 'EUR') })
  app = createApp(defineComponent({ render: () => h(StepCost, {
    modelValue: form.value, 'onUpdate:modelValue': (v: any) => { form.value = v },
    providers, 'onUpdate:providers': () => {}, cost }) }))
  app.use(i18n).use(createPinia())
  app.mount(document.body.appendChild(document.createElement('div')))
  await nextTick()
  const q = (id: string) => document.body.querySelector<HTMLElement>(`[data-testid="${id}"]`)
  return { form, q }
}

describe('StepCost voll: Kartenstreifen', () => {
  it('Karte mit Tarif antippen: Karte gewählt, 30 kWh x 0,29 € = 8,70 €', async () => {
    const { form, q } = await mount([card('kaufland', 0.29)])
    expect(q('card-strip')).not.toBeNull()
    q('card-kaufland')!.click(); await nextTick()
    expect(form.value.chargingProviderId).toBe('kaufland')
    expect(form.value.costEur).toBe(8.7)
  })

  it('Karte ohne Tarif antippen: der Editor zum Nachtragen öffnet sich', async () => {
    const { form, q } = await mount([card('enbw', null)])
    q('card-enbw')!.click(); await nextTick()
    expect(form.value.chargingProviderId).toBe('enbw')
    expect(q('charging-card-prompt')).not.toBeNull()
  })

  it('Gratis antippen: 0 € ohne Karte', async () => {
    const { form } = await mount([card('kaufland', 0.29)])
    ;[...document.querySelectorAll<HTMLElement>('[data-testid="card-strip"] button')].find(b => b.textContent?.includes('Gratis'))!.click(); await nextTick()
    expect(form.value.costEur).toBe(0)
    expect(form.value.chargingProviderId).toBeNull()
  })

  it('Neue Karte antippen: der Editor öffnet sich', async () => {
    const { q } = await mount([])
    q('charging-card-prompt-open')!.click(); await nextTick()
    expect(q('charging-card-prompt')).not.toBeNull()
  })
})
