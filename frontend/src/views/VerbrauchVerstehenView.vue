<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-950">
    <PublicNav />

    <main class="max-w-2xl mx-auto px-4 py-8">
      <!-- Hero -->
      <div class="text-center mb-8">
        <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 mb-2 leading-tight">
          E-Auto Verbrauch verstehen und tracken
        </h1>
        <p class="text-gray-500 dark:text-gray-400 text-sm sm:text-base max-w-xl mx-auto">
          Warum verbraucht dein Elektroauto mehr als angegeben?
          Was beeinflusst den Verbrauch? Wie trackst du ihn sinnvoll?
        </p>
      </div>

      <!-- Inhalt: WLTP vs Real -->
      <div class="space-y-6">

        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-5">
          <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-3">
            WLTP-Verbrauch vs. realer Verbrauch
          </h2>
          <p class="text-sm text-gray-600 dark:text-gray-400 mb-4">
            Der WLTP-Wert wird im Labor bei konstanten 23 Grad, ohne Heizung, ohne Klimaanlage
            und auf einem definierten Geschwindigkeitsprofil gemessen. Die Realität sieht anders aus:
          </p>
          <!-- Visualisierung -->
          <div class="space-y-3">
            <div
              v-for="factor in factors"
              :key="factor.label"
              class="flex items-center gap-3 text-sm"
            >
              <div class="w-32 shrink-0 text-gray-600 dark:text-gray-400">{{ factor.label }}</div>
              <div class="flex-1 h-3 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
                <div
                  class="h-full rounded-full transition-all"
                  :style="{ backgroundColor: factor.bgHex, width: factor.impact + '%' }"
                ></div>
              </div>
              <div class="w-24 text-right text-xs font-medium" :class="factor.textColor">
                {{ factor.label2 }}
              </div>
            </div>
          </div>
          <p class="text-xs text-gray-400 mt-3">
            Prozentuale Abweichung vom WLTP-Basisverbrauch (Quelle: Community-Daten + ADAC-Messungen)
          </p>
        </div>

        <!-- Einflussfaktoren -->
        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-5">
          <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-3">
            Die 4 größten Einflussfaktoren
          </h2>
          <div class="space-y-4 text-sm text-gray-600 dark:text-gray-400">
            <div class="flex gap-3">
              <div class="shrink-0 w-8 h-8 bg-blue-100 dark:bg-blue-900/30 rounded-lg flex items-center justify-center">
                <span class="text-blue-600 dark:text-blue-400 font-bold text-xs">1</span>
              </div>
              <div>
                <div class="font-medium text-gray-800 dark:text-gray-200 mb-0.5">Temperatur</div>
                <p>
                  Kalte Akkus haben weniger nutzbare Kapazität - und die Heizung verbraucht bis zu 4 kW zusätzlich.
                  Im Winter sind Mehrverbräuche von 30-50% gegenüber Sommer normal. Vorkonditionierung
                  (Aufwärmen während dem Laden) reduziert den Effekt deutlich.
                </p>
              </div>
            </div>
            <div class="flex gap-3">
              <div class="shrink-0 w-8 h-8 bg-orange-100 dark:bg-orange-900/30 rounded-lg flex items-center justify-center">
                <span class="text-orange-600 dark:text-orange-400 font-bold text-xs">2</span>
              </div>
              <div>
                <div class="font-medium text-gray-800 dark:text-gray-200 mb-0.5">Geschwindigkeit</div>
                <p>
                  Luftwiderstand steigt mit dem Quadrat der Geschwindigkeit. Bei 130 km/h
                  verbraucht dasselbe Auto oft doppelt so viel wie bei 80 km/h.
                  Wer viel Autobahn fährt, rechnet mit 25-50% Mehrverbrauch gegenüber Stadtverkehr.
                </p>
              </div>
            </div>
            <div class="flex gap-3">
              <div class="shrink-0 w-8 h-8 bg-purple-100 dark:bg-purple-900/30 rounded-lg flex items-center justify-center">
                <span class="text-purple-600 dark:text-purple-400 font-bold text-xs">3</span>
              </div>
              <div>
                <div class="font-medium text-gray-800 dark:text-gray-200 mb-0.5">Batterie-Alterung (SoH)</div>
                <p>
                  Mit jedem Jahr sinkt die nutzbare Kapazität leicht. Ein Auto mit 90% SoH hat
                  effektiv 10% weniger Reichweite als neu. Wer seinen SoH kennt, kann realistischer planen.
                </p>
              </div>
            </div>
            <div class="flex gap-3">
              <div class="shrink-0 w-8 h-8 bg-teal-100 dark:bg-teal-900/30 rounded-lg flex items-center justify-center">
                <span class="text-teal-600 dark:text-teal-400 font-bold text-xs">4</span>
              </div>
              <div>
                <div class="font-medium text-gray-800 dark:text-gray-200 mb-0.5">Standby-Verbrauch</div>
                <p>
                  Fahrzeug-Software, Klimatisierung im Stand und Over-the-Air-Updates ziehen Strom
                  auch ohne Fahrt. 1-3 kWh pro Tag sind normal - das beeinflusst den messbaren
                  Verbrauch pro 100 km, wenn man die Energie einrechnet.
                </p>
              </div>
            </div>
          </div>
        </div>

        <!-- Wie sinnvoll tracken -->
        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-5">
          <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-3">
            Verbrauch richtig tracken
          </h2>
          <p class="text-sm text-gray-600 dark:text-gray-400 mb-3">
            Der Bordcomputer zeigt nur den Motorzug während der Fahrt - nicht Standby, nicht Ladeverluste.
            Wer seinen echten Verbrauch wissen will, braucht einen anderen Ansatz:
          </p>
          <ul class="space-y-2 text-sm text-gray-600 dark:text-gray-400">
            <li class="flex items-start gap-2">
              <CheckIcon class="h-4 w-4 text-green-500 shrink-0 mt-0.5" />
              <span>
                <strong class="text-gray-800 dark:text-gray-200">Geladene kWh messen</strong> - an der Wallbox oder
                Ladestation, nicht was der Bordcomputer sagt
              </span>
            </li>
            <li class="flex items-start gap-2">
              <CheckIcon class="h-4 w-4 text-green-500 shrink-0 mt-0.5" />
              <span>
                <strong class="text-gray-800 dark:text-gray-200">km zwischen zwei Ladevorgangen</strong> notieren -
                das ergibt den Verbrauch für genau diese Strecke inklusive aller Verluste
              </span>
            </li>
            <li class="flex items-start gap-2">
              <CheckIcon class="h-4 w-4 text-green-500 shrink-0 mt-0.5" />
              <span>
                <strong class="text-gray-800 dark:text-gray-200">SoC-Korrekturfaktor</strong> mitrechnen -
                wenn du nicht immer auf denselben SoC ladst, muss die Differenz einberechnet werden
              </span>
            </li>
            <li class="flex items-start gap-2">
              <CheckIcon class="h-4 w-4 text-green-500 shrink-0 mt-0.5" />
                <span>
                <strong class="text-gray-800 dark:text-gray-200">Median statt Mittelwert</strong> für den Jahresdurchschnitt -
                einzelne Langstrecken oder extreme Wintertage verzerren den Mittelwert stark
              </span>
            </li>
          </ul>
        </div>

        <!-- Community Stats -->
        <div v-if="stats" class="bg-green-50 dark:bg-green-950/30 border border-green-100 dark:border-green-900 rounded-xl p-4 text-center">
          <p class="text-sm text-green-700 dark:text-green-400">
            EV Monitor berechnet den Verbrauch so für
            <strong>{{ stats.validTripCount.toLocaleString('de-DE') }} Ladevorgänge</strong>
            von <strong>{{ stats.userCount.toLocaleString('de-DE') }} Fahrern</strong>.
            Die Methodik ist <a href="/consumption-methodology" class="underline">hier erklart</a>.
          </p>
        </div>

        <!-- FAQ -->
        <div class="space-y-4 text-sm text-gray-600 dark:text-gray-400">
          <div>
            <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-1">
              Warum zeigt mein E-Auto mehr Verbrauch als der WLTP-Wert?
            </h2>
            <p>
              Weil WLTP unter Idealbedingungen gemessen wird. Temperatur, Geschwindigkeit, Heizung
              und Klimaanlage sind die Haupttreiber. Ein Verbrauch von 10-30% über WLTP ist völlig
              normal und kein Zeichen für einen Defekt.
            </p>
          </div>
          <div>
            <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-1">
              Stimmt der Verbrauch im Bordcomputer?
            </h2>
            <p>
              Der Bordcomputer ist ein nützlicher Richtwert, misst aber nur den Antriebsstrom
              während der Fahrt. Standby-Verluste, Ladeverluste und die Klimatisierung im Stand
              tauchen dort nicht auf. Der echte Verbrauch aus der Steckdose ist höher.
            </p>
          </div>
          <div>
            <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-1">
              Wie verringere ich den Verbrauch im Winter?
            </h2>
            <p>
              Vorkonditionieren während dem Laden ist der effektivste Hebel - der Akku wird auf
              Betriebstemperatur gebracht ohne Akkustrom zu nutzen. Sitzheizung statt Geblase spart
              2-3 kW. Eco-Modus und Tempolimit auf der Autobahn helfen ebenfalls.
            </p>
          </div>
        </div>
      </div>

      <!-- CTA -->
      <div class="mt-8 bg-gradient-to-br from-gray-900 to-green-900 rounded-xl p-6 text-center">
        <h2 class="text-lg font-bold text-white mb-2">Deinen echten Verbrauch kennen</h2>
        <p class="text-gray-300 text-sm mb-4">
          EV Monitor berechnet deinen Verbrauch korrekt - aus geladenen kWh und gefahrenen km.
          Nicht aus dem Bordcomputer.
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
import { ref, onMounted } from 'vue';
import { useHead } from '@unhead/vue';
import { ArrowRightIcon, CheckIcon } from '@heroicons/vue/24/outline';
import PublicNav from '../components/shared/PublicNav.vue';
import { getPlatformStats, type PlatformStats } from '../api/publicModelService';

