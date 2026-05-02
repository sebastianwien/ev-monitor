<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-950">
    <PublicNav />

    <main class="max-w-3xl mx-auto px-4 py-8">
      <!-- Hero -->
      <div class="text-center mb-8">
        <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 mb-2 leading-tight">
          Echte Reichweite Elektroauto - Real vs. WLTP
        </h1>
        <p class="text-gray-500 dark:text-gray-400 text-sm sm:text-base max-w-xl mx-auto">
          Wie weit fahren E-Autos wirklich? Community-Verbrauchsdaten von echten Fahrern zeigen,
          wie stark der Unterschied zum WLTP-Laborwert ist.
        </p>
      </div>

      <!-- WLTP Erklarung -->
      <div class="bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-800 rounded-xl p-4 mb-6 text-sm text-amber-800 dark:text-amber-300">
        <strong>Was ist WLTP?</strong> Der WLTP-Wert (Worldwide Harmonised Light Vehicle Test Procedure) ist ein
        Laborwert unter kontrollierten Bedingungen. In der Praxis weicht der reale Verbrauch je nach Temperatur,
        Fahrweise und Streckentyp deutlich ab.
      </div>

      <!-- Loading -->
      <div v-if="loading" class="flex justify-center py-16">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-green-500"></div>
      </div>

      <!-- Model Table -->
      <div v-if="!loading && models.length > 0">
        <!-- Strompreis-Eingabe für Kostenspalte -->
        <div class="flex items-center justify-between mb-4 gap-3 flex-wrap">
          <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200">
            {{ models.length }} Modelle im Vergleich
          </h2>
          <div class="flex items-center gap-2 text-sm">
            <label class="text-gray-500 dark:text-gray-400 shrink-0">Strompreis:</label>
            <input
              v-model.number="strompreis"
              type="number"
              min="0.10"
              max="0.80"
              step="0.01"
              class="w-20 border border-gray-200 dark:border-gray-600 rounded-lg px-2 py-1 text-sm text-right bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
            />
            <span class="text-gray-400 shrink-0">€/kWh</span>
          </div>
        </div>

        <div class="space-y-2">
          <div
            v-for="model in sortedModels"
            :key="`${model.brand}-${model.model}`"
            class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-4"
          >
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0">
                <a
                  :href="`/modelle/${model.brand}/${model.modelUrlSlug}`"
                  class="text-sm font-semibold text-gray-900 dark:text-gray-100 hover:text-green-600 dark:hover:text-green-400 transition-colors"
                >
                  {{ model.brandDisplayName }} {{ model.modelDisplayName }}
                </a>
                <div class="text-xs text-gray-400 mt-0.5">
                  {{ model.logCount.toLocaleString('de-DE') }} Ladevorgänge
                </div>
              </div>
              <div class="text-right shrink-0">
                <div v-if="model.avgConsumptionKwhPer100km" class="text-base font-bold text-gray-900 dark:text-gray-100">
                  {{ model.avgConsumptionKwhPer100km.toFixed(1) }} kWh
                </div>
                <div v-else class="text-base font-bold text-gray-400">-</div>
                <div class="text-xs text-gray-400">real / 100 km</div>
              </div>
            </div>

            <!-- Bar Visualisierung -->
            <div v-if="model.avgConsumptionKwhPer100km && model.wltpMidpoint" class="mt-3 space-y-1.5">
              <!-- Real -->
              <div class="flex items-center gap-2 text-xs">
                <span class="w-12 text-gray-500 dark:text-gray-400 shrink-0">Real</span>
                <div class="flex-1 h-2.5 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
                  <div
                    class="h-full bg-green-500 rounded-full"
                    :style="{ width: barWidth(model.avgConsumptionKwhPer100km) }"
                  ></div>
                </div>
                <span class="w-20 text-right text-gray-600 dark:text-gray-300 font-medium">
                  {{ formatKosten(model.avgConsumptionKwhPer100km) }} €/100km
                </span>
              </div>
              <!-- WLTP -->
              <div class="flex items-center gap-2 text-xs">
                <span class="w-12 text-gray-500 dark:text-gray-400 shrink-0">WLTP</span>
                <div class="flex-1 h-2.5 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
                  <div
                    class="h-full bg-blue-300 dark:bg-blue-600 rounded-full"
                    :style="{ width: barWidth(model.wltpMidpoint) }"
                  ></div>
                </div>
                <span class="w-20 text-right text-gray-500 dark:text-gray-400">
                  {{ formatKosten(model.wltpMidpoint) }} €/100km
                </span>
              </div>
            </div>

            <!-- Abweichung Badge -->
            <div v-if="model.deviationPct !== null" class="mt-2 flex items-center gap-2">
              <span
                :class="model.deviationPct <= 0 ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400' : model.deviationPct <= 25 ? 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400' : 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400'"
                class="inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full"
              >
                {{ model.deviationPct >= 0 ? '+' : '' }}{{ model.deviationPct.toFixed(0) }}% {{ model.deviationPct >= 0 ? 'über' : 'unter' }} WLTP
              </span>
              <a
                :href="`/modelle/${model.brand}/${model.modelUrlSlug}`"
                class="text-xs text-green-600 dark:text-green-400 hover:underline"
              >
                Details ansehen
              </a>
            </div>
          </div>
        </div>
      </div>

      <!-- Info -->
      <div class="mt-8 space-y-5 text-sm text-gray-600 dark:text-gray-400">
        <div>
          <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-1">
            Warum weicht die echte Reichweite vom WLTP ab?
          </h2>
          <p>
            WLTP-Tests finden bei 23 Grad, ohne Klimaanlage/Heizung und auf einem definierten Mischzyklus statt.
            Im Alltag kommen Autobahn (deutlich mehr Verbrauch), Winter (-20 bis -40% durch Heizung + Kaltakkus)
            und aggressive Fahrweise dazu. Die Community-Werte zeigen den echten Jahresdurchschnitt.
          </p>
        </div>
        <div>
          <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-1">
            Wie werden die Daten erhoben?
          </h2>
          <p>
            Alle Werte kommen von EV-Monitor-Nutzern, die ihre Ladevorgänge und Fahrten dokumentieren.
            Der Verbrauch wird aus tatsächlich geladenen kWh und gefahrenen km berechnet - nicht vom Bordcomputer.
            Ausreißer werden per Plausibilitätsprüfung gefiltert.
          </p>
        </div>
        <div>
          <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-1">
            Kann ich meine Daten beisteuern?
          </h2>
          <p>
            Ja - jeder EV-Monitor-Nutzer trägt automatisch zur Community-Statistik bei.
            Mehr Daten bedeuten präzisere Werte für alle.
          </p>
        </div>
      </div>

      <!-- CTA -->
      <div class="mt-8 bg-gradient-to-br from-gray-900 to-green-900 rounded-xl p-6 text-center">
        <h2 class="text-lg font-bold text-white mb-2">Deine echte Reichweite herausfinden</h2>
        <p class="text-gray-300 text-sm mb-4">
          Trag deine Ladevorgänge ein - EV Monitor berechnet deinen realen Verbrauch und deine echte Reichweite automatisch.
        </p>
        <a
          href="/register"
          class="inline-flex items-center gap-2 bg-green-500 hover:bg-green-400 text-white font-semibold px-6 py-3 rounded-lg transition-colors text-sm"
        >
          Kostenlos starten
          <ArrowRightIcon class="h-4 w-4" />
        </a>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useHead } from '@unhead/vue';
