<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { LockClosedIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { useAnalyticsUpsellLink, useAnalyticsUpsellTarget } from '../../composables/useUpsellTarget'
import { useCountryStore } from '../../stores/country'
import { getPricing } from '../../config/pricingConfig'

/**
 * Angebotszeile: Aufbau wie jede Zeile im Sheet (Titel, Unterzeile, Chevron), grün getönt mit Rand und
 * Schloss im Kreis, die ganze Zeile führt zum passenden Paket (Supporter-Pack, wenn die Daten schon gratis
 * kommen, sonst AutoSync). Unterzeile: Nutzen, dann Paket und Monatspreis. In der nativen App ohne Link und Preis.
 */
const props = defineProps<{ title?: string; text: string; from: string }>()

const { t } = useI18n()
const target = useAnalyticsUpsellTarget()
const to = useAnalyticsUpsellLink(props.from)
const countryStore = useCountryStore()
const offer = computed(() => {
  const p = getPricing(countryStore.country)
  return target.value === '/supporter'
    ? t('story.upsell_offer_supporter', { price: p.supporterMonthly })
    : t('story.upsell_offer_autosync', { price: p.monthly })
})
const tag = computed(() => to.value ? 'router-link' : 'div')
</script>

<template>
  <component :is="tag" :to="to ?? undefined"
    class="flex items-center gap-3 rounded-xl bg-green-50 px-3 py-3 ring-1 ring-green-200 dark:bg-green-950/50 dark:ring-green-800/70"
    :class="to ? 'focus-visible:outline focus-visible:outline-2 focus-visible:outline-green-600 hover:bg-green-100 dark:hover:bg-green-900/50' : ''">
    <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white ring-1 ring-green-200 dark:bg-gray-900 dark:ring-green-800/70" aria-hidden="true">
      <LockClosedIcon class="h-[18px] w-[18px] text-green-700 dark:text-green-400" />
    </span>
    <span class="min-w-0 flex-1">
      <span v-if="title" class="block text-[15px] font-bold text-gray-900 dark:text-gray-50">{{ title }}</span>
      <span class="block text-[13.5px] text-gray-700 text-pretty dark:text-gray-200">{{ text }}</span>
      <span v-if="to" class="mt-0.5 block text-[13px] font-semibold text-green-700 dark:text-green-400">{{ offer }}</span>
    </span>
    <ChevronRightIcon v-if="to" class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
  </component>
</template>
