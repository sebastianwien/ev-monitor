<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-950">
    <PublicNav />

    <main class="max-w-3xl mx-auto px-4 py-8">
      <!-- Hero -->
      <div class="text-center mb-8">
        <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 mb-2 leading-tight">
          Elektroauto Kosten pro km - echte Daten
        </h1>
        <p class="text-gray-500 dark:text-gray-400 text-sm sm:text-base max-w-xl mx-auto">
          Wieviel kostet ein Elektroauto pro Kilometer? Stromkosten, Verbrauch und Vergleich zum Benziner -
          berechnet aus realen Fahrdaten.
        </p>
      </div>

      <!-- Strompreis-Eingabe -->
      <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-4 mb-6">
        <div class="flex items-center gap-4 flex-wrap">
          <span class="text-sm font-medium text-gray-700 dark:text-gray-300 shrink-0">Dein Strompreis:</span>
          <div class="flex items-center gap-2 flex-1">
            <input
              v-model.number="strompreis"
              type="range"
              min="0.10"
              max="0.70"
              step="0.01"
              class="flex-1 accent-green-500"
            />
            <div class="flex items-center gap-1">
              <input
                v-model.number="strompreis"
                type="number"
                min="0.10"
                max="0.70"
                step="0.01"
                class="w-16 border border-gray-200 dark:border-gray-600 rounded-lg px-2 py-1 text-sm text-right bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
              />
              <span class="text-sm text-gray-500 dark:text-gray-400">€/kWh</span>
            </div>
          </div>
        </div>
        <p class="text-xs text-gray-400 mt-2">
          Heimladung DE: ca. 28-40 ct/kWh - Öffentliche Schnelllader: 50-80 ct/kWh
        </p>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="flex justify-center py-16">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-green-500"></div>
      </div>

      <!-- Model List -->
      <div v-if="!loading && models.length > 0">
        <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-4">
          Kosten pro 100 km nach Modell
        </h2>
        <div class="space-y-2 mb-8">
          <div
            v-for="(model, index) in topModels"
            :key="`${model.brand}-${model.model}`"
            class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-4 flex items-center gap-4"
          >
            <div class="w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold shrink-0"
              :class="index === 0 ? 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400' : index === 1 ? 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300' : index === 2 ? 'bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-400' : 'bg-gray-50 dark:bg-gray-700/50 text-gray-400'"
            >
              {{ index + 1 }}
            </div>

            <div class="flex-1 min-w-0">
              <a
                :href="`/modelle/${model.brand}/${model.modelUrlSlug}`"
                class="text-sm font-medium text-gray-900 dark:text-gray-100 hover:text-green-600 dark:hover:text-green-400 transition-colors block truncate"
              >
                {{ model.brandDisplayName }} {{ model.modelDisplayName }}
              </a>
              <div class="text-xs text-gray-400 mt-0.5">
                {{ model.avgConsumptionKwhPer100km?.toFixed(1) ?? '?' }} kWh/100km real
                <span v-if="model.minWltpConsumptionKwhPer100km" class="ml-2 text-gray-300 dark:text-gray-600">
                  (WLTP: {{ model.minWltpConsumptionKwhPer100km.toFixed(1) }})
                </span>
              </div>
            </div>

            <div class="text-right shrink-0">
              <div class="text-base font-bold text-green-600 dark:text-green-400">
                {{ formatKosten(model.avgConsumptionKwhPer100km) }}
              </div>
              <div class="text-xs text-gray-400">€ / 100 km</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Kosten-Vergleich Tabelle -->
      <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-5 mb-8">
        <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-4">
          Elektro vs. Benziner vs. Diesel - Kosten pro 100 km
        </h2>
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="text-left text-xs text-gray-500 dark:text-gray-400 border-b border-gray-100 dark:border-gray-700">
                <th class="pb-2 font-medium">Antrieb</th>
                <th class="pb-2 font-medium text-right">Verbrauch</th>
                <th class="pb-2 font-medium text-right">Preis</th>
                <th class="pb-2 font-medium text-right">Kosten/100km</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-50 dark:divide-gray-700">
              <tr v-for="row in vergleichsTabelle" :key="row.label">
                <td class="py-2.5 font-medium" :class="row.highlight ? 'text-green-600 dark:text-green-400' : 'text-gray-700 dark:text-gray-300'">
                  {{ row.label }}
                </td>
                <td class="py-2.5 text-right text-gray-600 dark:text-gray-400">{{ row.verbrauch }}</td>
                <td class="py-2.5 text-right text-gray-600 dark:text-gray-400">{{ row.preis }}</td>
                <td class="py-2.5 text-right font-bold" :class="row.highlight ? 'text-green-600 dark:text-green-400' : 'text-gray-700 dark:text-gray-300'">
                  {{ row.kosten }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="text-xs text-gray-400 mt-3">
          Benzin/Diesel: Durchschnittspreis DE 2025 (ca. 1,80 €/L Benzin, 1,65 €/L Diesel).
          E-Auto: Dein eingestellter Strompreis ({{ strompreis.toFixed(2) }} €/kWh).
        </p>
      </div>

      <!-- FAQ -->
      <div class="space-y-5 text-sm text-gray-600 dark:text-gray-400 mb-8">
        <div>
          <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-1">
            Was kostet ein Elektroauto pro Kilometer?
          </h2>
          <p>
            Bei einem Haushaltsstrompreis von 33 ct/kWh und einem realen Verbrauch von 18 kWh/100 km
            kostet ein E-Auto <strong>5,94 € pro 100 km</strong> bzw. rund <strong>6 Cent pro km</strong>.
            Zum Vergleich: ein Benziner mit 7 L/100 km bei 1,80 €/L kostet 12,60 € pro 100 km.
          </p>
        </div>
        <div>
          <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-1">
            Welche Gesamtkosten hat ein E-Auto gegenüber einem Benziner?
          </h2>
          <p>
            Die reine Energiekosten-Ersparnis liegt bei durchschnittlich 6-8 Cent/km - bei 15.000 km/Jahr
            sind das 900-1.200 € pro Jahr. Hinzu kommen geringere Wartungskosten (kein Ölwechsel,
            weniger Verschleiß). Die Anschaffungskosten sind höher, gleichen sich je nach Modell
            nach 3-6 Jahren aus.
          </p>
        </div>
        <div>
          <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-1">
            Lohnt sich ein E-Auto wenn ich viel auf der Autobahn fahre?
          </h2>
          <p>
            Bei viel Autobahnanteil steigt der Verbrauch auf 22-28 kWh/100 km - das entspricht
            7-9 € pro 100 km bei 33 ct/kWh, oder bis zu 14-18 € wenn hauptsächlich Schnelllader
            genutzt werden. Der Vorteil gegenüber Benzinern ist dann geringer, bleibt aber oft positiv.
          </p>
        </div>
      </div>

      <!-- CTA -->
      <div class="bg-gradient-to-br from-gray-900 to-green-900 rounded-xl p-6 text-center">
        <h2 class="text-lg font-bold text-white mb-2">Deine echten Kosten pro km</h2>
        <p class="text-gray-300 text-sm mb-4">
          Diese Zahlen sind Durchschnittswerte. Deine echten Kosten hangen von deinem Fahrverhalten,
          deiner Route und deinem Stromtarif ab. EV Monitor berechnet sie exakt aus deinen Daten.
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
  title: 'Elektroauto Kosten pro km - echte Verbrauchsdaten | EV Monitor',
  meta: [
    {
      name: 'description',
      content: 'Wieviel kostet ein Elektroauto pro Kilometer wirklich? Stromkosten pro 100 km für Tesla, VW ID, BMW und mehr - mit echten Community-Verbrauchsdaten und Benziner-Vergleich.'
    },
    { property: 'og:title', content: 'Elektroauto Kosten pro km - Real-Daten | EV Monitor' },
    { property: 'og:description', content: 'Echte Ladekosten pro 100 km für die meistgefahrenen E-Autos - aus Community-Daten, mit Benziner-Vergleich.' }
  ],
  link: [{ rel: 'canonical', href: 'https://ev-monitor.net/elektroauto-kosten-pro-km' }],
  script: [
    {
      type: 'application/ld+json',
      innerHTML: JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'Article',
        headline: 'Elektroauto Kosten pro km - echte Verbrauchsdaten',
        description: 'Stromkosten pro 100 km für die meistgefahrenen Elektroautos aus echten Fahrdaten',
        url: 'https://ev-monitor.net/elektroauto-kosten-pro-km',
        author: { '@type': 'Person', name: 'Sebastian Ihle' },
        publisher: { '@type': 'Organization', name: 'EV Monitor', url: 'https://ev-monitor.net' },
        datePublished: '2026-04-30',
        breadcrumb: {
          '@type': 'BreadcrumbList',
          itemListElement: [
            { '@type': 'ListItem', position: 1, name: 'EV Monitor', item: 'https://ev-monitor.net' },
            { '@type': 'ListItem', position: 2, name: 'Kosten pro km', item: 'https://ev-monitor.net/elektroauto-kosten-pro-km' }
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
            name: 'Was kostet ein Elektroauto pro Kilometer?',
            acceptedAnswer: {
              '@type': 'Answer',
              text: 'Bei einem Haushaltsstrompreis von 33 ct/kWh und einem realen Verbrauch von 18 kWh/100 km kostet ein E-Auto 5,94 € pro 100 km bzw. rund 6 Cent pro km. Zum Vergleich: ein Benziner mit 7 L/100 km bei 1,80 €/L kostet 12,60 € pro 100 km.'
            }
          },
          {
            '@type': 'Question',
            name: 'Welche Gesamtkosten hat ein E-Auto gegenüber einem Benziner?',
            acceptedAnswer: {
              '@type': 'Answer',
              text: 'Die reine Energiekosten-Ersparnis liegt bei durchschnittlich 6-8 Cent/km - bei 15.000 km/Jahr sind das 900-1.200 € pro Jahr. Hinzu kommen geringere Wartungskosten (kein Ölwechsel, weniger Verschleiß). Die Anschaffungskosten sind höher, gleichen sich je nach Modell nach 3-6 Jahren aus.'
            }
          },
          {
            '@type': 'Question',
            name: 'Lohnt sich ein E-Auto wenn ich viel auf der Autobahn fahre?',
            acceptedAnswer: {
              '@type': 'Answer',
              text: 'Bei viel Autobahnanteil steigt der Verbrauch auf 22-28 kWh/100 km - das entspricht 7-9 € pro 100 km bei 33 ct/kWh, oder bis zu 14-18 € wenn hauptsächlich Schnelllader genutzt werden. Der Vorteil gegenüber Benzinern ist dann geringer, bleibt aber oft positiv.'
            }
          }
        ]
      })
    }
  ]
});

