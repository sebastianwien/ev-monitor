<template>
  <div class="min-h-screen bg-gray-50">
    <!-- Navigation (only show if not authenticated) -->
    <nav v-if="!isAuthenticated" class="bg-white border-b border-gray-200 px-4 py-3">
      <div class="max-w-4xl mx-auto flex items-center justify-between">
        <a href="/" class="flex items-center gap-2 font-bold text-green-600 text-lg">
          <BoltIcon class="h-6 w-6" />
          EV Monitor
        </a>
        <div class="flex items-center gap-3">
          <a href="/login" class="text-sm text-gray-600 hover:text-gray-900">Anmelden</a>
          <a href="/register" class="text-sm bg-green-600 text-white px-3 py-1.5 rounded-lg hover:bg-green-700">
            Kostenlos registrieren
          </a>
        </div>
      </div>
    </nav>

    <main class="max-w-4xl mx-auto px-4 py-8">
      <!-- Loading state -->
      <div v-if="loading" class="flex items-center justify-center py-20">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-green-600"></div>
      </div>

      <!-- 404 state -->
      <div v-else-if="notFound" class="text-center py-20">
        <div class="text-5xl mb-4">🔍</div>
        <h1 class="text-2xl font-bold text-gray-800 mb-2">Modell nicht gefunden</h1>
        <p class="text-gray-500 mb-6">Für dieses Fahrzeugmodell haben wir leider keine Daten.</p>
        <a href="/" class="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700">
          Zur Startseite
        </a>
      </div>

      <!-- Model stats page -->
      <div v-else-if="stats">
        <!-- Breadcrumb -->
        <nav class="text-sm text-gray-500 mb-4">
          <a href="/" class="hover:text-gray-700">EV Monitor</a>
          <span class="mx-2">›</span>
          <a href="/modelle" class="hover:text-gray-700">Modelle</a>
          <span class="mx-2">›</span>
          <span class="text-gray-900">{{ stats.modelDisplayName }}</span>
        </nav>

        <!-- Hero -->
        <div class="bg-white rounded-2xl border border-gray-200 p-6 mb-6">
          <h1 class="text-3xl font-bold text-gray-900 mb-2">
            {{ stats.modelDisplayName }} – Realer Verbrauch & WLTP Vergleich
          </h1>
          <p class="text-gray-600 text-lg">
            Echte Verbrauchsdaten von EV Monitor Nutzern – kein Marketing, nur Realität.
          </p>

          <!-- Key metrics -->
          <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mt-6">
            <div class="bg-green-50 rounded-xl p-4 text-center">
              <div class="text-2xl font-bold text-green-700">
                {{ stats.logCount > 0 ? stats.logCount.toLocaleString('de-DE') : '–' }}
              </div>
              <div class="text-sm text-green-600 mt-1">Ladevorgänge</div>
            </div>
            <div class="bg-blue-50 rounded-xl p-4 text-center">
              <div class="text-2xl font-bold text-blue-700">
                {{ stats.uniqueContributors > 0 ? stats.uniqueContributors : '–' }}
              </div>
              <div class="text-sm text-blue-600 mt-1">Fahrer</div>
            </div>
            <div class="bg-purple-50 rounded-xl p-4 text-center">
              <div class="text-2xl font-bold text-purple-700">
                {{ stats.avgConsumptionKwhPer100km ? stats.avgConsumptionKwhPer100km.toFixed(1) + ' kWh' : '–' }}
              </div>
              <div class="text-sm text-purple-600 mt-1">Ø Verbrauch / 100km</div>
              <div v-if="stats.estimatedConsumptionCount > 0" class="text-xs text-purple-500 mt-2 italic">
                {{ stats.estimatedConsumptionCount }} geschätzt (ohne SoC)
              </div>
            </div>
            <div class="bg-yellow-50 rounded-xl p-4 text-center">
              <div class="text-2xl font-bold text-yellow-700">
                {{ stats.avgCostPerKwh ? (stats.avgCostPerKwh * 100).toFixed(1) + ' ct' : '–' }}
              </div>
              <div class="text-sm text-yellow-600 mt-1">Ø Preis / kWh</div>
            </div>
          </div>

          <!-- No data yet notice -->
          <div v-if="stats.logCount === 0" class="mt-4 p-4 bg-gray-50 rounded-xl border border-gray-200">
            <p class="text-gray-600 text-sm">
              Noch keine Fahrdaten für dieses Modell vorhanden.
              <a href="/register" class="text-green-600 font-medium hover:underline">Registriere dich</a>
              und trage als Erster deine Ladevorgänge ein!
            </p>
          </div>

          <!-- Seasonal Consumption Breakdown -->
          <div v-if="showSeasonalBreakdown" class="mt-4 p-4 bg-gradient-to-br from-blue-50 to-indigo-50 border border-blue-200 rounded-xl">
            <div class="flex items-start gap-3">
              <InformationCircleIcon class="h-5 w-5 text-blue-600 mt-0.5 flex-shrink-0" />
              <div class="flex-1">
                <p class="text-sm font-bold text-blue-900 mb-3">
                  📊 Verbrauch nach Jahreszeit
                </p>

                <!-- Summer Stats -->
                <div class="flex items-center justify-between mb-2 text-sm">
                  <div class="flex items-center gap-2">
                    <span class="text-orange-600 font-medium">🌞 Sommer (Apr–Sep)</span>
                  </div>
                  <div class="flex items-center gap-3">
                    <span v-if="stats.seasonalDistribution!.summerConsumptionKwhPer100km"
                          class="font-bold text-orange-700">
                      {{ stats.seasonalDistribution!.summerConsumptionKwhPer100km.toFixed(1) }} kWh/100km
                    </span>
                    <span v-else class="text-gray-400 text-xs">—</span>
                    <span class="text-xs text-gray-500">({{ stats.seasonalDistribution!.summerLogCount }} Fahrten)</span>
                  </div>
                </div>

                <!-- Winter Stats -->
                <div class="flex items-center justify-between mb-3 text-sm">
                  <div class="flex items-center gap-2">
                    <span class="text-blue-700 font-medium">❄️ Winter (Okt–Mär)</span>
                  </div>
                  <div class="flex items-center gap-3">
                    <span v-if="stats.seasonalDistribution!.winterConsumptionKwhPer100km"
                          class="font-bold text-blue-800">
                      {{ stats.seasonalDistribution!.winterConsumptionKwhPer100km.toFixed(1) }} kWh/100km
                    </span>
                    <span v-else class="text-gray-400 text-xs">—</span>
                    <span class="text-xs text-gray-500">({{ stats.seasonalDistribution!.winterLogCount }} Fahrten)</span>
                  </div>
                </div>

                <!-- Weighted Average (Overall) -->
                <div class="pt-2 border-t border-blue-200">
                  <div class="flex items-center justify-between text-sm">
                    <span class="text-gray-700 font-medium">Ø Gewichtet (Gesamt)</span>
                    <span class="font-bold text-gray-900">
                      {{ stats.seasonalDistribution!.totalConsumptionKwhPer100km != null ? stats.seasonalDistribution!.totalConsumptionKwhPer100km.toFixed(1) + ' kWh/100km' : '—' }}
                    </span>
                  </div>
                  <p class="text-xs text-gray-500 mt-1">
                    {{ stats.seasonalDistribution!.summerPercentage }}% Sommer,
                    {{ stats.seasonalDistribution!.winterPercentage }}% Winter (nach gefahrenen km)
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- WLTP vs Real Comparison -->
        <div v-if="stats.wltpVariants.length > 0" class="bg-white rounded-2xl border border-gray-200 p-6 mb-6">
          <h2 class="text-xl font-bold text-gray-900 mb-4 flex items-center gap-2">
            <ClipboardDocumentListIcon class="h-6 w-6 text-gray-700" />
            Offizielle WLTP-Daten nach Batterievariante
          </h2>
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="text-left text-gray-500 border-b border-gray-100">
                  <th class="pb-3 pr-4 font-medium">Batterie</th>
                  <th class="pb-3 pr-4 font-medium">WLTP Reichweite</th>
                  <th class="pb-3 pr-4 font-medium">WLTP Verbrauch</th>
                  <th class="pb-3 font-medium">Realer Verbrauch</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="variant in stats.wltpVariants" :key="variant.batteryCapacityKwh"
                    class="border-b border-gray-50">
                  <td class="py-3 pr-4 font-medium text-gray-900">{{ variant.batteryCapacityKwh }} kWh</td>
                  <td class="py-3 pr-4 text-gray-700">{{ variant.wltpRangeKm }} km</td>
                  <td class="py-3 pr-4 text-gray-700">{{ variant.wltpConsumptionKwhPer100km }} kWh/100km</td>
                  <td class="py-3">
                    <span v-if="variant.realConsumptionKwhPer100km" class="flex items-center gap-2 flex-wrap">
                      <span :class="consumptionDeltaClass(variant.realConsumptionKwhPer100km, variant.wltpConsumptionKwhPer100km)"
                            class="font-medium">
                        {{ variant.realConsumptionKwhPer100km.toFixed(1) }} kWh/100km
                      </span>
                      <span :class="deltaLabelClass(variant.realConsumptionKwhPer100km, variant.wltpConsumptionKwhPer100km)"
                            class="text-xs px-1.5 py-0.5 rounded-full">
                        {{ deltaLabel(variant.realConsumptionKwhPer100km, variant.wltpConsumptionKwhPer100km) }}
                      </span>
                      <span v-if="variant.realConsumptionTripCount != null && variant.realConsumptionTripCount < 10"
                            class="inline-flex items-center gap-1 text-xs px-1.5 py-0.5 rounded-full bg-red-50 border border-red-200 text-red-600"
                            :title="`Basiert auf nur ${variant.realConsumptionTripCount} ${variant.realConsumptionTripCount === 1 ? 'Fahrt' : 'Fahrten'} – Wert noch nicht repräsentativ`">
                        ⚠ {{ variant.realConsumptionTripCount }} {{ variant.realConsumptionTripCount === 1 ? 'Fahrt' : 'Fahrten' }}
                      </span>
                      <span v-else-if="variant.realConsumptionTripCount != null && variant.realConsumptionTripCount < 50"
                            class="inline-flex items-center gap-1 text-xs px-1.5 py-0.5 rounded-full bg-yellow-50 border border-yellow-200 text-yellow-700"
                            :title="`Basiert auf ${variant.realConsumptionTripCount} Fahrten – Wert wird mit mehr Daten genauer`">
                        {{ variant.realConsumptionTripCount }} Fahrten
                      </span>
                    </span>
                    <span v-else class="text-gray-400">noch keine Daten</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="text-xs text-gray-400 mt-3">
            WLTP = offizieller Herstellerwert (COMBINED). Realer Verbrauch basiert auf Nutzerdaten von EV Monitor.
          </p>
        </div>

        <!-- What is EV Monitor / CTA -->
        <div class="bg-gradient-to-br from-green-600 to-green-700 rounded-2xl p-6 text-white">
          <div class="flex items-center gap-2 mb-2">
            <ArrowTrendingUpIcon class="h-6 w-6" />
            <h2 class="text-xl font-bold">Trage deine Daten bei!</h2>
          </div>
          <p class="text-green-100 mb-4">
            EV Monitor ist eine Community-Plattform für EV-Fahrer. Tracke deine Ladevorgänge,
            vergleiche deinen realen Verbrauch mit WLTP und hilf anderen Fahrern mit deinen Daten.
          </p>
          <div class="flex flex-wrap gap-3">
            <a href="/register"
               class="bg-white text-green-700 font-semibold px-4 py-2 rounded-lg hover:bg-green-50 transition-colors">
              Kostenlos starten
            </a>
            <a href="/login"
               class="border border-white text-white px-4 py-2 rounded-lg hover:bg-green-600 transition-colors">
              Anmelden
            </a>
          </div>
        </div>

        <!-- SEO rich text section -->
        <div class="bg-white rounded-2xl border border-gray-200 p-6 mt-6">
          <h2 class="text-xl font-bold text-gray-900 mb-4">
            {{ stats.modelDisplayName }} – Verbrauch, Reichweite & Ladekosten
          </h2>

          <div class="space-y-4 text-sm text-gray-600 leading-relaxed">
            <p>
              Der <strong>{{ stats.modelDisplayName }}</strong> ist ein Elektrofahrzeug
              <template v-if="bestWltpRange">mit einer offiziellen WLTP-Reichweite von bis zu <strong>{{ bestWltpRange }} km</strong></template>.
              <template v-if="stats.avgConsumptionKwhPer100km">
                Laut realen Messdaten von EV Monitor Nutzern liegt der tatsächliche Durchschnittsverbrauch
                bei <strong>{{ stats.avgConsumptionKwhPer100km.toFixed(1) }} kWh/100km</strong> – ermittelt aus
                {{ stats.logCount }} dokumentierten Ladevorgängen im Alltag.
              </template>
              <template v-else>
                Sei der Erste, der Ladevorgänge für diesen {{ stats.modelDisplayName }} einträgt und der Community hilft!
              </template>
            </p>

            <div v-if="stats.avgCostPerKwh">
              <h3 class="font-semibold text-gray-800 mb-1">Ladekosten im Alltag</h3>
              <p>
                Der durchschnittliche Ladepreis für den {{ stats.modelDisplayName }} beträgt laut Nutzerdaten
                <strong>{{ (stats.avgCostPerKwh * 100).toFixed(1) }} Cent pro kWh</strong>.
                <template v-if="stats.avgKwhPerSession">
                  Eine typische Ladeeinheit von {{ stats.avgKwhPerSession.toFixed(1) }} kWh kostet damit
                  rund <strong>{{ (stats.avgCostPerKwh * stats.avgKwhPerSession).toFixed(2) }} €</strong>.
                </template>
                Zu Hause an der Wallbox fallen geringere Kosten an (ca. 25–35 ct/kWh) als an öffentlichen
                Schnellladesäulen (35–60 ct/kWh).
              </p>
            </div>

            <div v-if="stats.wltpVariants.length > 0">
              <h3 class="font-semibold text-gray-800 mb-1">WLTP vs. realer Verbrauch</h3>
              <p>
                Der WLTP-Zyklus wird unter standardisierten Laborbedingungen gemessen und weicht im Alltag
                häufig ab. Faktoren wie Autobahnfahrten, Heizung, Klimaanlage und Fahrstil erhöhen den
                tatsächlichen Energieverbrauch des {{ stats.modelDisplayName }}.
                <template v-if="stats.avgConsumptionKwhPer100km && worstWltpConsumption">
                  Im Community-Durchschnitt liegt der Realverbrauch
                  <strong>{{ (stats.avgConsumptionKwhPer100km - worstWltpConsumption).toFixed(1) >= '0'
                    ? '+' + (stats.avgConsumptionKwhPer100km - worstWltpConsumption).toFixed(1)
                    : (stats.avgConsumptionKwhPer100km - worstWltpConsumption).toFixed(1) }} kWh/100km</strong>
                  gegenüber dem WLTP-Wert.
                </template>
              </p>
            </div>

            <div>
              <h3 class="font-semibold text-gray-800 mb-1">Verbrauch im Winter und Sommer</h3>
              <p>
                Wie alle Elektroautos zeigt der {{ stats.modelDisplayName }} saisonale Verbrauchsschwankungen.
                Im Winter (Dezember bis Februar) steigt der Verbrauch typischerweise um <strong>20–30%</strong>
                durch Kabinenheizung und reduzierte Batterieeffizienz bei Kälte. Im Sommer (Juni bis August)
                wird die maximale Effizienz erreicht. Vorheizen des Fahrzeugs beim Laden schont die Reichweite
                erheblich.
              </p>
            </div>
          </div>
        </div>

        <!-- FAQ Section -->
        <div v-if="faqItems.length > 0" class="bg-white rounded-2xl border border-gray-200 p-6 mt-6">
          <h2 class="text-xl font-bold text-gray-900 mb-4">
            Häufige Fragen zum {{ stats.modelDisplayName }}
          </h2>
          <div class="space-y-3">
            <details v-for="(faq, i) in faqItems" :key="i"
                     class="border border-gray-100 rounded-xl overflow-hidden">
              <summary class="flex items-center justify-between p-4 cursor-pointer hover:bg-gray-50
                              font-medium text-gray-900 text-sm list-none">
                {{ faq.question }}
                <span class="text-gray-400 ml-2 flex-shrink-0">﹀</span>
              </summary>
              <div class="px-4 pb-4 pt-1 text-sm text-gray-600 leading-relaxed border-t border-gray-100">
                {{ faq.answer }}
              </div>
            </details>
          </div>
        </div>
      </div>
    </main>

    <!-- Internal linking: popular models -->
    <div class="max-w-4xl mx-auto px-4 mt-8">
      <div class="bg-white rounded-2xl border border-gray-200 p-6">
        <h2 class="text-base font-bold text-gray-900 mb-3">Andere beliebte Elektroauto-Modelle</h2>
        <div class="flex flex-wrap gap-2 text-sm">
          <a href="/modelle/TESLA/MODEL_3" class="text-green-600 hover:underline">Tesla Model 3</a>
          <span class="text-gray-300">·</span>
          <a href="/modelle/TESLA/MODEL_Y" class="text-green-600 hover:underline">Tesla Model Y</a>
          <span class="text-gray-300">·</span>
          <a href="/modelle/VW/ID_3" class="text-green-600 hover:underline">VW ID.3</a>
          <span class="text-gray-300">·</span>
          <a href="/modelle/VW/ID_4" class="text-green-600 hover:underline">VW ID.4</a>
          <span class="text-gray-300">·</span>
          <a href="/modelle/HYUNDAI/IONIQ_5" class="text-green-600 hover:underline">Hyundai Ioniq 5</a>
          <span class="text-gray-300">·</span>
          <a href="/modelle/HYUNDAI/IONIQ_6" class="text-green-600 hover:underline">Hyundai Ioniq 6</a>
          <span class="text-gray-300">·</span>
          <a href="/modelle/KIA/EV_6" class="text-green-600 hover:underline">Kia EV6</a>
          <span class="text-gray-300">·</span>
          <a href="/modelle/BMW/I4" class="text-green-600 hover:underline">BMW i4</a>
          <span class="text-gray-300">·</span>
          <a href="/modelle/AUDI/Q4_E_TRON" class="text-green-600 hover:underline">Audi Q4 e-tron</a>
          <span class="text-gray-300">·</span>
          <a href="/modelle/POLESTAR/POLESTAR_2" class="text-green-600 hover:underline">Polestar 2</a>
          <span class="text-gray-300">·</span>
          <a href="/modelle/RENAULT/ZOE" class="text-green-600 hover:underline">Renault Zoe</a>
          <span class="text-gray-300">·</span>
          <a href="/modelle/NISSAN/LEAF" class="text-green-600 hover:underline">Nissan Leaf</a>
        </div>
      </div>
    </div>

    <footer class="max-w-4xl mx-auto px-4 py-8 mt-6 border-t border-gray-200 text-sm text-gray-500 text-center">
      © {{ currentYear }} EV Monitor ·
      <a href="/" class="hover:text-gray-700">{{ isAuthenticated ? 'Dashboard' : 'Startseite' }}</a>
      <template v-if="!isAuthenticated">
        ·
        <a href="/register" class="hover:text-gray-700">Kostenlos registrieren</a> ·
        <a href="/login" class="hover:text-gray-700">Anmelden</a>
      </template>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useHead } from '@unhead/vue'
