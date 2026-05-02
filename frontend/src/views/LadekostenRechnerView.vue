<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-950">
    <PublicNav />

    <main class="max-w-2xl mx-auto px-4 py-8">
      <div class="text-center mb-8">
        <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 mb-2 leading-tight">
          Ladekosten Rechner Elektroauto
        </h1>
        <p class="text-gray-500 dark:text-gray-400 text-sm sm:text-base max-w-xl mx-auto">
          Berechne was dich dein E-Auto pro Monat, pro 100&nbsp;km und im Vergleich zum Benziner wirklich kostet.
        </p>
      </div>

      <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm border border-gray-100 dark:border-gray-700 p-5 mb-6">
        <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-4">Deine Eingaben</h2>
        <div class="space-y-5">
          <div>
            <label for="strompreis" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Strompreis</label>
            <div class="flex items-center gap-3">
              <input v-model.number="strompreis" type="range" min="0.10" max="0.60" step="0.01" class="flex-1 accent-green-500" aria-labelledby="strompreis" />
              <div class="flex items-center gap-1 w-24">
                <input id="strompreis" v-model.number="strompreis" type="number" min="0.10" max="0.60" step="0.01" class="w-full border border-gray-200 dark:border-gray-600 rounded-lg px-2 py-1.5 text-sm text-right bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
                <span class="text-sm text-gray-500 dark:text-gray-400 shrink-0">€/kWh</span>
              </div>
            </div>
            <div class="flex justify-between text-xs text-gray-400 mt-0.5 px-0.5"><span>0,10 €</span><span>0,60 €</span></div>
          </div>

          <div>
            <label for="verbrauch" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Durchschnittsverbrauch</label>
            <div class="flex items-center gap-3">
              <input v-model.number="verbrauch" type="range" min="10" max="35" step="0.5" class="flex-1 accent-green-500" aria-labelledby="verbrauch" />
              <div class="flex items-center gap-1 w-28">
                <input id="verbrauch" v-model.number="verbrauch" type="number" min="10" max="35" step="0.5" class="w-full border border-gray-200 dark:border-gray-600 rounded-lg px-2 py-1.5 text-sm text-right bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
                <span class="text-sm text-gray-500 dark:text-gray-400 shrink-0">kWh/100</span>
              </div>
            </div>
            <div class="flex justify-between text-xs text-gray-400 mt-0.5 px-0.5"><span>10</span><span>35</span></div>
          </div>

          <div>
            <label for="km-monat" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Fahrleistung pro Monat</label>
            <div class="flex items-center gap-3">
              <input v-model.number="kmProMonat" type="range" min="200" max="4000" step="50" class="flex-1 accent-green-500" aria-labelledby="km-monat" />
              <div class="flex items-center gap-1 w-24">
                <input id="km-monat" v-model.number="kmProMonat" type="number" min="200" max="4000" step="50" class="w-full border border-gray-200 dark:border-gray-600 rounded-lg px-2 py-1.5 text-sm text-right bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
                <span class="text-sm text-gray-500 dark:text-gray-400 shrink-0">km</span>
              </div>
            </div>
            <div class="flex justify-between text-xs text-gray-400 mt-0.5 px-0.5"><span>200 km</span><span>4.000 km</span></div>
          </div>

          <div class="border-t border-gray-100 dark:border-gray-700 pt-4">
            <button
              @click="showBenziner = !showBenziner"
              :aria-expanded="showBenziner"
              class="flex items-center gap-1.5 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 transition-colors"
            >
              <ChevronDownIcon v-if="showBenziner" class="h-4 w-4" />
              <ChevronRightIcon v-else class="h-4 w-4" />
              Vergleich mit Benziner {{ showBenziner ? 'ausblenden' : 'anzeigen' }}
            </button>
            <div v-if="showBenziner" class="mt-3 space-y-4">
              <div>
                <label for="benzinpreis" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Benzinpreis</label>
                <div class="flex items-center gap-3">
                  <input v-model.number="benzinpreis" type="range" min="1.20" max="2.50" step="0.01" class="flex-1 accent-orange-400" aria-labelledby="benzinpreis" />
                  <div class="flex items-center gap-1 w-24">
                    <input id="benzinpreis" v-model.number="benzinpreis" type="number" min="1.20" max="2.50" step="0.01" class="w-full border border-gray-200 dark:border-gray-600 rounded-lg px-2 py-1.5 text-sm text-right bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
                    <span class="text-sm text-gray-500 dark:text-gray-400 shrink-0">€/L</span>
                  </div>
                </div>
              </div>
              <div>
                <label for="benzin-verbrauch" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Benzinverbrauch</label>
                <div class="flex items-center gap-3">
                  <input v-model.number="benzinVerbrauch" type="range" min="4" max="15" step="0.5" class="flex-1 accent-orange-400" aria-labelledby="benzin-verbrauch" />
                  <div class="flex items-center gap-1 w-28">
                    <input id="benzin-verbrauch" v-model.number="benzinVerbrauch" type="number" min="4" max="15" step="0.5" class="w-full border border-gray-200 dark:border-gray-600 rounded-lg px-2 py-1.5 text-sm text-right bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
                    <span class="text-sm text-gray-500 dark:text-gray-400 shrink-0">L/100km</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-2 gap-3 mb-6">
        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-4 text-center">
          <div class="text-2xl font-bold text-green-600 dark:text-green-400">{{ formatEur(computed_kostenPro100km) }}</div>
          <div class="text-xs text-gray-500 dark:text-gray-400 mt-1">Kosten pro 100 km</div>
        </div>
        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-4 text-center">
          <div class="text-2xl font-bold text-green-600 dark:text-green-400">{{ formatEur(computed_kostenProMonat) }}</div>
          <div class="text-xs text-gray-500 dark:text-gray-400 mt-1">Ladekosten/Monat</div>
        </div>
        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-4 text-center">
          <div class="text-2xl font-bold text-green-600 dark:text-green-400">{{ formatEur(computed_kostenProKm) }}</div>
          <div class="text-xs text-gray-500 dark:text-gray-400 mt-1">Kosten pro km</div>
        </div>
        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-4 text-center">
          <div class="text-2xl font-bold text-green-600 dark:text-green-400">{{ formatEur(computed_kostenProMonat * 12) }}</div>
          <div class="text-xs text-gray-500 dark:text-gray-400 mt-1">Ladekosten/Jahr</div>
        </div>
      </div>

      <div v-if="showBenziner" class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-5 mb-6">
        <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-4">Vergleich mit Benziner</h2>
        <div class="space-y-3">
          <div class="flex justify-between items-center text-sm">
            <span class="text-gray-600 dark:text-gray-400">Benzinkosten pro Monat</span>
            <span class="font-semibold text-orange-500">{{ formatEur(computed_benzinProMonat) }}</span>
          </div>
          <div class="flex justify-between items-center text-sm">
            <span class="text-gray-600 dark:text-gray-400">Ladekosten pro Monat</span>
            <span class="font-semibold text-green-600 dark:text-green-400">{{ formatEur(computed_kostenProMonat) }}</span>
          </div>
          <div class="border-t border-gray-100 dark:border-gray-700 pt-3 flex justify-between items-center">
            <span class="text-sm font-medium text-gray-700 dark:text-gray-300">Ersparnis pro Monat</span>
            <span :class="computed_ersparnisMonat >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-500'" class="text-lg font-bold">
              {{ computed_ersparnisMonat >= 0 ? '+' : '' }}{{ formatEur(computed_ersparnisMonat) }}
            </span>
          </div>
          <div class="flex justify-between items-center">
            <span class="text-sm font-medium text-gray-700 dark:text-gray-300">Ersparnis pro Jahr</span>
            <span :class="computed_ersparnisMonat >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-500'" class="text-xl font-bold">
              {{ computed_ersparnisMonat >= 0 ? '+' : '' }}{{ formatEur(computed_ersparnisMonat * 12) }}
            </span>
          </div>
        </div>
      </div>

      <div class="bg-blue-50 dark:bg-blue-950/30 border border-blue-100 dark:border-blue-900 rounded-xl p-4 mb-6 text-sm text-blue-700 dark:text-blue-300">
        <p><strong>Hinweis:</strong> Der Rechner gibt einen Schätzwert auf Basis der eingegebenen Durchschnittswerte. Der tatsächliche Verbrauch schwankt je nach Fahrweise, Temperatur und Streckentyp. Mit einem Ladetagebuch siehst du deine <em>echten</em> Kosten.</p>
      </div>

      <div class="bg-gradient-to-br from-gray-900 to-green-900 rounded-xl p-6 text-center">
        <h2 class="text-lg font-bold text-white mb-2">Echte Kosten statt Schätzwerte</h2>
        <p class="text-gray-300 text-sm mb-4">EV Monitor trackt meine Ladekosten automatisch - mit echtem Verbrauch, echten Preisen, echter Reichweite. Kostenlos, keine Kreditkarte nötig.</p>
        <a href="/register" class="inline-flex items-center gap-2 bg-green-500 hover:bg-green-400 text-white font-semibold px-6 py-3 rounded-lg transition-colors text-sm">
          Kostenlos starten
          <ArrowRightIcon class="h-4 w-4" />
        </a>
      </div>

      <div class="mt-10 space-y-6 text-sm text-gray-600 dark:text-gray-400">
        <div>
          <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-2">Wie berechne ich die Ladekosten meines Elektroautos?</h2>
          <p>Die Formel ist einfach: <strong>Ladekosten pro 100 km = Verbrauch (kWh/100 km) × Strompreis (€/kWh)</strong>. Bei einem Verbrauch von 18 kWh/100 km und einem Strompreis von 0,33 €/kWh kostet dich jede 100 km exakt 5,94 €. Zum Vergleich: ein Benziner mit 7 L/100 km bei 1,80 €/L kostet 12,60 € pro 100 km.</p>
        </div>
        <div>
          <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-2">Welchen Strompreis soll ich verwenden?</h2>
          <p>Wenn du hauptsächlich zu Hause lädst, ist dein Haushaltsstrompreis der richtige Wert - in Deutschland liegt er aktuell bei etwa 28-40 Cent/kWh. Nutzt du öfter öffentliche Schnelllader, liegt der Preis je nach Anbieter zwischen 50 und 80 Cent/kWh. Viele EV-Fahrer laden zu 80-90% zuhause.</p>
        </div>
        <div>
          <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-2">Warum weicht mein realer Verbrauch vom WLTP-Wert ab?</h2>
          <p>Der WLTP-Wert ist ein Laborwert unter Idealbedingungen. In der Praxis beeinflussen Temperatur (-30% im Winter), Fahrweise (Autobahn deutlich mehr als Stadtverkehr) und Klimaanlage den Verbrauch erheblich. Die Community-Daten von EV Monitor zeigen die echten Werte für jedes Modell.</p>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useHead } from '@unhead/vue';
