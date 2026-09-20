import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { BrandInfo, CapacityOption, ModelInfo } from '../../api/carService'

const carStore = vi.hoisted(() => ({
  getBrands: vi.fn(),
  getModelsForBrand: vi.fn(),
  invalidateCars: vi.fn(),
}))
vi.mock('../../stores/car', () => ({ useCarStore: () => carStore }))

const carService = vi.hoisted(() => ({ createCar: vi.fn() }))
vi.mock('../../api/carService', async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  carService,
}))

import { useCarQuickAdd, MIN_CAR_YEAR, TOP_BRANDS } from '../useCarQuickAdd'

const brand = (value: string, label: string): BrandInfo => ({ value, label })

const spec = (kWh: number, over: Partial<CapacityOption> = {}): CapacityOption => ({
  kWh,
  variantName: null,
  vehicleSpecificationId: `spec-${kWh}`,
  trimLevel: null,
  availableFrom: null,
  availableTo: null,
  ...over,
})

const model = (value: string, capacities: CapacityOption[]): ModelInfo => ({
  value, label: value, capacities,
})

const BRANDS = [
  brand('TESLA', 'Tesla'),
  brand('VOLKSWAGEN', 'Volkswagen'),
  brand('AIWAYS', 'Aiways'),
]

describe('useCarQuickAdd: Marken', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    carStore.getBrands.mockResolvedValue(BRANDS)
  })

  it('zeigt ohne Suche nur die gaengigen Marken', async () => {
    const q = useCarQuickAdd()
    await q.loadBrands()

    expect(q.filteredBrands.value.map(b => b.value)).toEqual(['TESLA', 'VOLKSWAGEN'])
    expect(TOP_BRANDS).not.toContain('AIWAYS')
  })

  it('durchsucht bei einer Eingabe alle Marken, nicht nur die gaengigen', async () => {
    const q = useCarQuickAdd()
    await q.loadBrands()
    q.searchQuery.value = 'aiw'

    expect(q.filteredBrands.value.map(b => b.value)).toEqual(['AIWAYS'])
  })

  it('ignoriert Gross-/Kleinschreibung und Leerzeichen in der Suche', async () => {
    const q = useCarQuickAdd()
    await q.loadBrands()
    q.searchQuery.value = '  TESL '

    expect(q.filteredBrands.value.map(b => b.value)).toEqual(['TESLA'])
  })

  it('laedt die Marken nur einmal, auch bei mehrfachem Aufruf', async () => {
    const q = useCarQuickAdd()
    await q.loadBrands()
    await q.loadBrands()

    expect(carStore.getBrands).toHaveBeenCalledOnce()
  })

  it('haelt brandsLoading nur waehrend des Ladens auf true', async () => {
    const q = useCarQuickAdd()
    const pending = q.loadBrands()
    expect(q.brandsLoading.value).toBe(true)
    await pending
    expect(q.brandsLoading.value).toBe(false)
  })

  it('meldet einen Ladefehler, statt die Liste leer zu lassen', async () => {
    // Ohne eigenen Fehlerzustand sieht der User "keine Marke gefunden" - eine falsche Aussage.
    carStore.getBrands.mockRejectedValue(new Error('offline'))
    const q = useCarQuickAdd()

    await expect(q.loadBrands()).resolves.toBeUndefined()

    expect(q.brandsFailed.value).toBe(true)
    expect(q.brandsLoading.value).toBe(false)
  })

  it('laesst einen gescheiterten Ladeversuch wiederholen', async () => {
    carStore.getBrands.mockRejectedValueOnce(new Error('offline'))
    const q = useCarQuickAdd()
    await q.loadBrands()

    carStore.getBrands.mockResolvedValue(BRANDS)
    await q.loadBrands()

    expect(q.brandsFailed.value).toBe(false)
    expect(q.filteredBrands.value).toHaveLength(2)
  })
})

