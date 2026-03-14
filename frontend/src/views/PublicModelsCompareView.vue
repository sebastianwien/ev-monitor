<template>
  <div class="min-h-screen bg-gray-50">
    <PublicNav />

    <main class="max-w-6xl mx-auto px-4 py-8">
      <!-- Breadcrumb -->
      <nav class="text-sm text-gray-500 mb-6">
        <a href="/modelle" class="hover:text-gray-700">Modelle</a>
        <span class="mx-2">›</span>
        <span class="text-gray-900">Vergleich</span>
      </nav>

      <!-- Loading -->
      <div v-if="loading" class="flex items-center justify-center py-20">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-green-600"></div>
      </div>

      <!-- Error / invalid params -->
      <div v-else-if="error || validModels.length < 2" class="text-center py-20">
        <TruckIcon class="h-16 w-16 text-gray-300 mx-auto mb-4" />
        <h1 class="text-xl font-bold text-gray-800 mb-2">Vergleich nicht möglich</h1>
        <p class="text-gray-500 mb-6">{{ error || 'Bitte mindestens 2 Modelle auswählen.' }}</p>
        <a href="/modelle" class="bg-green-600 text-white px-6 py-3 rounded-lg hover:bg-green-700">
          Zur Modellübersicht
        </a>
      </div>

      <template v-else>
        <!-- Header -->
        <div class="bg-white rounded-2xl border border-gray-200 p-6 mb-6">
          <h1 class="text-2xl font-bold text-gray-900 mb-1">
            {{ validModels.map(m => m.modelDisplayName).join(' vs. ') }}
          </h1>
          <p class="text-gray-500 text-sm">
            Reale Community-Daten im direkten Vergleich – WLTP vs. Praxis
          </p>
          <a href="/modelle" class="inline-flex items-center gap-1.5 text-sm text-green-600 hover:underline mt-3">
            <ArrowLeftIcon class="h-4 w-4" />
            Andere Modelle wählen
          </a>
        </div>

        <!-- Comparison Table -->
        <div class="bg-white rounded-2xl border border-gray-200 overflow-hidden mb-6">
          <div class="overflow-x-auto">
            <table class="w-full min-w-[480px] text-sm">
              <!-- Model header row -->
              <thead>
                <tr class="border-b-2 border-gray-100">
                  <th class="sticky left-0 bg-white z-10 w-36 py-4 px-4 text-left font-normal text-gray-400"></th>
                  <th v-for="m in validModels" :key="m.brand + m.model"
                      class="py-4 px-3 text-center"
                      :class="m.logCount === 0 ? 'opacity-60' : ''">
                    <a :href="`/modelle/${m.brand}/${m.model}`"
                       class="font-bold text-gray-900 hover:text-green-600 transition-colors block">
                      {{ m.modelDisplayName }}
                    </a>
                    <!-- Real data badge -->
                    <span v-if="m.logCount > 0"
                          class="inline-flex items-center gap-1 mt-1 text-xs px-2 py-0.5 rounded-full bg-green-50 border border-green-200 text-green-700 font-normal">
                      <ChartBarIcon class="h-3 w-3" />
                      Community-Daten
                    </span>
                    <!-- WLTP-only badge — same muted look as fallback cards -->
                    <span v-else
                          class="inline-flex items-center gap-1 mt-1 text-xs px-2 py-0.5 rounded-full bg-gray-100 border border-gray-200 text-gray-400 font-normal">
                      Nur WLTP
                    </span>
                  </th>
                </tr>
              </thead>

              <tbody>
                <!-- ── Block 1: Verbrauch & Effizienz ───────────────────── -->
                <tr class="bg-gray-50">
                  <td :colspan="validModels.length + 1" class="sticky left-0 px-4 py-2 text-xs font-semibold text-gray-500 uppercase tracking-wide">
                    Verbrauch & Effizienz
                  </td>
                </tr>
                <!-- Real consumption -->
                <tr class="border-t border-gray-50 hover:bg-gray-50 transition-colors">
                  <td class="sticky left-0 bg-white z-10 px-4 py-3 text-gray-600 font-medium whitespace-nowrap">
                    Ø Real (kWh/100km)
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center" :class="cellClass(i, realConsumptions)">
                    {{ validModels[i].avgConsumptionKwhPer100km ? validModels[i].avgConsumptionKwhPer100km.toFixed(1) : '–' }}
                  </td>
                </tr>
                <!-- WLTP consumption -->
                <tr class="border-t border-gray-50 hover:bg-gray-50 transition-colors">
                  <td class="sticky left-0 bg-white z-10 px-4 py-3 text-gray-600 font-medium whitespace-nowrap">
                    WLTP (kWh/100km)
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center text-gray-700">
                    {{ wltpConsumptions[i] ? wltpConsumptions[i]!.toFixed(1) : '–' }}
                  </td>
                </tr>
                <!-- Delta WLTP -->
                <tr class="border-t border-gray-50 hover:bg-gray-50 transition-colors">
                  <td class="sticky left-0 bg-white z-10 px-4 py-3 text-gray-600 font-medium whitespace-nowrap">
                    Abweichung WLTP
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center">
                    <span v-if="wltpDeltas[i] !== null"
                          :class="deltaChipClass(wltpDeltas[i]!)"
                          class="text-xs px-2 py-0.5 rounded-full font-medium">
                      {{ wltpDeltas[i]! > 0 ? '+' : '' }}{{ wltpDeltas[i]!.toFixed(1) }}%
                    </span>
                    <span v-else class="text-gray-400">–</span>
                  </td>
                </tr>

                <!-- ── Block 2: Reichweite ──────────────────────────────── -->
                <tr class="bg-gray-50">
                  <td :colspan="validModels.length + 1" class="sticky left-0 px-4 py-2 text-xs font-semibold text-gray-500 uppercase tracking-wide">
                    Reichweite
                  </td>
                </tr>
                <tr class="border-t border-gray-50 hover:bg-gray-50 transition-colors">
                  <td class="sticky left-0 bg-white z-10 px-4 py-3 text-gray-600 font-medium whitespace-nowrap">
                    <div>Praxis (90→10%)</div>
                    <div class="text-xs text-gray-400 font-normal">bei realem Verbrauch</div>
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center" :class="cellClass(i, realRanges, false)">
                    {{ realRanges[i] ? realRanges[i] + ' km' : '–' }}
                  </td>
                </tr>
                <tr class="border-t border-gray-50 hover:bg-gray-50 transition-colors">
                  <td class="sticky left-0 bg-white z-10 px-4 py-3 text-gray-600 font-medium whitespace-nowrap">
                    WLTP Reichweite
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center text-gray-700">
                    {{ wltpRanges[i] ? wltpRanges[i] + ' km' : '–' }}
                  </td>
                </tr>

                <!-- ── Block 3: Kosten ──────────────────────────────────── -->
                <tr class="bg-gray-50">
                  <td :colspan="validModels.length + 1" class="sticky left-0 px-4 py-2 text-xs font-semibold text-gray-500 uppercase tracking-wide">
                    Kosten
                  </td>
                </tr>
                <tr class="border-t border-gray-50 hover:bg-gray-50 transition-colors">
                  <td class="sticky left-0 bg-white z-10 px-4 py-3 text-gray-600 font-medium whitespace-nowrap">
                    Ø Kosten / 100km
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center" :class="cellClass(i, costsPerHundred)">
                    {{ costsPerHundred[i] ? costsPerHundred[i]!.toFixed(2) + ' €' : '–' }}
                  </td>
                </tr>
                <tr class="border-t border-gray-50 hover:bg-gray-50 transition-colors">
                  <td class="sticky left-0 bg-white z-10 px-4 py-3 text-gray-600 font-medium whitespace-nowrap">
                    Ø Ladepreis / kWh
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center" :class="cellClass(i, costsPerKwh)">
                    {{ costsPerKwh[i] ? costsPerKwh[i]!.toFixed(1) + ' ct' : '–' }}
                  </td>
                </tr>

                <!-- ── Block 4: Saisonal ────────────────────────────────── -->
                <tr class="bg-gray-50">
                  <td :colspan="validModels.length + 1" class="sticky left-0 px-4 py-2 text-xs font-semibold text-gray-500 uppercase tracking-wide">
                    Saisonal
                  </td>
                </tr>
                <tr class="border-t border-gray-50 hover:bg-gray-50 transition-colors">
                  <td class="sticky left-0 bg-white z-10 px-4 py-3 text-gray-600 font-medium whitespace-nowrap">
                    🌞 Sommer (kWh/100km)
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center" :class="cellClass(i, summerConsumptions)">
                    {{ summerConsumptions[i] ? summerConsumptions[i]!.toFixed(1) : '–' }}
                  </td>
                </tr>
                <tr class="border-t border-gray-50 hover:bg-gray-50 transition-colors">
                  <td class="sticky left-0 bg-white z-10 px-4 py-3 text-gray-600 font-medium whitespace-nowrap">
                    ❄️ Winter (kWh/100km)
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center" :class="cellClass(i, winterConsumptions)">
                    {{ winterConsumptions[i] ? winterConsumptions[i]!.toFixed(1) : '–' }}
                  </td>
                </tr>

                <!-- ── Block 5: Datenbasis ──────────────────────────────── -->
                <tr class="bg-gray-50">
                  <td :colspan="validModels.length + 1" class="sticky left-0 px-4 py-2 text-xs font-semibold text-gray-500 uppercase tracking-wide">
                    Community-Datenbasis
                  </td>
                </tr>
                <tr class="border-t border-gray-50 hover:bg-gray-50 transition-colors">
                  <td class="sticky left-0 bg-white z-10 px-4 py-3 text-gray-600 font-medium whitespace-nowrap">
                    Ladevorgänge
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center" :class="cellClass(i, logCounts, false)">
                    {{ validModels[i].logCount > 0 ? validModels[i].logCount.toLocaleString('de-DE') : '–' }}
                  </td>
                </tr>
                <tr class="border-t border-gray-50 hover:bg-gray-50 transition-colors">
                  <td class="sticky left-0 bg-white z-10 px-4 py-3 text-gray-600 font-medium whitespace-nowrap">
                    Mitwirkende
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center text-gray-700">
                    {{ validModels[i].uniqueContributors > 0 ? validModels[i].uniqueContributors : '–' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Legend -->
          <div class="px-4 py-3 border-t border-gray-100 flex flex-wrap gap-4 text-xs text-gray-400">
            <span class="flex items-center gap-1.5">
              <span class="w-2.5 h-2.5 rounded-full bg-green-500 inline-block"></span>
              Bester Wert
            </span>
            <span class="flex items-center gap-1.5">
              <span class="w-2.5 h-2.5 rounded-full bg-red-400 inline-block"></span>
              Schlechtester Wert
            </span>
            <span>– = keine Daten vorhanden</span>
          </div>
        </div>

        <!-- Links to individual pages -->
        <div class="bg-white rounded-2xl border border-gray-200 p-5 mb-6">
          <p class="text-sm font-semibold text-gray-700 mb-3">Detailseiten</p>
          <div class="flex flex-wrap gap-3">
            <a v-for="m in validModels" :key="m.brand + m.model"
               :href="`/modelle/${m.brand}/${m.model}`"
               class="flex items-center gap-1.5 text-sm text-green-600 hover:underline">
              <TruckIcon class="h-4 w-4" />
              {{ m.modelDisplayName }} →
            </a>
          </div>
        </div>

        <!-- CTA -->
        <div class="bg-gradient-to-br from-green-600 to-green-700 rounded-2xl p-6 text-white">
          <h2 class="text-xl font-bold mb-2">Daten fehlen oder unvollständig?</h2>
          <p class="text-green-100 text-sm mb-4">
            Trage deine Ladevorgänge bei – je mehr Fahrer mitmachen, desto belastbarer werden die Vergleichswerte.
          </p>
          <div class="flex flex-wrap gap-3">
            <a href="/register" class="bg-white text-green-700 font-semibold px-4 py-2 rounded-lg hover:bg-green-50 transition-colors text-sm">
              Kostenlos starten
            </a>
            <a href="/login" class="border border-white text-white px-4 py-2 rounded-lg hover:bg-green-600 transition-colors text-sm">
              Anmelden
            </a>
          </div>
        </div>
      </template>
    </main>

    <footer class="max-w-6xl mx-auto px-4 py-8 mt-6 border-t border-gray-200 text-sm text-gray-500 text-center">
      © {{ currentYear }} EV Monitor ·
      <a href="/modelle" class="hover:text-gray-700">Alle Modelle</a>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useHead } from '@unhead/vue'
import { getModelStats, type PublicModelStats } from '../api/publicModelService'
import { TruckIcon, ArrowLeftIcon, ChartBarIcon } from '@heroicons/vue/24/outline'
import PublicNav from '../components/PublicNav.vue'

const route = useRoute()
const loading = ref(true)
const error = ref<string | null>(null)
const models = ref<(PublicModelStats | null)[]>([])
const currentYear = new Date().getFullYear()

const modelKeys = computed((): string[] => {
  const raw = route.query.models as string | undefined
  if (!raw) return []
  return raw.split(',').slice(0, 3).map(s => s.trim()).filter(Boolean)
})

onMounted(async () => {
  if (modelKeys.value.length < 2) {
    error.value = 'Bitte mindestens 2 Modelle zum Vergleich auswählen.'
    loading.value = false
    return
  }
  try {
    models.value = await Promise.all(
      modelKeys.value.map(key => {
        const [brand, model] = key.split('/')
        return getModelStats(brand, model)
      })
    )
  } catch {
    error.value = 'Fehler beim Laden der Modelldaten.'
  } finally {
    loading.value = false
  }
})

const validModels = computed(() => models.value.filter((m): m is PublicModelStats => m !== null))

// ── Metric arrays (index = model index) ───────────────────────────────────────
const realConsumptions = computed(() => validModels.value.map(m => m.avgConsumptionKwhPer100km))

const wltpConsumptions = computed(() =>
  validModels.value.map(m =>
    m.wltpVariants.length > 0
      ? Math.min(...m.wltpVariants.map(v => v.wltpConsumptionKwhPer100km))
      : null
  )
)

const wltpDeltas = computed(() =>
  validModels.value.map((_, i) => {
    const real = realConsumptions.value[i]
    const wltp = wltpConsumptions.value[i]
    if (!real || !wltp) return null
    return ((real - wltp) / wltp) * 100
  })
)

const realRanges = computed(() =>
  validModels.value.map((m, i) => {
    const real = realConsumptions.value[i]
    if (!real || !m.wltpVariants.length) return null
    const maxBattery = Math.max(...m.wltpVariants.map(v => v.batteryCapacityKwh))
    return Math.round(maxBattery * 0.8 / real * 100)
  })
)

const wltpRanges = computed(() =>
  validModels.value.map(m =>
    m.wltpVariants.length > 0 ? Math.max(...m.wltpVariants.map(v => v.wltpRangeKm)) : null
  )
)

const costsPerHundred = computed(() =>
  validModels.value.map((m, i) => {
    const real = realConsumptions.value[i]
    if (!m.avgCostPerKwh || !real) return null
    return m.avgCostPerKwh * real
  })
)

const costsPerKwh = computed(() =>
  validModels.value.map(m => m.avgCostPerKwh ? m.avgCostPerKwh * 100 : null)
)

const summerConsumptions = computed(() =>
  validModels.value.map(m => m.seasonalDistribution?.summerConsumptionKwhPer100km ?? null)
)

const winterConsumptions = computed(() =>
  validModels.value.map(m => m.seasonalDistribution?.winterConsumptionKwhPer100km ?? null)
)

const logCounts = computed(() => validModels.value.map(m => m.logCount))

// ── Highlighting helpers ───────────────────────────────────────────────────────
function bestIdx(values: (number | null)[], lowerIsBetter: boolean): number {
  const valid = values.map((v, i) => ({ v, i })).filter(x => x.v !== null)
  if (valid.length < 2) return -1
  return lowerIsBetter
    ? valid.reduce((a, b) => a.v! < b.v! ? a : b).i
    : valid.reduce((a, b) => a.v! > b.v! ? a : b).i
}

function worstIdx(values: (number | null)[], lowerIsBetter: boolean): number {
  const valid = values.map((v, i) => ({ v, i })).filter(x => x.v !== null)
  if (valid.length < 2) return -1
  return lowerIsBetter
    ? valid.reduce((a, b) => a.v! > b.v! ? a : b).i
    : valid.reduce((a, b) => a.v! < b.v! ? a : b).i
}

function cellClass(i: number, vals: (number | null)[], lowerIsBetter = true): string {
  if (bestIdx(vals, lowerIsBetter) === i) return 'text-green-600 font-bold'
  if (worstIdx(vals, lowerIsBetter) === i) return 'text-red-500'
  return 'text-gray-800'
}

function deltaChipClass(pct: number): string {
  if (pct <= 5) return 'bg-green-100 text-green-700'
  if (pct <= 15) return 'bg-yellow-100 text-yellow-700'
  return 'bg-red-100 text-red-700'
}

useHead(computed(() => {
  const names = validModels.value.map(m => m.modelDisplayName).join(' vs. ')
  return {
    title: names ? `${names} – Vergleich | EV Monitor` : 'Modellvergleich | EV Monitor',
    meta: [
      { name: 'description', content: `Direkter Vergleich: ${names}. Realer Verbrauch, Reichweite, Kosten und WLTP-Abweichung aus echten Community-Daten.` },
      { name: 'robots', content: 'index, follow' }
    ]
  }
}))
</script>