useHead({
  title: 'E-Auto Verbrauch verstehen - WLTP vs. real, Einflussfaktoren | EV Monitor',
  meta: [
    {
      name: 'description',
      content: 'Warum verbraucht dein E-Auto mehr als angegeben? WLTP vs. realer Verbrauch erklart: Temperatur, Autobahn, SoH - und wie du deinen Verbrauch richtig trackst.'
    },
    { property: 'og:title', content: 'E-Auto Verbrauch verstehen | EV Monitor' },
    { property: 'og:description', content: 'WLTP vs. real: Einflussfaktoren auf den E-Auto Verbrauch und wie du ihn sinnvoll trackst.' }
  ],
  link: [{ rel: 'canonical', href: 'https://ev-monitor.net/verbrauch-verstehen' }],
  script: [
    {
      type: 'application/ld+json',
      innerHTML: JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'Article',
        headline: 'E-Auto Verbrauch verstehen - WLTP vs. real, Einflussfaktoren',
        description: 'Warum verbraucht dein E-Auto mehr als angegeben? WLTP vs. realer Verbrauch erklärt.',
        url: 'https://ev-monitor.net/verbrauch-verstehen',
        author: { '@type': 'Person', name: 'Sebastian Ihle' },
        publisher: { '@type': 'Organization', name: 'EV Monitor', url: 'https://ev-monitor.net' },
        datePublished: '2026-04-30',
        breadcrumb: {
          '@type': 'BreadcrumbList',
          itemListElement: [
            { '@type': 'ListItem', position: 1, name: 'EV Monitor', item: 'https://ev-monitor.net' },
            { '@type': 'ListItem', position: 2, name: 'Verbrauch verstehen', item: 'https://ev-monitor.net/verbrauch-verstehen' }
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
            name: 'Warum zeigt mein E-Auto mehr Verbrauch als der WLTP-Wert?',
            acceptedAnswer: {
              '@type': 'Answer',
              text: 'Weil WLTP unter Idealbedingungen gemessen wird. Temperatur, Geschwindigkeit, Heizung und Klimaanlage sind die Haupttreiber. Ein Verbrauch von 10-30% über WLTP ist völlig normal und kein Zeichen für einen Defekt.'
            }
          },
          {
            '@type': 'Question',
            name: 'Stimmt der Verbrauch im Bordcomputer?',
            acceptedAnswer: {
              '@type': 'Answer',
              text: 'Der Bordcomputer ist ein nützlicher Richtwert, misst aber nur den Antriebsstrom während der Fahrt. Standby-Verluste, Ladeverluste und die Klimatisierung im Stand tauchen dort nicht auf. Der echte Verbrauch aus der Steckdose ist höher.'
            }
          },
          {
            '@type': 'Question',
            name: 'Wie verringere ich den Verbrauch im Winter?',
            acceptedAnswer: {
              '@type': 'Answer',
              text: 'Vorkonditionieren während dem Laden ist der effektivste Hebel - der Akku wird auf Betriebstemperatur gebracht ohne Akkustrom zu nutzen. Sitzheizung statt Gebläse spart 2-3 kW. Eco-Modus und Tempolimit auf der Autobahn helfen ebenfalls.'
            }
          }
        ]
      })
    }
  ]
});

const stats = ref<PlatformStats | null>(null);

const factors = [
  { label: 'Winter (-15°C)', label2: '+40% Verbrauch', impact: 90, bgHex: '#3b82f6', textColor: 'text-blue-600 dark:text-blue-400' },
  { label: 'Autobahn 130', label2: '+35% Verbrauch', impact: 75, bgHex: '#f97316', textColor: 'text-orange-600 dark:text-orange-400' },
  { label: 'Herbst (10°C)', label2: '+20% Verbrauch', impact: 50, bgHex: '#facc15', textColor: 'text-yellow-600 dark:text-yellow-400' },
  { label: 'Stadtverkehr', label2: '+10% Verbrauch', impact: 30, bgHex: '#4ade80', textColor: 'text-green-600 dark:text-green-400' },
  { label: 'WLTP-Basis', label2: '0% Abweichung', impact: 15, bgHex: '#9ca3af', textColor: 'text-gray-500 dark:text-gray-400' },
];

onMounted(async () => {
  try {
    stats.value = await getPlatformStats();
  } catch {
    // bleibt null
  }
});
</script>