import { ArrowRightIcon, ChevronDownIcon, ChevronRightIcon } from '@heroicons/vue/24/outline';
import PublicNav from '../components/shared/PublicNav.vue';
import { kostenPro100km, kostenProKm, kostenProMonat, benzinKostenProMonat, ersparnisProMonat } from '../utils/ladekostenCalc';

useHead({
  title: 'Ladekosten Rechner Elektroauto - Kosten pro km & Monat berechnen | EV Monitor',
  meta: [
    { name: 'description', content: 'Kostenloser Ladekosten-Rechner für Elektroautos: Berechne deine Ladekosten pro 100 km, pro Monat und im Vergleich zum Benziner. Mit echten Community-Verbrauchswerten.' },
    { property: 'og:title', content: 'Ladekosten Rechner Elektroauto | EV Monitor' },
    { property: 'og:description', content: 'Ladekosten pro 100 km, Monat und Jahr berechnen - kostenlos und ohne Anmeldung.' }
  ],
  link: [{ rel: 'canonical', href: 'https://ev-monitor.net/ladekosten-rechner' }],
  script: [
    {
      type: 'application/ld+json',
      innerHTML: JSON.stringify([
        {
          '@context': 'https://schema.org',
          '@type': 'WebApplication',
          name: 'EV Monitor Ladekosten Rechner',
          description: 'Kostenloser Rechner für Ladekosten von Elektroautos - pro km, Monat und im Vergleich zum Benziner',
          url: 'https://ev-monitor.net/ladekosten-rechner',
          applicationCategory: 'UtilityApplication',
          featureList: ['Ladekosten pro 100 km', 'Monatliche Ladekosten', 'Jahreskosten', 'Benziner-Vergleich', 'Ersparnis-Berechnung'],
          offers: { '@type': 'Offer', price: '0', priceCurrency: 'EUR' },
          breadcrumb: { '@type': 'BreadcrumbList', itemListElement: [{ '@type': 'ListItem', position: 1, name: 'EV Monitor', item: 'https://ev-monitor.net' }, { '@type': 'ListItem', position: 2, name: 'Ladekosten Rechner', item: 'https://ev-monitor.net/ladekosten-rechner' }] }
        },
        {
          '@context': 'https://schema.org',
          '@type': 'FAQPage',
          mainEntity: [
            { '@type': 'Question', name: 'Wie berechne ich die Ladekosten meines Elektroautos?', acceptedAnswer: { '@type': 'Answer', text: 'Ladekosten pro 100 km = Verbrauch (kWh/100 km) × Strompreis (€/kWh). Bei 18 kWh/100 km und 0,33 €/kWh kostet dich jede 100 km exakt 5,94 €.' } },
            { '@type': 'Question', name: 'Welchen Strompreis soll ich verwenden?', acceptedAnswer: { '@type': 'Answer', text: 'Bei Heimladung deinen Haushaltsstrompreis (DE: 28-40 Cent/kWh). Bei öffentlichen Schnellladern 50-80 Cent/kWh. Die meisten EV-Fahrer laden zu 80-90% zuhause.' } },
            { '@type': 'Question', name: 'Warum weicht mein realer Verbrauch vom WLTP-Wert ab?', acceptedAnswer: { '@type': 'Answer', text: 'Der WLTP-Wert ist ein Laborwert. Temperatur, Fahrweise und Klimaanlage beeinflussen den Verbrauch erheblich - bis zu -30% im Winter.' } }
          ]
        }
      ])
    }
  ]
});

const strompreis = ref(0.33);
const verbrauch = ref(18.0);
const kmProMonat = ref(1000);
const benzinpreis = ref(1.85);
const benzinVerbrauch = ref(7.5);
const showBenziner = ref(false);

const computed_kostenPro100km = computed(() => kostenPro100km(strompreis.value, verbrauch.value));
const computed_kostenProKm = computed(() => kostenProKm(strompreis.value, verbrauch.value));
const computed_kostenProMonat = computed(() => kostenProMonat(strompreis.value, verbrauch.value, kmProMonat.value));
const computed_benzinProMonat = computed(() => benzinKostenProMonat(benzinpreis.value, benzinVerbrauch.value, kmProMonat.value));
const computed_ersparnisMonat = computed(() => ersparnisProMonat(strompreis.value, verbrauch.value, benzinpreis.value, benzinVerbrauch.value, kmProMonat.value));

function formatEur(value: number): string {
  return value.toLocaleString('de-DE', { style: 'currency', currency: 'EUR', minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
</script>
