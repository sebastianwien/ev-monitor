import { computed, ref } from 'vue'
import { useCarStore } from '../stores/car'
import { carService, type BrandInfo, type CapacityOption, type ModelInfo } from '../api/carService'
import { groupCapacitiesByTrim } from './useCarForm'

/**
 * Der verkuerzte Weg zum ersten Fahrzeug: Marke, Modell, Variante, Baujahr - mehr nicht.
 * Kennzeichen, Waermepumpe und Degradation fragt das Onboarding bewusst nicht ab, die
 * stehen spaeter in den Fahrzeug-Einstellungen. Die Logik liegt hier statt in der
 * Komponente, damit der Wizard-Footer den Fortschritt kennt (Primary aktiv oder nicht).
 */
export type CarAddPhase = 'brand-select' | 'model-select' | 'trim-select' | 'year-input' | 'success'

/** Aeltere Fahrzeuge gibt es kaum, und die Liste bleibt so ueberschaubar. */
export const MIN_CAR_YEAR = 2010

/** Vorauswahl ohne Suche - deckt den Grossteil der Neuanmeldungen ab. */
export const TOP_BRANDS = [
  'TESLA', 'VOLKSWAGEN', 'BMW', 'AUDI', 'MERCEDES_BENZ', 'HYUNDAI',
  'KIA', 'SKODA', 'POLESTAR', 'RENAULT', 'VOLVO', 'PORSCHE',
]

