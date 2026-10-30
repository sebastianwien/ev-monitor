<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { LockClosedIcon } from '@heroicons/vue/24/outline'
import { useAnalyticsUpsellLink, useAnalyticsUpsellTarget } from '../../composables/useUpsellTarget'
import { useCountryStore } from '../../stores/country'
import { getPricing } from '../../config/pricingConfig'

/**
 * Gesperrter Platz in einem Sheet: Schloss, ein Satz, rechts der Knopf zum passenden Paket
 * (Supporter-Pack, wenn die Daten schon gratis kommen, sonst AutoSync), darunter der Monatspreis.
 * In der nativen App ohne Knopf und Preis. Der Satz verspricht nur, was das Paket zeigt, keine echten Werte.
 */
const props = defineProps<{ text: string; from: string }>()

const { t } = useI18n()
const target = useAnalyticsUpsellTarget()
const to = useAnalyticsUpsellLink(props.from)
const countryStore = useCountryStore()
const supporter = computed(() => target.value === '/supporter')
const price = computed(() => {
  const p = getPricing(countryStore.country)
  return t('story.upsell_price', { price: supporter.value ? p.supporterMonthly : p.monthly })
})
</script>

<template>
  <div class="flex min-h-11 flex-wrap items-center justify-between gap-x-3 gap-y-2">
    <span class="flex min-w-0 items-start gap-1.5 text-sm text-gray-800 dark:text-gray-200">
      <LockClosedIcon class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      <span class="min-w-0 text-pretty">{{ text }}</span>
    </span>
    <span v-if="to" class="ml-auto flex shrink-0 flex-col items-end gap-0.5">
      <router-link :to="to"
        class="inline-flex min-h-[44px] items-center rounded-full bg-story-accent px-4 py-2.5 text-sm font-semibold text-white dark:bg-story-accent-dark dark:text-gray-900">
        {{ supporter ? t('story.upsell_cta_supporter') : t('story.upsell_cta_autosync') }}
      </router-link>
      <span class="text-xs tabular-nums text-gray-500 dark:text-gray-400">{{ price }}</span>
    </span>
  </div>
</template>