import { ArrowRightIcon } from '@heroicons/vue/24/outline';
import PublicNav from '../components/shared/PublicNav.vue';
import { getTopModels, type TopModelPreview } from '../api/publicModelService';

useHead({
  title: 'Echte Reichweite Elektroauto - Real vs. WLTP Vergleich | EV Monitor',
  meta: [
    {
      name: 'description',
      content: 'Wie weit fahren Elektroautos wirklich? Real-Verbrauch vs. WLTP für Tesla, VW ID.4, BMW iX und mehr - aus echten Fahrdaten der EV-Monitor-Community.'
    },
    { property: 'og:title', content: 'Echte Reichweite Elektroauto - Real vs. WLTP | EV Monitor' },
    { property: 'og:description', content: 'Community-Verbrauchsdaten zeigen die echte Reichweite von Elektroautos im Alltag - nicht den WLTP-Laborwert.' }
  ],
  link: [{ rel: 'canonical', href: 'https://ev-monitor.net/echte-reichweite' }],
  script: [
    {
      type: 'application/ld+json',
      innerHTML: JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'Article',
        headline: 'Echte Reichweite Elektroautos - Community-Verbrauchsdaten',
        description: 'Realer Verbrauch vs. WLTP für die meistgefahrenen Elektroautos - aus Fahrdaten echter EV-Monitor-Nutzer',
        url: 'https://ev-monitor.net/echte-reichweite',
        author: { '@type': 'Person', name: 'Sebastian Ihle' },
        publisher: { '@type': 'Organization', name: 'EV Monitor', url: 'https://ev-monitor.net' },
        datePublished: '2026-04-30',
        breadcrumb: {
          '@type': 'BreadcrumbList',
          itemListElement: [
            { '@type': 'ListItem', position: 1, name: 'EV Monitor', item: 'https://ev-monitor.net' },
            { '@type': 'ListItem', position: 2, name: 'Echte Reichweite', item: 'https://ev-monitor.net/echte-reichweite' }
          ]
        }
      })
    },
    {
      type: 'application/ld+json',
      innerHTML: JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'FAQPage',
        mainEntity: [
          {
            '@type': 'Question',
            name: 'Warum weicht die echte Reichweite vom WLTP ab?',
            acceptedAnswer: {
              '@type': 'Answer',
              text: 'WLTP-Tests finden bei 23 Grad, ohne Klimaanlage/Heizung und auf einem definierten Mischzyklus statt. Im Alltag kommen Autobahn, Winter (-20 bis -40% durch Heizung + Kaltakkus) und aggressive Fahrweise dazu. Die Community-Werte zeigen den echten Jahresdurchschnitt.'
            }
          },
          {
            '@type': 'Question',
            name: 'Wie werden die Daten erhoben?',
            acceptedAnswer: {
              '@type': 'Answer',
              text: 'Alle Werte kommen von EV-Monitor-Nutzern, die ihre Ladevorgänge und Fahrten dokumentieren. Der Verbrauch wird aus tatsächlich geladenen kWh und gefahrenen km berechnet - nicht vom Bordcomputer. Ausreißer werden per Plausibilitätsprüfung gefiltert.'
            }
          },
          {
            '@type': 'Question',
            name: 'Kann ich meine Daten beisteuern?',
            acceptedAnswer: {
              '@type': 'Answer',
              text: 'Ja - jeder EV-Monitor-Nutzer trägt automatisch zur Community-Statistik bei. Mehr Daten bedeuten präzisere Werte für alle.'
            }
          }
        ]
      })
    }
  ]
});

