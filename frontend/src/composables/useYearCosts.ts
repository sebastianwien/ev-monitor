import { computed, ref, watch } from 'vue'
import api from '../api/axios'
import { fixedCostService, type FixedCost } from '../api/fixedCostService'
import type { StatisticsData } from './useDashboardStats'
import type { FixedCostMode } from './useFixedCostCalc'
import { buildYearCostSeries, firstCostIndex, rankCategories } from '../utils/carCostSummary'
import { readLocal, writeLocal } from '../utils/safeStorage'

// Gemeinsame Schluessel fuer Dashboard-Kostenverlauf und Fahrzeugseite.
const LS_MODE = 'cost_history_mode'
const LS_HIDDEN = 'car_cost_hidden'
/** Schluessel fuer Strom in der Liste ausgeblendeter Kategorien. */
export const ENERGY = 'ENERGY'

function parseHidden(raw: string | null): string[] {
  try {
    const v = JSON.parse(raw ?? '[]')
    return Array.isArray(v) ? v.filter(x => typeof x === 'string') : []
  } catch {
    return []
  }
}

/**
 * Kosten eines Autos je Kalenderjahr: Strom aus `/logs/statistics`, Fixkosten aus der
 * Fixkostenliste. Haelt Jahr, Umlagemodus und ausgeblendete Kategorien (beide im Browser
 * gemerkt) und liefert die Monatsreihe ab dem ersten Monat mit Kosten.
 */
export function useYearCosts(carId: () => string | null, registrationDate: () => string | null = () => null) {
  const thisYear = new Date().getFullYear()
  const year = ref(thisYear)
  /** Jahr der geladenen Daten. Folgt `year` erst nach dem Laden, damit nie alte Monate unter neuer Jahreszahl stehen. */
  const dataYear = ref(thisYear)
  const points = ref<StatisticsData['chargesOverTime']>([])
  const items = ref<FixedCost[]>([])
  const loading = ref(true)
  const error = ref(false)

  const mode = ref<FixedCostMode>(readLocal(LS_MODE) === 'due_month' ? 'due_month' : 'pro_rata')
  const spread = computed({
    get: () => mode.value === 'pro_rata',
    set: (on: boolean) => {
      mode.value = on ? 'pro_rata' : 'due_month'
      writeLocal(LS_MODE, mode.value)
    },
  })
  const hidden = ref<Set<string>>(new Set(parseHidden(readLocal(LS_HIDDEN))))
  function setShown(key: string, on: boolean) {
    const next = new Set(hidden.value)
    if (on) next.delete(key)
    else next.add(key)
    hidden.value = next
    writeLocal(LS_HIDDEN, JSON.stringify([...next]))
  }

  async function loadStats() {
    const id = carId()
    const y = year.value
    if (!id) return
    const res = await api.get<StatisticsData>('/logs/statistics', {
      params: { carId: id, startDate: `${y}-01-01`, endDate: `${y}-12-31`, groupBy: 'MONTH' },
    })
    // Schnelles Weiterklicken: nur die Antwort zum zuletzt gewaehlten Jahr uebernehmen.
    if (y !== year.value || id !== carId()) return
    points.value = res.data.chargesOverTime ?? []
    dataYear.value = y
  }
  async function load() {
    const id = carId()
    if (!id) return
    loading.value = true
    error.value = false
    try {
      const [, list] = await Promise.all([loadStats(), fixedCostService.list(id)])
      items.value = list
    } catch {
      error.value = true
    } finally {
      loading.value = false
    }
  }
  watch(carId, () => {
    year.value = thisYear
    load()
  }, { immediate: true })
  watch(year, async () => {
    error.value = false
    try { await loadStats() } catch { error.value = true }
  })

  // Fruehestes waehlbares Jahr: Erstzulassung oder erste Fixkosten, sonst das laufende Jahr.
  const minYear = computed(() => {
    const years = [thisYear]
    const reg = registrationDate()
    if (reg) years.push(Number(reg.slice(0, 4)))
    for (const fc of items.value) {
      const d = fc.date ?? fc.startDate
      if (d) years.push(Number(d.slice(0, 4)))
    }
    return Math.min(...years.filter(Number.isFinite))
  })

  const shownPoints = computed(() => (hidden.value.has(ENERGY) ? [] : points.value))
  const shownItems = computed(() => items.value.filter(fc => !hidden.value.has(fc.category)))
  // Monate vor den ersten Kosten des Jahres fallen weg: da gab es das Auto noch nicht. Bestimmt
  // ohne Filter und mit umgelegten Fixkosten, damit Schalter die Monate nicht verschieben.
  const start = computed(() => firstCostIndex(buildYearCostSeries(points.value, items.value, 'pro_rata', dataYear.value)))
  const inRange = <T>(list: T[]) => (start.value < 0 ? [] : list.slice(start.value))

  /** Monatsreihe im gewaehlten Modus, ausgeblendete Kategorien fehlen. */
  const series = computed(() => inRange(buildYearCostSeries(shownPoints.value, shownItems.value, mode.value, dataYear.value)))
  /** Dieselbe Reihe ohne Filter, fuer Legende und Farbstufen, die beim Ausblenden stabil bleiben. */
  const allSeries = computed(() => inRange(buildYearCostSeries(points.value, items.value, mode.value, dataYear.value)))
  /** Mit umgelegten Fixkosten: Grundlage fuer Monatsdurchschnitte, unabhaengig vom Schalter. */
  const proRataSeries = computed(() => inRange(buildYearCostSeries(shownPoints.value, shownItems.value, 'pro_rata', dataYear.value)))

  /** Kostenkategorien nach Jahressumme (umgelegt, ungefiltert): Farbstufen bleiben bei Schaltern stabil. */
  const ranked = computed(() => rankCategories(inRange(buildYearCostSeries(points.value, items.value, 'pro_rata', dataYear.value))))

  return { ranked, thisYear, year, dataYear, minYear, points, items, loading, error, load, spread, hidden, setShown, series, allSeries, proRataSeries }
}
