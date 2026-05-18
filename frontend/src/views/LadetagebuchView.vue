<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-950">
    <PublicNav />

    <main class="max-w-2xl mx-auto px-4 py-8">
      <!-- Hero -->
      <div class="text-center mb-8">
        <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 mb-2 leading-tight">
          Digitales Ladetagebuch fur dein Elektroauto
        </h1>
        <p class="text-gray-500 dark:text-gray-400 text-sm sm:text-base max-w-xl mx-auto">
          Alle Ladevorgange an einem Ort - Kosten, Verbrauch, Reichweite und SoH auf einen Blick.
          Kostenlos, ohne Kreditkarte.
        </p>
        <div class="mt-5">
          <a
            href="/register"
            class="inline-flex items-center gap-2 bg-green-500 hover:bg-green-400 text-white font-semibold px-6 py-3 rounded-lg transition-colors text-sm"
          >
            Ladetagebuch starten
            <ArrowRightIcon class="h-4 w-4" />
          </a>
          <p class="text-xs text-gray-400 mt-2">Kostenlos - kein Abo notwendig</p>
        </div>
      </div>

      <!-- Feature Cards -->
      <div class="space-y-4 mb-10">
        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-5 flex gap-4">
          <div class="shrink-0 w-10 h-10 bg-green-100 dark:bg-green-900/30 rounded-lg flex items-center justify-center">
            <BoltIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
          </div>
          <div>
            <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-1">Jeden Ladevorgang erfassen</h2>
            <p class="text-sm text-gray-500 dark:text-gray-400">
              Manuell oder automatisch per AutoSync (Tesla, VW, Skoda, SEAT, CUPRA und mehr).
              SoC, kWh, Kosten, Ladeort - alles wird gespeichert.
            </p>
          </div>
        </div>

        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-5 flex gap-4">
          <div class="shrink-0 w-10 h-10 bg-blue-100 dark:bg-blue-900/30 rounded-lg flex items-center justify-center">
            <ChartBarIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
          </div>
          <div>
            <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-1">Verbrauch und Kosten im Blick</h2>
            <p class="text-sm text-gray-500 dark:text-gray-400">
              Dashboard mit echtem Verbrauch (kWh/100 km), Kosten pro km, monatlicher Uberblick.
              Kein Raten mehr - nur Fakten aus deinen eigenen Daten.
            </p>
          </div>
        </div>

        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-5 flex gap-4">
          <div class="shrink-0 w-10 h-10 bg-purple-100 dark:bg-purple-900/30 rounded-lg flex items-center justify-center">
            <ArrowTrendingUpIcon class="h-5 w-5 text-purple-600 dark:text-purple-400" />
          </div>
          <div>
            <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-1">Echte Reichweite statt WLTP</h2>
            <p class="text-sm text-gray-500 dark:text-gray-400">
              Sehe auf Basis deiner gefahrenen km und geladenen kWh, wie weit dein Akku wirklich tragt -
              nicht der Laborwert des Herstellers.
            </p>
          </div>
        </div>

        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-5 flex gap-4">
          <div class="shrink-0 w-10 h-10 bg-orange-100 dark:bg-orange-900/30 rounded-lg flex items-center justify-center">
            <HeartIcon class="h-5 w-5 text-orange-600 dark:text-orange-400" />
          </div>
          <div>
            <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-1">Batteriezustand (SoH) verfolgen</h2>
            <p class="text-sm text-gray-500 dark:text-gray-400">
              EV Monitor berechnet deinen State of Health aus deinen Ladetagebuch-Eintragem automatisch.
              Fruhwarnung bei Degradation - relevant fur Garantie und Wiederverkauf.
            </p>
          </div>
        </div>

        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-5 flex gap-4">
          <div class="shrink-0 w-10 h-10 bg-teal-100 dark:bg-teal-900/30 rounded-lg flex items-center justify-center">
            <BuildingStorefrontIcon class="h-5 w-5 text-teal-600 dark:text-teal-400" />
          </div>
          <div>
            <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-1">Ladeanbieter vergleichen</h2>
            <p class="text-sm text-gray-500 dark:text-gray-400">
              Welcher Anbieter ist fur dich am gunstigsten? EV Monitor wertet deine echten Ladevorgange aus
              und zeigt Kosten pro kWh je Anbieter.
            </p>
          </div>
        </div>
      </div>

      <!-- Community Stats -->
      <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-5 mb-8 text-center">
        <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-4">
          Echte Daten von echten EV-Fahrern
        </h2>
        <div class="grid grid-cols-3 gap-4">
          <div>
            <div class="text-xl font-bold text-green-600 dark:text-green-400">{{ stats?.userCount?.toLocaleString('de-DE') ?? '...' }}</div>
            <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">Fahrer</div>
          </div>
          <div>
            <div class="text-xl font-bold text-green-600 dark:text-green-400">{{ stats?.validTripCount?.toLocaleString('de-DE') ?? '...' }}</div>
            <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">Ladevorgange</div>
          </div>
          <div>
            <div class="text-xl font-bold text-green-600 dark:text-green-400">{{ stats?.modelCount ?? '...' }}</div>
            <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">Modelle</div>
          </div>
        </div>
      </div>

      <!-- Wer sollte ein Ladetagebuch fuhren? -->
      <div class="mb-8">
        <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-4">
          Wer sollte ein Ladetagebuch fuhren?
        </h2>
        <ul class="space-y-2 text-sm text-gray-600 dark:text-gray-400">
          <li class="flex items-start gap-2">
            <CheckIcon class="h-4 w-4 text-green-500 shrink-0 mt-0.5" />
            <span><strong class="text-gray-800 dark:text-gray-200">Alle E-Auto-Besitzer</strong> - wer wissen will was sein Auto wirklich kostet</span>
          </li>
          <li class="flex items-start gap-2">
            <CheckIcon class="h-4 w-4 text-green-500 shrink-0 mt-0.5" />
            <span><strong class="text-gray-800 dark:text-gray-200">Dienstwagenfahrer</strong> - fur die korrekte Abrechnung von privaten Ladungen</span>
          </li>
          <li class="flex items-start gap-2">
            <CheckIcon class="h-4 w-4 text-green-500 shrink-0 mt-0.5" />
            <span><strong class="text-gray-800 dark:text-gray-200">Wiederverkaufer</strong> - SoH-Nachweis erhoht den Wiederverkaufswert</span>
          </li>
          <li class="flex items-start gap-2">
            <CheckIcon class="h-4 w-4 text-green-500 shrink-0 mt-0.5" />
            <span><strong class="text-gray-800 dark:text-gray-200">Neugierige</strong> - wer den WLTP-Mythos mit eigenen Daten widerlegen will</span>
          </li>
        </ul>
      </div>

      <!-- FAQ -->
      <div class="space-y-5 mb-10 text-sm text-gray-600 dark:text-gray-400">
        <div>
          <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-1">
            Was ist ein Ladetagebuch fur Elektroautos?
          </h2>
          <p>
            Ein Ladetagebuch dokumentiert jeden Ladevorgang deines E-Autos: wann, wo, wie viel kWh,
            zu welchem Preis und mit welchem Ergebnis (SoC). Ahnlich wie ein Fahrtenbuch - aber fur
            das Laden statt fur das Tanken. EV Monitor digitalisiert diesen Prozess und macht ihn
            weitgehend automatisch.
          </p>
        </div>
        <div>
          <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-1">
            Muss ich jeden Ladevorgang manuell eintragen?
          </h2>
          <p>
            Nein - mit EV Monitor AutoSync (Premium-Feature fur €3,90/Monat) werden Ladevorgange
            automatisch erfasst, wenn dein Fahrzeug verbunden ist. Fur Tesla, VW, Skoda, SEAT und CUPRA
            wird automatisch synchronisiert. Manuelles Eintragen bleibt naturlich moglich und ist kostenlos.
          </p>
        </div>
        <div>
          <h2 class="text-base font-semibold text-gray-800 dark:text-gray-200 mb-1">
            Ist EV Monitor kostenlos?
          </h2>
          <p>
            Das Ladetagebuch mit manuellen Eintramen, Dashboard, Verbrauchsstatistiken und Community-Vergleich
            ist komplett kostenlos. AutoSync (automatische Fahrzeuganbindung) ist ein optionales Premium-Feature.
          </p>
        </div>
      </div>

      <!-- Final CTA -->
      <div class="bg-gradient-to-br from-gray-900 to-green-900 rounded-xl p-6 text-center">
        <h2 class="text-lg font-bold text-white mb-2">Dein erstes Ladetagebuch - heute</h2>
        <p class="text-gray-300 text-sm mb-4">
          In 2 Minuten angemeldet. Direkt loslegen. Keine versteckten Kosten.
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
import {
  ArrowRightIcon,
  BoltIcon,
  ChartBarIcon,
  ArrowTrendingUpIcon,
  HeartIcon,
  BuildingStorefrontIcon,
  CheckIcon
} from '@heroicons/vue/24/outline';
import PublicNav from '../components/shared/PublicNav.vue';
import { getPlatformStats, type PlatformStats } from '../api/publicModelService';