describe('useCarQuickAdd: Modellauswahl', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    carStore.getBrands.mockResolvedValue(BRANDS)
  })

  it('laedt nach der Marke die Modelle und wechselt die Phase', async () => {
    carStore.getModelsForBrand.mockResolvedValue([model('MODEL_3', [spec(60)])])
    const q = useCarQuickAdd()

    await q.selectBrand(BRANDS[0])

    expect(carStore.getModelsForBrand).toHaveBeenCalledWith('TESLA')
    expect(q.phase.value).toBe('model-select')
    expect(q.models.value).toHaveLength(1)
  })

  it('meldet einen Fehler beim Laden der Modelle', async () => {
    carStore.getModelsForBrand.mockRejectedValue(new Error('offline'))
    const q = useCarQuickAdd()

    await expect(q.selectBrand(BRANDS[0])).resolves.toBeUndefined()

    expect(q.modelsFailed.value).toBe(true)
    expect(q.modelsLoading.value).toBe(false)
    expect(q.phase.value).toBe('model-select')
  })

  it('laedt die Modelle nach einem Fehler erneut', async () => {
    carStore.getModelsForBrand.mockRejectedValueOnce(new Error('offline'))
    const q = useCarQuickAdd()
    await q.selectBrand(BRANDS[0])

    carStore.getModelsForBrand.mockResolvedValue([model('MODEL_3', [spec(60)])])
    await q.retryModels()

    expect(q.modelsFailed.value).toBe(false)
    expect(q.models.value).toHaveLength(1)
  })

  it('ueberspringt die Variante, wenn das Modell nur eine Kapazitaet hat', () => {
    const q = useCarQuickAdd()
    const only = spec(60)

    q.selectModel(model('MODEL_3', [only]))

    expect(q.phase.value).toBe('year-input')
    expect(q.selectedSpec.value).toEqual(only)
  })

  it('fragt bei mehreren Kapazitaeten nach der Variante', () => {
    const q = useCarQuickAdd()

    q.selectModel(model('MODEL_3', [spec(60), spec(75)]))

    expect(q.phase.value).toBe('trim-select')
    expect(q.selectedSpec.value).toBe(null)
  })

  it('springt nach einer Trim-Gruppe mit nur einer Kapazitaet direkt zum Baujahr', () => {
    const q = useCarQuickAdd()
    const long = spec(75, { trimLevel: 'Long Range' })
    q.selectModel(model('MODEL_3', [spec(60, { trimLevel: 'Standard' }), long]))

    q.selectTrim('Long Range')

    expect(q.phase.value).toBe('year-input')
    expect(q.selectedSpec.value).toEqual(long)
  })

  it('bleibt in der Trim-Auswahl, solange eine Gruppe mehrere Kapazitaeten hat', () => {
    const q = useCarQuickAdd()
    q.selectModel(model('MODEL_3', [
      spec(75, { trimLevel: 'Long Range' }),
      spec(79, { trimLevel: 'Long Range' }),
    ]))

    q.selectTrim('Long Range')

    expect(q.phase.value).toBe('trim-select')
    expect(q.specsForTrim.value).toHaveLength(2)
  })

  it('setzt eine zuvor gewaehlte Variante zurueck, wenn das Modell wechselt', () => {
    const q = useCarQuickAdd()
    q.selectModel(model('MODEL_3', [spec(60)]))
    expect(q.selectedSpec.value).not.toBe(null)

    q.selectModel(model('MODEL_Y', [spec(60), spec(75)]))

    expect(q.selectedSpec.value).toBe(null)
    expect(q.selectedTrimLevel.value).toBe(null)
  })
})

