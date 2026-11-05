<script setup lang="ts">
import { useCountryStore } from '../../stores/country'
import { analytics } from '../../services/analytics'
import { thgSeason, thgCopy, THG_URL } from '../../utils/thgOffer'

/** THG-Prämie über den Partner, nur DE und nur in einer bekannten Saison. personal: eingeloggt, dann "Affiliate-Link" statt "Anzeige". */
const props = defineProps<{ placement: 'dashboard' | 'modelle' | 'onboarding'; personal?: boolean }>()

const countryStore = useCountryStore()
const season = thgSeason(new Date())
const copy = season ? thgCopy(season) : null

function handleClick() {
  analytics.trackAffiliateBannerClicked('thg', props.placement)
}
</script>

<template>
  <a
    v-if="copy && countryStore.country === 'DE'"
    :href="THG_URL"
    target="_blank"
    rel="noopener sponsored"
    class="relative flex flex-col gap-2 bg-green-50 dark:bg-green-900/20 border-2 border-gray-900 dark:border-gray-100 rounded-lg px-4 pt-3 pb-5 hover:bg-green-100 dark:hover:bg-green-900/30 transition-colors group mb-4 edge-lift sm:flex-row sm:items-center sm:justify-between sm:gap-3 sm:pb-4"
    data-testid="thg-banner"
    @click="handleClick"
  >
    <div class="min-w-0">
      <p class="text-[15px] font-bold text-gray-900 dark:text-gray-50 leading-snug">{{ copy.title }}</p>
      <p class="text-sm text-gray-600 dark:text-gray-300 leading-snug mt-0.5">{{ copy.text }}</p>
    </div>
    <span class="shrink-0 text-sm font-semibold text-green-700 dark:text-green-400 group-hover:underline whitespace-nowrap">{{ copy.cta }} →</span>
    <span class="absolute bottom-1 right-3 text-[10px] text-gray-500 dark:text-gray-400">{{ personal ? 'Affiliate-Link' : 'Anzeige' }}</span>
  </a>
</template>