interface EnrichedModel extends TopModelPreview {
  wltpMidpoint: number | null;
  deviationPct: number | null;
}

const loading = ref(true);
const models = ref<EnrichedModel[]>([]);
const strompreis = ref(0.33);

const maxConsumption = computed(() => {
  const vals = models.value
    .map(m => m.avgConsumptionKwhPer100km ?? 0)
    .filter(v => v > 0);
  return vals.length > 0 ? Math.max(...vals) : 30;
});

const sortedModels = computed(() =>
  [...models.value]
    .filter(m => m.avgConsumptionKwhPer100km !== null && m.logCount >= 10)
    .sort((a, b) => (a.deviationPct ?? 999) - (b.deviationPct ?? 999))
);

function barWidth(value: number | null): string {
  if (!value) return '0%';
  return `${Math.min(100, (value / (maxConsumption.value * 1.1)) * 100).toFixed(1)}%`;
}

function formatKosten(kwhPer100km: number | null): string {
  if (!kwhPer100km) return '-';
  return (kwhPer100km * strompreis.value).toFixed(2);
}

onMounted(async () => {
  try {
    const raw = await getTopModels(50);
    models.value = raw.map(m => {
      const wltpMidpoint =
        m.minWltpConsumptionKwhPer100km && m.maxWltpConsumptionKwhPer100km
          ? (m.minWltpConsumptionKwhPer100km + m.maxWltpConsumptionKwhPer100km) / 2
          : m.minWltpConsumptionKwhPer100km ?? m.maxWltpConsumptionKwhPer100km ?? null;

      const deviationPct =
        m.avgConsumptionKwhPer100km && wltpMidpoint && wltpMidpoint > 0
          ? ((m.avgConsumptionKwhPer100km - wltpMidpoint) / wltpMidpoint) * 100
          : null;

      return { ...m, wltpMidpoint, deviationPct };
    });
  } finally {
    loading.value = false;
  }
});
</script>
