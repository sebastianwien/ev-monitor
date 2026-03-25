<script setup lang="ts">
import { computed } from 'vue'
import { BoltIcon, CurrencyEuroIcon } from '@heroicons/vue/24/outline'

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
  // {
  //   id: 'enbw',
  //   icon: 'bolt',
  //   headline: 'Laden an 300.000+ Ladepunkten',
  //   text: 'Mit EnBW mobility+ europaweit an Schnellladern laden - transparent, ohne Grundgebühr.',
  //   cta: 'EnBW mobility+ entdecken',
  //   url: 'AWIN_TRACKING_URL_ENBW',
  //   weight: 40,
  // },
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
    v-if="banner"
    class="relative bg-white dark:bg-gray-800 md:rounded-2xl md:border-x border-t md:border-b border-gray-200 dark:border-gray-700 md:mb-6 px-6 py-4"
  >
    <div class="flex items-center justify-between gap-4">
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
        class="shrink-0 text-xs font-medium bg-green-600 hover:bg-green-700 text-white px-3 py-2 rounded-lg transition-colors whitespace-nowrap"
      >
        {{ banner.cta }}
      </a>
    </div>

    <!-- Anzeige-Label -->
    <span class="absolute bottom-1 right-3 text-[10px] text-gray-300 dark:text-gray-600">Anzeige</span>
  </div>
</template>
