// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { computed, createApp, defineComponent, h, nextTick, ref, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'

vi.mock('../../../api/axios', () => ({ default: { get: vi.fn(() => Promise.resolve({ data: { count: 0 } })) } }))
import StepCost from '../StepCost.vue'
import { useCostInput } from '../../../composables/useCostInput'

/**
 * Schritt 2: die Kostenzeile nimmt den Gesamtbetrag oder den Preis je kWh (Euroraum in ct).
 * Die Einheit rechts schaltet um; die Wahl bleibt für das nächste Mal. "Gratis" daneben.
 */
let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
beforeEach(() => { localStorage.clear(); i18n.global.locale.value = 'de' })

async function mount(opts: { providers?: any[]; providerId?: string } = {}) {
  const form = ref<any>({ kwhCharged: 30, kwhAtVehicle: null, costEur: null, costExchangeRate: null, costCurrency: null,
    chargingProviderId: opts.providerId ?? null, chargingType: 'AC', isPublicCharging: false, latitude: null, longitude: null, cpoName: null, applyTariffToLocation: false })
  const providers = ref<any[]>(opts.providers ?? [])
  const cost = useCostInput(form, { isEurCountry: computed(() => true), exchangeRate: computed(() => 1), localCurrency: computed(() => 'EUR') })
  app = createApp(defineComponent({ render: () => h(StepCost, {
    modelValue: form.value, 'onUpdate:modelValue': (v: any) => { form.value = v },
    providers: providers.value, 'onUpdate:providers': (v: any) => { providers.value = v },
    cost, compact: true, communityPrice: null }) }))
  app.use(i18n).use(createPinia())
  app.mount(document.body.appendChild(document.createElement('div')))
  await nextTick()
  const q = (id: string) => document.body.querySelector<HTMLElement>(`[data-testid="${id}"]`)
  const input = () => document.body.querySelector<HTMLInputElement>('#wizard-cost')!
  const perKwh = () => document.body.querySelector<HTMLInputElement>('#wizard-cost-per-kwh')!
  const typeInto = async (el: HTMLInputElement, v: string) => { el.value = v; el.dispatchEvent(new Event('input')); await nextTick() }
  const typeIn = (v: string) => typeInto(input(), v)
  return { form, q, input, perKwh, typeIn, typeInto }
}

describe('StepCost kompakt: Gesamt oder je kWh', () => {
  it('zwei Felder, beide sichtbar: 34 ct je kWh bei 30 kWh ergeben 10,20 €, das Gesamtfeld rechnet mit', async () => {
    const { form, q, input, perKwh, typeInto } = await mount()
    expect(q('cost-box')!.contains(input())).toBe(true)
    expect(q('cost-box')!.contains(perKwh())).toBe(true)
    expect(q('cost-unit')).toBeNull()
    await typeInto(perKwh(), '34')
    expect(form.value.costEur).toBe(10.2)
    expect(input().value).toBe('10.2')
    // Kein Sprung in die grüne Zeile mitten im Tippen
    expect(q('cost-derived')).toBeNull()
  })

  it('Gesamtbetrag getippt: das Feld je kWh rechnet mit (12 € bei 30 kWh = 40 ct)', async () => {
    const { form, perKwh, typeIn } = await mount()
    await typeIn('12')
    expect(form.value.costEur).toBe(12)
    expect(perKwh().value).toBe('40')
  })

  it('kein eigener Gratis-Knopf: 0 tippen speichert 0 € und bleibt im Feld', async () => {
    const { form, q, typeIn } = await mount()
    expect(q('cost-free')).toBeNull()
    await typeIn('0')
    expect(form.value.costEur).toBe(0)
    expect(q('cost-derived')).toBeNull()
  })

  it('Preis aus der Ladekarte: über "Ändern" lässt sich 0 eintippen - Kosten sind Pflicht, kein Deadlock', async () => {
    const card = { id: 'c1', providerName: 'EnBW', label: null, acPricePerKwh: 0.39, dcPricePerKwh: 0.59, isPrivate: false }
    const { form, q, typeIn } = await mount({ providers: [card], providerId: 'c1' })
    await nextTick()
    expect(q('cost-derived')).not.toBeNull()
    q('cost-other')!.click(); await nextTick()
    await typeIn('0')
    expect(form.value.costEur).toBe(0)
  })
})