useHead({
  title: 'Digitales Ladetagebuch fur Elektroauto - Kosten und Verbrauch tracken | EV Monitor',
  meta: [
    {
      name: 'description',
      content: 'Kostenloses digitales Ladetagebuch fur Elektroautos. Ladekosten, Verbrauch, echte Reichweite und SoH automatisch tracken. Tesla, VW, Skoda, SEAT und mehr.'
    },
    { property: 'og:title', content: 'Digitales Ladetagebuch Elektroauto | EV Monitor' },
    { property: 'og:description', content: 'Alle Ladevorgange erfassen, Kosten im Blick behalten, echte Reichweite sehen. Kostenlos starten.' }
  ],
  link: [{ rel: 'canonical', href: 'https://ev-monitor.net/ladetagebuch' }],
  script: [
    {
      type: 'application/ld+json',
      innerHTML: JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'SoftwareApplication',
        name: 'EV Monitor - Ladetagebuch fur Elektroautos',
        description: 'Digitales Ladetagebuch: Ladekosten, Verbrauch, echte Reichweite und SoH tracken',
        url: 'https://ev-monitor.net/ladetagebuch',
        applicationCategory: 'UtilityApplication',
        operatingSystem: 'Web',
        offers: { '@type': 'Offer', price: '0', priceCurrency: 'EUR' },
        breadcrumb: {
          '@type': 'BreadcrumbList',
          itemListElement: [
            { '@type': 'ListItem', position: 1, name: 'EV Monitor', item: 'https://ev-monitor.net' },
            { '@type': 'ListItem', position: 2, name: 'Ladetagebuch', item: 'https://ev-monitor.net/ladetagebuch' }
          ]
        }
      })
    }
  ]
});

const stats = ref<PlatformStats | null>(null);

onMounted(async () => {
  try {
    stats.value = await getPlatformStats();
  } catch {
    // stats bleiben null, Fallback-Text wird angezeigt
  }
});
</script>