describe('useCarQuickAdd: Zurueck', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fuehrt Schritt fuer Schritt zurueck bis zur Marke', () => {
    const q = useCarQuickAdd()
    q.selectModel(model('MODEL_3', [
      spec(75, { trimLevel: 'Long Range' }),
      spec(79, { trimLevel: 'Long Range' }),
    ]))
    q.selectTrim('Long Range')
    q.selectSpec(spec(79, { trimLevel: 'Long Range' }))
    expect(q.phase.value).toBe('year-input')

    // Zurueck landet wieder in der Kapazitaetsliste der Gruppe, nicht bei den Gruppen
    q.back()
    expect(q.phase.value).toBe('trim-select')
    expect(q.selectedTrimLevel.value).toBe('Long Range')

    q.back()
    expect(q.phase.value).toBe('trim-select')
    expect(q.selectedTrimLevel.value).toBe(null)

    q.back()
    expect(q.phase.value).toBe('model-select')

    q.back()
    expect(q.phase.value).toBe('brand-select')
  })

  it('geht vom Baujahr direkt zum Modell zurueck, wenn es keine Varianten gab', () => {
    const q = useCarQuickAdd()
    q.selectModel(model('MODEL_3', [spec(60)]))

    q.back()

    expect(q.phase.value).toBe('model-select')
  })

  it('leert die Suche beim Zurueck auf die Markenliste', async () => {
    carStore.getBrands.mockResolvedValue(BRANDS)
    carStore.getModelsForBrand.mockResolvedValue([])
    const q = useCarQuickAdd()
    await q.selectBrand(BRANDS[0])
    q.searchQuery.value = 'tesl'

    q.back()

    expect(q.searchQuery.value).toBe('')
  })
})

describe('useCarQuickAdd: Baujahr', () => {
  const thisYear = new Date().getFullYear()

  it('startet im aktuellen Jahr und laesst sich nicht darueber hinaus erhoehen', () => {
    const q = useCarQuickAdd()
    expect(q.year.value).toBe(thisYear)

    q.increaseYear()

    expect(q.year.value).toBe(thisYear)
  })

  it('laesst sich nicht unter das Mindestjahr senken', () => {
    const q = useCarQuickAdd()
    for (let i = 0; i <= thisYear - MIN_CAR_YEAR + 5; i++) q.decreaseYear()

    expect(q.year.value).toBe(MIN_CAR_YEAR)
  })
})

describe('useCarQuickAdd: Anlegen', () => {
  beforeEach(() => vi.clearAllMocks())

  async function atYearInput() {
    const q = useCarQuickAdd()
    q.selectModel(model('MODEL_3', [spec(60)]))
    return q
  }

  it('legt das Auto mit der gewaehlten Spezifikation an', async () => {
    carService.createCar.mockResolvedValue({ id: 'car-1' })
    const q = await atYearInput()
    q.decreaseYear()

    await q.createCar()

    expect(carService.createCar).toHaveBeenCalledWith(expect.objectContaining({
      model: 'MODEL_3',
      year: new Date().getFullYear() - 1,
      customNetCapacityKwh: 60,
      vehicleSpecificationId: 'spec-60',
    }))
    expect(q.phase.value).toBe('success')
    expect(q.created.value).toBe(true)
  })

  it('verwirft den Fahrzeug-Cache, damit das neue Auto ueberall auftaucht', async () => {
    carService.createCar.mockResolvedValue({ id: 'car-1' })
    const q = await atYearInput()

    await q.createCar()

    expect(carStore.invalidateCars).toHaveBeenCalledOnce()
  })

  it('bleibt beim Baujahr stehen und meldet den Serverfehler', async () => {
    carService.createCar.mockRejectedValue({ response: { data: { message: 'Modell unbekannt' } } })
    const q = await atYearInput()

    await q.createCar()

    expect(q.phase.value).toBe('year-input')
    expect(q.failed.value).toBe(true)
    expect(q.errorMessage.value).toBe('Modell unbekannt')
    expect(q.created.value).toBe(false)
  })

  it('laesst errorMessage leer, wenn der Server keine Begruendung liefert', async () => {
    carService.createCar.mockRejectedValue(new Error('Network Error'))
    const q = await atYearInput()

    await q.createCar()

    expect(q.failed.value).toBe(true)
    expect(q.errorMessage.value).toBe(null)
  })

  it('legt ohne gewaehlte Variante nichts an', async () => {
    const q = useCarQuickAdd()

    await q.createCar()

    expect(carService.createCar).not.toHaveBeenCalled()
  })

  it('verhindert doppeltes Anlegen bei einem zweiten Klick', async () => {
    let release!: (v: unknown) => void
    carService.createCar.mockReturnValue(new Promise(r => { release = r }))
    const q = await atYearInput()

    const first = q.createCar()
    expect(q.creating.value).toBe(true)
    await q.createCar()
    release({ id: 'car-1' })
    await first

    expect(carService.createCar).toHaveBeenCalledOnce()
  })
})
