<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { LockClosedIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { useAnalyticsUpsellLink, useAnalyticsUpsellTarget } from '../../composables/useUpsellTarget'
import { useCountryStore } from '../../stores/country'
import { getPricing } from '../../config/pricingConfig'

/**
 * Angebotszeile: sieht aus wie jede Zeile im Sheet (Titel, Unterzeile, Chevron), leicht grün getönt,
 * die ganze Zeile führt zum passenden Paket (Supporter-Pack, wenn die Daten schon gratis kommen, sonst
 * AutoSync). Unterzeile: Nutzen, dann Paket und Monatspreis. In der nativen App ohne Link und ohne Preis.
 * `compact`: eine Zeile mit Schloss, für Stellen ohne Zeilenliste (Ladeorte unter dem Schalter).
 */
const props = defineProps<{ title?: string; text: string; from: string; compact?: boolean }>()

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
  <component :is="tag" v-if="compact" :to="to ?? undefined"
    class="flex min-h-11 items-center gap-2 py-1 text-[13px] text-gray-700 dark:text-gray-300">
    <LockClosedIcon class="h-4 w-4 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
    <span class="min-w-0 flex-1">
      <span class="block">{{ text }}</span>
      <span v-if="to" class="block whitespace-nowrap font-semibold text-green-700 dark:text-green-400">{{ offer }}</span>
    </span>
    <ChevronRightIcon v-if="to" class="h-4 w-4 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
  </component>
  <component :is="tag" v-else :to="to ?? undefined"
    class="-mx-3 flex items-center gap-3 rounded-lg bg-green-50 px-3 py-2.5 dark:bg-green-950/40"
    :class="to ? 'focus-visible:outline focus-visible:outline-2 focus-visible:outline-green-600 hover:bg-green-100 dark:hover:bg-green-900/40' : ''">
    <LockClosedIcon class="h-4 w-4 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
    <span class="min-w-0 flex-1">
      <span v-if="title" class="block text-sm font-semibold text-gray-900 dark:text-gray-100">{{ title }}</span>
      <span class="block text-[13px] text-gray-700 text-pretty dark:text-gray-300">{{ text }}</span>
      <span v-if="to" class="block text-[13px] font-semibold text-green-700 dark:text-green-400">{{ offer }}</span>
    </span>
    <ChevronRightIcon v-if="to" class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
  </component>
</template>
