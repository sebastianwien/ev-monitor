import { ref, computed, watch, type Ref, type ComputedRef } from 'vue'
import { rescaleTotalToNewEnergy } from '../utils/costRescale'

/** Der Ausschnitt des Log-Formulars, den die Kosteneingabe liest und schreibt. */
export interface CostForm {
  kwhCharged: number | null
  kwhAtVehicle: number | null
  costEur: number | null
  costExchangeRate: number | null
  costCurrency: string | null
}

export interface CostCurrency {
  isEurCountry: ComputedRef<boolean>
  exchangeRate: ComputedRef<number>
  localCurrency: ComputedRef<string>
}

const round2 = (n: number) => Math.round(n * 100) / 100
const round3 = (n: number) => Math.round(n * 1000) / 1000

/**
 * Kosten in Landeswährung eingeben, gesamt oder je kWh - gespeichert wird immer EUR.
 *
 * Außerhalb der Eurozone wandert der Kurs mit ins Formular, damit die Eingabe beim
 * Bearbeiten exakt wieder herauskommt. Wird die Energie nachträglich korrigiert, wächst
 * ein eingegebener Gesamtbetrag mit (der Preis je kWh bleibt, was er war).
 */
export function useCostInput(form: Ref<CostForm>, currency: CostCurrency) {
  const costMode = ref<'total' | 'per_kwh'>('total')
  const costLocalTotal = ref<number | null>(null)
  const costLocalPerKwh = ref<number | null>(null)

  const localToEur = (local: number) => local / currency.exchangeRate.value
  const eurToLocal = (eur: number) => eur * currency.exchangeRate.value

  const effectiveKwh = computed<number | null>(() => form.value.kwhCharged ?? form.value.kwhAtVehicle)

  const calculatedLocalTotal = computed(() => {
    const kwh = effectiveKwh.value, price = costLocalPerKwh.value
    return kwh != null && price != null ? round2(kwh * price) : null
  })

  const calculatedLocalPerKwh = computed(() => {
    const kwh = effectiveKwh.value, total = costLocalTotal.value
    return kwh != null && kwh > 0 && total != null ? round3(total / kwh) : null
  })

  const syncCostToEur = () => {
    let eurValue: number | null = null
    if (costMode.value === 'total' && costLocalTotal.value != null) {
      eurValue = currency.isEurCountry.value ? costLocalTotal.value : localToEur(costLocalTotal.value)
    } else if (costMode.value === 'per_kwh' && costLocalPerKwh.value != null && effectiveKwh.value) {
      const localTotal = costLocalPerKwh.value * effectiveKwh.value
      eurValue = currency.isEurCountry.value ? localTotal : localToEur(localTotal)
    }
    form.value.costEur = eurValue != null ? round2(eurValue) : null
    const abroad = !currency.isEurCountry.value && eurValue != null
    form.value.costExchangeRate = abroad ? currency.exchangeRate.value : null
    form.value.costCurrency = abroad ? currency.localCurrency.value : null
  }

  // Nach initFromEur feuert der Watcher einmal ohne Nutzereingabe - dieser Lauf darf den
  // gespeicherten EUR-Betrag nicht mit dem heutigen Kurs ueberschreiben.
  let skipNextSync = false
  // Umschalten (switchMode) überspringt nur, solange die Energie im selben Lauf gleich bleibt.
  let skipToggleSync = false
  watch([costMode, costLocalTotal, costLocalPerKwh, effectiveKwh], (now, before) => {
    if (skipNextSync) { skipNextSync = false; return }
    if (skipToggleSync) { skipToggleSync = false; if (now[3] === before[3]) return }
    syncCostToEur()
  })

  watch(effectiveKwh, (newKwh, previousKwh) => {
    if (costMode.value !== 'total') return
    costLocalTotal.value = rescaleTotalToNewEnergy(costLocalTotal.value, previousKwh ?? null, newKwh)
  })

  /** Einen Vorschlag in EUR je kWh übernehmen - aus Ladekarte oder Community-Preis. */
  const setPerKwhEur = (eurPerKwh: number) => {
    costMode.value = 'per_kwh'
    costLocalTotal.value = null
    costLocalPerKwh.value = currency.isEurCountry.value ? eurPerKwh : round3(eurToLocal(eurPerKwh))
  }

  /**
   * Umschalten Gesamt / je kWh nimmt den Wert mit: das Zielfeld zeigt den abgeleiteten Wert.
   * Nur umgeschaltet heißt nichts geändert - costEur bleibt, und zurück steht wieder das
   * Original statt einer zweimal gerundeten Zahl (30,46 € -> 0,5 €/kWh -> 30,45 €).
   * `carried` hält den Stand des letzten Umschaltens; ändern Karte, Gratis, reset oder die Energie
   * etwas, passt er nicht mehr und das Zielfeld wird neu gerechnet.
   */
  let carried: { mode: 'total' | 'per_kwh'; value: number; source: number; kwh: number | null } | null = null
  const fieldOf = (mode: 'total' | 'per_kwh') => mode === 'total' ? costLocalTotal : costLocalPerKwh
  const switchMode = (mode: 'total' | 'per_kwh') => {
    const from = costMode.value
    if (mode === from) return
    const untouched = carried != null && carried.mode === from && fieldOf(from).value === carried.value
      && fieldOf(mode).value === carried.source && effectiveKwh.value === carried.kwh
    carried = null
    const derived = mode === 'per_kwh' ? calculatedLocalPerKwh.value : calculatedLocalTotal.value
    const source = fieldOf(from).value
    if (!untouched && derived != null && source != null) {
      fieldOf(mode).value = derived
      carried = { mode, value: derived, source, kwh: effectiveKwh.value }
      skipToggleSync = true
    }
    costMode.value = mode
  }

  /**
   * Beim Bearbeiten: den gespeicherten EUR-Betrag als Gesamtbetrag in Landeswährung zeigen.
   * Ein gespeicherter Kurs hat Vorrang, damit die damalige Eingabe exakt wieder erscheint.
   */
  const initFromEur = () => {
    if (form.value.costEur == null) return
    const rate = form.value.costExchangeRate ?? currency.exchangeRate.value
    const local = currency.isEurCountry.value ? form.value.costEur : form.value.costEur * rate
    costMode.value = 'total'
    costLocalTotal.value = round2(local)
    skipNextSync = true
  }

  const reset = () => {
    costMode.value = 'total'
    costLocalTotal.value = null
    costLocalPerKwh.value = null
  }

  return {
    costMode, costLocalTotal, costLocalPerKwh,
    calculatedLocalTotal, calculatedLocalPerKwh,
    setPerKwhEur, switchMode, initFromEur, reset, eurToLocal,
  }
}
