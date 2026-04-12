<script setup lang="ts">
import { computed } from 'vue'
import { BoltIcon, CurrencyEuroIcon } from '@heroicons/vue/24/outline'
import { useCountryStore } from '../../stores/country'

const countryStore = useCountryStore()
const isGerman = computed(() => countryStore.country === 'DE')

interface Banner { id: string; icon: string; headline: string; text: string; cta: string; url: string; weight: number }

const banners: Banner[] = [
  // URLs mit echten Awin-Tracking-Links ersetzen um Banner zu aktivieren
  // {
  //   id: 'thg',
  //   icon: 'currency',
  //   headline: 'Dein BEV bringt dir Geld',
  //   text: 'Als Elektroauto-Fahrer kannst du deine jährliche THG-Quote verkaufen - bis zu 100 € Prämie.',
  //   cta: 'THG-Prämie beantragen',
  //   url: 'AWIN_TRACKING_URL_THG',
  //   weight: 60,
  // },
  {
    id: 'enbw',
    icon: 'bolt',
    headline: 'Laden an 900.000+ Ladepunkten',
    text: 'EnBW mobility+ - europaweit unterwegs laden, transparente Preise, kein Mindesteinsatz.',
    cta: 'EnBW mobility+ entdecken',
    url: 'https://www.awin1.com/cread.php?s=3178475&v=15335&q=343652&r=2827948',
    weight: 40,
  },
]

const banner = computed(() => {
  const total = banners.reduce((sum, b) => sum + b.weight, 0)
  let rand = Math.random() * total
  for (const b of banners) {
    rand -= b.weight
    if (rand <= 0) return b
  }
  return banners[0]
})
</script>

<template>
  <div
    v-if="banner && isGerman"
    class="relative bg-green-50/60 dark:bg-green-900/10 md:rounded-2xl md:border-x border-t md:border-b border-green-100 dark:border-green-900/40 px-6 py-4"
  >
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
      <!-- Icon + Text -->
      <div class="flex items-start gap-3 min-w-0">
        <div class="shrink-0 mt-0.5 p-2 rounded-lg bg-green-50 dark:bg-green-900/30">
          <CurrencyEuroIcon v-if="banner.icon === 'currency'" class="h-5 w-5 text-green-600 dark:text-green-400" />
          <BoltIcon v-else class="h-5 w-5 text-green-600 dark:text-green-400" />
        </div>
        <div class="min-w-0">
          <p class="text-sm font-semibold text-gray-800 dark:text-gray-200 leading-snug">{{ banner.headline }}</p>
          <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5 leading-snug">{{ banner.text }}</p>
        </div>
      </div>

      <!-- CTA -->
      <a
        :href="banner.url"
        target="_blank"
        rel="noopener sponsored"
        class="shrink-0 self-center sm:self-auto text-xs font-medium bg-green-600 hover:bg-green-500 text-white px-3 py-2 rounded-lg whitespace-nowrap shadow-[0_4px_0_0_#15803d] active:shadow-[0_1px_0_0_#15803d] active:translate-y-[3px] transition-transform"
      >
        {{ banner.cta }}
      </a>
    </div>

    <!-- Anzeige-Label -->
    <span class="absolute bottom-1 right-3 text-[10px] text-gray-300 dark:text-gray-600">Anzeige</span>
  </div>
</template>