import { useAuthStore } from '../stores/auth'
import { getModelStats, type PublicModelStats } from '../api/publicModelService'
import { BoltIcon, ArrowTrendingUpIcon, InformationCircleIcon, ClipboardDocumentListIcon } from '@heroicons/vue/24/outline'

const route = useRoute()
const authStore = useAuthStore()
const loading = ref(true)
const notFound = ref(false)
const stats = ref<PublicModelStats | null>(null)

const isAuthenticated = computed(() => authStore.isAuthenticated())

const brand = route.params.brand as string
const model = route.params.model as string

const bestWltpRange = computed(() => {
  if (!stats.value?.wltpVariants.length) return null
  return Math.max(...stats.value.wltpVariants.map(v => v.wltpRangeKm))
})

const worstWltpConsumption = computed(() => {
  if (!stats.value?.wltpVariants.length) return null
  return Math.max(...stats.value.wltpVariants.map(v => v.wltpConsumptionKwhPer100km))
})

const showSeasonalBreakdown = computed(() => {
  // Always show seasonal breakdown if data exists
  return !!stats.value?.seasonalDistribution
})


const faqItems = computed(() => {
  if (!stats.value) return []
  const name = stats.value.modelDisplayName
  const items: { question: string; answer: string }[] = []

  // Q1: realer Verbrauch
  if (stats.value.avgConsumptionKwhPer100km) {
    items.push({
      question: `Wie hoch ist der reale Verbrauch des ${name}?`,
      answer: `Laut ${stats.value.logCount} dokumentierten Ladevorgängen von EV Monitor Nutzern liegt der reale Durchschnittsverbrauch des ${name} bei ${stats.value.avgConsumptionKwhPer100km.toFixed(1)} kWh/100km. Der offizielle WLTP-Wert beträgt ${worstWltpConsumption.value?.toFixed(1) ?? '–'} kWh/100km. Im Winter kann der Verbrauch durch Heizung und kältere Batterien um 20–30% höher ausfallen.`
    })
  }

  // Q2: echte Reichweite
  if (bestWltpRange.value && stats.value.avgConsumptionKwhPer100km && stats.value.wltpVariants.length > 0) {
    const largestBattery = Math.max(...stats.value.wltpVariants.map(v => v.batteryCapacityKwh))
    const realRange = Math.round(largestBattery / stats.value.avgConsumptionKwhPer100km * 100)
    items.push({
      question: `Wie weit kommt der ${name} wirklich?`,
      answer: `Der WLTP-Wert des ${name} beträgt bis zu ${bestWltpRange.value} km. In der Praxis erreichen Fahrer mit der ${largestBattery} kWh Batterie bei einem realen Verbrauch von ${stats.value.avgConsumptionKwhPer100km.toFixed(1)} kWh/100km etwa ${realRange} km. Im Winter (Frost, Heizung) kann die Reichweite auf 60–70% des WLTP-Wertes sinken.`
    })
  }

  // Q3: Ladekosten
  if (stats.value.avgCostPerKwh && stats.value.avgKwhPerSession) {
    const costPerSession = (stats.value.avgCostPerKwh * stats.value.avgKwhPerSession).toFixed(2)
    items.push({
      question: `Was kostet eine Ladung des ${name}?`,
      answer: `Im Durchschnitt kostet eine Ladeeinheit ${(stats.value.avgCostPerKwh * 100).toFixed(1)} Cent pro kWh. Bei einer typischen Ladung von ${stats.value.avgKwhPerSession.toFixed(1)} kWh entstehen Kosten von etwa ${costPerSession} €. Zu Hause an der Wallbox ist Laden günstiger (ca. 25–35 ct/kWh), an öffentlichen Schnellladern teurer (35–60 ct/kWh).`
    })
  }

  // Q4: WLTP vs Real
  if (worstWltpConsumption.value && stats.value.avgConsumptionKwhPer100km) {
    const diff = (stats.value.avgConsumptionKwhPer100km - worstWltpConsumption.value).toFixed(1)
    const pct = Math.round((stats.value.avgConsumptionKwhPer100km / worstWltpConsumption.value - 1) * 100)
    items.push({
      question: `Wie groß ist der Unterschied zwischen WLTP und realem Verbrauch beim ${name}?`,
      answer: `Der offizielle WLTP-Verbrauch des ${name} liegt bei ${worstWltpConsumption.value.toFixed(1)} kWh/100km. Laut Community-Daten verbrauchen Fahrer im Alltag ${stats.value.avgConsumptionKwhPer100km.toFixed(1)} kWh/100km – das sind ${diff} kWh mehr (+${pct}%). WLTP-Werte werden unter idealisierten Testbedingungen ermittelt und weichen im Alltag durch Autobahnfahrten, Heizung und Klimaanlage ab.`
    })
  }

  // Q5: Winter
  items.push({
    question: `Wie verändert sich der Verbrauch des ${name} im Winter?`,
    answer: `Im Winter steigt der Verbrauch eines Elektroautos wie dem ${name} typischerweise um 20–30% gegenüber dem Sommer. Ursachen sind der Energiebedarf für die Kabinenheizung, die reduzierte Zellchemie-Effizienz bei Kälte und das Vorheizen der Batterie. Bei -10°C kann der Verbrauch bis zu 40% über dem WLTP-Wert liegen. Vorheizen beim Laden (per App) hilft, die Reichweitenverluste zu minimieren.`
  })

  return items
})

