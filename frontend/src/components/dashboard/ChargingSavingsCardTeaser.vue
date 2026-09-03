<script setup lang="ts">
/**
 * Gesperrter Zustand der Ersparnis-Kachel, nachdem der Probemonat abgelaufen ist.
 *
 * Bewusst ohne Zahl: die konkrete Ersparnis ist das staerkste Kaufargument und wird
 * nicht verschenkt. Das CTA-Ziel richtet sich nach dem, was dem Nutzer fehlt - ein
 * Tesla-Fahrer hat die Datenquelle schon und braucht nur das Supporter-Pack, alle
 * anderen die AutoSync-Ebene (siehe useAnalyticsUpsellTarget).
 */
import { HomeIcon, LockClosedIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { useI18n } from 'vue-i18n'
import { useAnalyticsUpsellTarget } from '../../composables/useUpsellTarget'

const { t } = useI18n()
const upsellTarget = useAnalyticsUpsellTarget()
</script>

<template>
  <div class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#4b5563] p-4 md:p-5">
    <div class="flex items-center justify-center gap-2 mb-3">
      <HomeIcon class="h-5 w-5 flex-none text-emerald-600 dark:text-emerald-400" />
      <span class="text-sm font-semibold text-gray-500 dark:text-gray-400">{{ t('savings.title') }}</span>
      <LockClosedIcon class="h-4 w-4 flex-none text-gray-400 dark:text-gray-500" />
    </div>
    <p class="mb-4 text-xs leading-relaxed text-gray-600 dark:text-gray-300">{{ t('savings.locked_body') }}</p>
    <router-link :to="upsellTarget"
                 class="inline-flex items-center gap-1 rounded-sm bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-emerald-700 dark:bg-emerald-500 dark:hover:bg-emerald-600">
      {{ t('savings.locked_cta') }}
      <ChevronRightIcon class="h-3.5 w-3.5" />
    </router-link>
  </div>
</template>