export function useCarQuickAdd() {
  const carStore = useCarStore()

  const phase = ref<CarAddPhase>('brand-select')
  const searchQuery = ref('')

  const brands = ref<BrandInfo[]>([])
  const brandsLoading = ref(false)
  // Ohne eigenen Fehlerzustand zeigt die leere Liste "keine Marke gefunden" - eine falsche
  // Aussage, aus der es kein Zurueck gibt. Der Ladefehler bekommt deshalb sein eigenes Flag.
  const brandsFailed = ref(false)
  const models = ref<ModelInfo[]>([])
  const modelsLoading = ref(false)
  const modelsFailed = ref(false)

  const selectedBrand = ref<BrandInfo | null>(null)
  const selectedModel = ref<ModelInfo | null>(null)
  const selectedTrimLevel = ref<string | null>(null)
  const selectedSpec = ref<CapacityOption | null>(null)
  const year = ref(new Date().getFullYear())

  const creating = ref(false)
  const failed = ref(false)
  /** Begruendung des Servers, falls er eine mitliefert - sonst uebersetzt die Komponente generisch. */
  const errorMessage = ref<string | null>(null)
  const created = ref(false)

  const filteredBrands = computed(() => {
    const q = searchQuery.value.trim().toLowerCase()
    if (!q) return brands.value.filter(b => TOP_BRANDS.includes(b.value))
    return brands.value.filter(b => b.label.toLowerCase().includes(q))
  })

  const trimGroups = computed(() =>
    selectedModel.value ? groupCapacitiesByTrim(selectedModel.value.capacities) : [])

  const isGrouped = computed(() => trimGroups.value.length > 0)

  const specsForTrim = computed(() => {
    if (!selectedTrimLevel.value) return []
    return trimGroups.value.find(g => g.trimLevel === selectedTrimLevel.value)?.options ?? []
  })

  /** Die Liste, die in der Trim-Phase gerade dransteht: Gruppen oder deren Kapazitaeten. */
  const visibleSpecs = computed(() =>
    selectedTrimLevel.value ? specsForTrim.value : selectedModel.value?.capacities ?? [])

  const canCreate = computed(() => phase.value === 'year-input' && selectedSpec.value != null)
  const canDecreaseYear = computed(() => year.value > MIN_CAR_YEAR)
  const canIncreaseYear = computed(() => year.value < new Date().getFullYear())

  const loadBrands = async () => {
    if (brands.value.length > 0 || brandsLoading.value) return
    brandsLoading.value = true
    brandsFailed.value = false
    try {
      brands.value = await carStore.getBrands()
    } catch {
      brandsFailed.value = true
    } finally {
      brandsLoading.value = false
    }
  }

  const loadModels = async (brandValue: string) => {
    modelsLoading.value = true
    modelsFailed.value = false
    try {
      models.value = await carStore.getModelsForBrand(brandValue)
    } catch {
      modelsFailed.value = true
    } finally {
      modelsLoading.value = false
    }
  }

  const selectBrand = async (brand: BrandInfo) => {
    selectedBrand.value = brand
    phase.value = 'model-select'
    await loadModels(brand.value)
  }

  /** Nach einem Ladefehler erneut versuchen, ohne die Markenauswahl zu verlieren. */
  const retryModels = async () => {
    if (selectedBrand.value) await loadModels(selectedBrand.value.value)
  }

  const selectModel = (model: ModelInfo) => {
    selectedModel.value = model
    selectedTrimLevel.value = null
    selectedSpec.value = null
    // Bei genau einer Kapazitaet gibt es nichts zu waehlen - eine Frage weniger.
    if (model.capacities.length === 1) {
      selectedSpec.value = model.capacities[0]
      phase.value = 'year-input'
    } else {
      phase.value = 'trim-select'
    }
  }

  const selectTrim = (trimLevel: string) => {
    selectedTrimLevel.value = trimLevel
    const options = specsForTrim.value
    if (options.length === 1) {
      selectedSpec.value = options[0]
      phase.value = 'year-input'
    }
  }

  const selectSpec = (option: CapacityOption) => {
    selectedSpec.value = option
    phase.value = 'year-input'
  }

  /** Ein Schritt zurueck - innerhalb der Trim-Phase erst von den Kapazitaeten zu den Gruppen. */
  const back = () => {
    failed.value = false
    errorMessage.value = null
    if (phase.value === 'year-input') {
      phase.value = isGrouped.value || (selectedModel.value?.capacities.length ?? 0) > 1
        ? 'trim-select'
        : 'model-select'
      return
    }
    if (phase.value === 'trim-select') {
      if (selectedTrimLevel.value) {
        selectedTrimLevel.value = null
        return
      }
      phase.value = 'model-select'
      return
    }
    if (phase.value === 'model-select') {
      searchQuery.value = ''
      phase.value = 'brand-select'
    }
  }

  const decreaseYear = () => { if (canDecreaseYear.value) year.value-- }
  const increaseYear = () => { if (canIncreaseYear.value) year.value++ }

  const createCar = async () => {
    if (!canCreate.value || creating.value) return
    creating.value = true
    failed.value = false
    errorMessage.value = null
    try {
      await carService.createCar({
        model: selectedModel.value!.value,
        year: year.value,
        licensePlate: '',
        trim: null,
        customNetCapacityKwh: selectedSpec.value!.kWh,
        powerKw: null,
        batteryDegradationPercent: null,
        hasHeatPump: false,
        vehicleSpecificationId: selectedSpec.value!.vehicleSpecificationId,
      })
      carStore.invalidateCars()
      created.value = true
      phase.value = 'success'
    } catch (e: unknown) {
      failed.value = true
      errorMessage.value = (e as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? null
    } finally {
      creating.value = false
    }
  }

  return {
    phase, searchQuery, brands, brandsLoading, models, modelsLoading,
    brandsFailed, modelsFailed,
    selectedBrand, selectedModel, selectedTrimLevel, selectedSpec, year,
    creating, failed, errorMessage, created,
    filteredBrands, trimGroups, isGrouped, specsForTrim, visibleSpecs,
    canCreate, canDecreaseYear, canIncreaseYear,
    loadBrands, retryModels, selectBrand, selectModel, selectTrim, selectSpec, back,
    decreaseYear, increaseYear, createCar,
  }
}