const brandDisplay = computed(() => toTitleCase(brand))
const currentYear = new Date().getFullYear()

// Dynamic SEO meta tags
useHead(computed(() => {
  if (!stats.value || notFound.value) {
    return {
      title: 'Fahrzeugmodell – EV Monitor',
      meta: [
        { name: 'robots', content: 'noindex, nofollow' }
      ]
    }
  }
  const name = stats.value.modelDisplayName
  const consumption = stats.value.avgConsumptionKwhPer100km
    ? `${stats.value.avgConsumptionKwhPer100km.toFixed(1)} kWh/100km`
    : null
  const range = bestWltpRange.value ? `${bestWltpRange.value} km WLTP` : null

  const descParts = [`${name} Verbrauch & Reichweite (${currentYear}): `]
  if (consumption) descParts.push(`Realer Verbrauch ${consumption}. `)
  if (range) descParts.push(`WLTP-Reichweite bis zu ${range}. `)
  descParts.push(`Echte Community-Daten von ${stats.value.logCount > 0 ? stats.value.logCount + ' Ladevorgängen' : 'EV Monitor Nutzern'} – kein Marketing.`)
  const description = descParts.join('').trim()

  const keywords = [
    name,
    `${name} Verbrauch`,
    `${name} kWh pro 100km`,
    `${name} Reichweite`,
    `${name} WLTP`,
    `${name} realer Verbrauch`,
    `${name} Ladekosten`,
    `${name} laden`,
    `${name} Praxisverbrauch`,
    `${name} Winterverbrauch`,
    `${name} Sommerverbrauch`,
    `${brandDisplay.value} Elektroauto`,
    'Elektroauto Verbrauch',
    'EV kWh 100km',
    'WLTP Unterschied real',
    'Elektroauto Ladekosten',
    'EV Reichweite Winter',
    'Ladetagebuch Elektroauto',
  ].join(', ')

  const faqJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: faqItems.value.map(f => ({
      '@type': 'Question',
      name: f.question,
      acceptedAnswer: { '@type': 'Answer', text: f.answer }
    }))
  }

  const productJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: `${name} Elektroauto`,
    category: 'Elektrofahrzeug',
    description,
    brand: { '@type': 'Brand', name: brandDisplay.value },
    ...(stats.value.wltpVariants.length > 0 && {
      additionalProperty: stats.value.wltpVariants.map(v => ({
        '@type': 'PropertyValue',
        name: `WLTP Reichweite (${v.batteryCapacityKwh} kWh)`,
        value: `${v.wltpRangeKm} km`
      }))
    })
  }

  const breadcrumbJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      {
        '@type': 'ListItem',
        position: 1,
        name: 'EV Monitor',
        item: 'https://ev-monitor.net'
      },
      {
        '@type': 'ListItem',
        position: 2,
        name: 'Modelle',
        item: 'https://ev-monitor.net/modelle'
      },
      {
        '@type': 'ListItem',
        position: 3,
        name: name
      }
    ]
  }

  return {
    title: `${name} Verbrauch & Reichweite (${currentYear}) – EV Monitor`,
    meta: [
      { name: 'description', content: description },
      { name: 'keywords', content: keywords },
      { name: 'robots', content: 'index, follow' },
      { property: 'og:title', content: `${name} – Realer Verbrauch & WLTP Vergleich ${currentYear}` },
      { property: 'og:description', content: description },
      { property: 'og:type', content: 'article' },
      { property: 'og:url', content: `https://ev-monitor.net/modelle/${brand}/${model}` },
      { property: 'og:locale', content: 'de_DE' },
    ],
    link: [
      { rel: 'canonical', href: `https://ev-monitor.net/modelle/${brand}/${model}` }
    ],
    script: [
      { type: 'application/ld+json', children: JSON.stringify(breadcrumbJsonLd) },
      { type: 'application/ld+json', children: JSON.stringify(productJsonLd) },
      ...(faqItems.value.length > 0
        ? [{ type: 'application/ld+json', children: JSON.stringify(faqJsonLd) }]
        : [])
    ]
  }
}))

onMounted(async () => {
  try {
    const data = await getModelStats(brand, model)
    if (!data) {
      notFound.value = true
    } else {
      stats.value = data
    }
  } catch {
    notFound.value = true
  } finally {
    loading.value = false
  }
})

function consumptionDeltaClass(real: number, wltp: number): string {
  const percentDelta = ((real - wltp) / wltp) * 100
  if (percentDelta <= 0) return 'text-green-600'
  if (percentDelta <= 15) return 'text-yellow-600'
  return 'text-red-600'
}

function deltaLabelClass(real: number, wltp: number): string {
  const percentDelta = ((real - wltp) / wltp) * 100
  if (percentDelta <= 0) return 'bg-green-100 text-green-700'
  if (percentDelta <= 15) return 'bg-yellow-100 text-yellow-700'
  return 'bg-red-100 text-red-700'
}

function deltaLabel(real: number, wltp: number): string {
  const percentDelta = ((real - wltp) / wltp) * 100
  const sign = percentDelta > 0 ? '+' : ''
  return `${sign}${percentDelta.toFixed(1)}%`
}

function toTitleCase(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase()
}
</script>
