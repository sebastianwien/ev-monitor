import { describe, it, expect } from 'vitest'
import { ref, nextTick, computed } from 'vue'
import { useCostInput } from '../useCostInput'

const setup = (eur = true, rate = 1, currency = 'EUR') => {
  const form = ref<any>({ kwhCharged: 40, kwhAtVehicle: null, costEur: null, costExchangeRate: null, costCurrency: null })
  const cost = useCostInput(form, { isEurCountry: computed(() => eur), exchangeRate: computed(() => rate), localCurrency: computed(() => currency) })
  return { form, cost }
}

describe('useCostInput', () => {
  it('Gesamtbetrag landet gerundet in costEur', async () => {
    const { form, cost } = setup()
    cost.costLocalTotal.value = 29.615
    await nextTick()
    expect(form.value.costEur).toBe(29.62)
    expect(cost.calculatedLocalPerKwh.value).toBeCloseTo(0.74, 2)
  })

  it('Preis je kWh rechnet mit der Energie hoch', async () => {
    const { form, cost } = setup()
    cost.costMode.value = 'per_kwh'
    cost.costLocalPerKwh.value = 0.7
    await nextTick()
    expect(form.value.costEur).toBe(28)
    expect(cost.calculatedLocalTotal.value).toBe(28)
  })

  it('außerhalb der Eurozone wird umgerechnet und der Kurs gespeichert', async () => {
    const { form, cost } = setup(false, 11.2, 'NOK')
    cost.costLocalTotal.value = 112
    await nextTick()
    expect(form.value.costEur).toBe(10)
    expect(form.value.costExchangeRate).toBe(11.2)
    expect(form.value.costCurrency).toBe('NOK')
  })

  it('setPerKwhEur übernimmt einen EUR-Vorschlag in Landeswährung', async () => {
    const { form, cost } = setup(false, 11.2, 'NOK')
    cost.setPerKwhEur(0.5)
    await nextTick()
    expect(cost.costMode.value).toBe('per_kwh')
    expect(cost.costLocalPerKwh.value).toBeCloseTo(5.6, 3)
    expect(form.value.costEur).toBe(20)
  })

  it('Gesamtbetrag wächst mit, wenn die Energie später korrigiert wird', async () => {
    const { form, cost } = setup()
    cost.costLocalTotal.value = 20
    await nextTick()
    form.value.kwhCharged = 80
    await nextTick()
    expect(cost.costLocalTotal.value).toBe(40)
  })

  it('leere Eingabe setzt costEur zurück', async () => {
    const { form, cost } = setup()
    cost.costLocalTotal.value = 5
    await nextTick()
    cost.costLocalTotal.value = null
    await nextTick()
    expect(form.value.costEur).toBeNull()
  })
})