const loading = ref(true);
const models = ref<TopModelPreview[]>([]);
const strompreis = ref(0.33);

const topModels = computed(() =>
  [...models.value]
    .filter(m => m.avgConsumptionKwhPer100km !== null && m.logCount >= 10)
    .sort((a, b) => (a.avgConsumptionKwhPer100km ?? 99) - (b.avgConsumptionKwhPer100km ?? 99))
    .slice(0, 15)
);

const vergleichsTabelle = computed(() => [
  {
    label: 'E-Auto (effizient, z.B. Tesla M3)',
    verbrauch: '15 kWh/100km',
    preis: `${strompreis.value.toFixed(2)} €/kWh`,
    kosten: `${(15 * strompreis.value).toFixed(2)} €`,
    highlight: true
  },
  {
    label: 'E-Auto (Durchschnitt)',
    verbrauch: '19 kWh/100km',
    preis: `${strompreis.value.toFixed(2)} €/kWh`,
    kosten: `${(19 * strompreis.value).toFixed(2)} €`,
    highlight: true
  },
  {
    label: 'E-Auto (SUV)',
    verbrauch: '24 kWh/100km',
    preis: `${strompreis.value.toFixed(2)} €/kWh`,
    kosten: `${(24 * strompreis.value).toFixed(2)} €`,
    highlight: true
  },
  {
    label: 'Benziner (Kompakt)',
    verbrauch: '6.5 L/100km',
    preis: '1,80 €/L',
    kosten: '11,70 €',
    highlight: false
  },
  {
    label: 'Benziner (SUV)',
    verbrauch: '9.0 L/100km',
    preis: '1,80 €/L',
    kosten: '16,20 €',
    highlight: false
  },
  {
    label: 'Diesel (Kompakt)',
    verbrauch: '5.5 L/100km',
    preis: '1,65 €/L',
    kosten: '9,08 €',
    highlight: false
  },
]);

function formatKosten(kwhPer100km: number | null): string {
  if (!kwhPer100km) return '-';
  return (kwhPer100km * strompreis.value).toFixed(2);
}

onMounted(async () => {
  try {
    models.value = await getTopModels(50);
  } finally {
    loading.value = false;
  }
});
</script>
