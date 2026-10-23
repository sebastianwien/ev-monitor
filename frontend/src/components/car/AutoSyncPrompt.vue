<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowPathIcon, BanknotesIcon, Battery50Icon, XMarkIcon } from '@heroicons/vue/24/outline'
import { analytics } from '../../services/analytics'
import type { Car } from '../../api/carService'
import { carDisplayName } from '../../utils/enumLabel'

/**
 * AutoSync-Angebot direkt nach dem Anlegen eines Smartcar-faehigen Fahrzeugs in /cars -
 * das Gegenstueck zu TeslaTelemetryPrompt. Erklaert in drei Zeilen, was das Abo dem Auto
 * abnimmt, und fuehrt auf /upgrade, wo Preis, Probezeit und Kauf stehen. Welche Autos das
 * Fenster bekommen, entscheidet `carTeaserKind` (Smartcar-Marke, kein Abo, Kauf moeglich).
 */
const props = defineProps<{ car: Car }>()
const emit = defineEmits<{ (e: 'close'): void }>()
const { t } = useI18n()
const carName = computed(() => carDisplayName(props.car.brand, props.car.model))

const onCta = () => analytics.track('autosync_prompt_clicked', { brand: props.car.brand })
const onLater = () => {
  analytics.track('autosync_prompt_dismissed', { brand: props.car.brand })
  emit('close')
}
</script>

<template>
  <!-- Teleport nach body: die Fahrzeug-View liegt auf Mobile im SwipeTabPager, dessen Track
       ein translateX() traegt - ein transformierter Vorfahre wuerde position:fixed einfangen. -->
  <Teleport to="body">
  <div
    class="fixed inset-0 bg-black bg-opacity-50 flex items-end md:items-center justify-center z-50 md:p-4"
    role="dialog"
    aria-modal="true"
    :aria-label="t('cars.autosync_prompt_title', { car: carName })"
    data-testid="autosync-prompt"
    @click.self="onLater"
  >
    <div class="bg-white dark:bg-gray-800 rounded-t-sm md:rounded-sm md:shadow-[6px_6px_0_rgba(0,0,0,0.40)] md:dark:shadow-[6px_6px_0_rgba(255,255,255,0.40)] w-full max-w-lg max-h-[92vh] md:max-h-[90vh] overflow-y-auto">
      <div class="flex items-start justify-between gap-4 px-5 md:px-6 pt-5 md:pt-6 pb-4 border-b border-gray-100 dark:border-gray-700">
        <div class="min-w-0">
          <p class="text-green-600 dark:text-green-400 text-[10px] font-bold uppercase tracking-[0.14em] mb-0.5">{{ t('cars.autosync_prompt_eyebrow') }}</p>
          <h2 class="text-lg md:text-xl font-bold text-gray-900 dark:text-white tracking-tight">{{ t('cars.autosync_prompt_title', { car: carName }) }}</h2>
        </div>
        <button type="button" :aria-label="t('common.close')"
                class="shrink-0 h-11 w-11 -mr-2 -mt-2 flex items-center justify-center text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 transition-colors"
                @click="onLater">
          <XMarkIcon class="w-6 h-6" />
        </button>
      </div>

      <div class="p-5 md:p-6 space-y-5">
        <p class="text-[15px] leading-relaxed text-gray-700 dark:text-gray-300">{{ t('cars.autosync_prompt_lead') }}</p>

        <ul class="space-y-3">
          <li class="flex items-start gap-3">
            <ArrowPathIcon class="h-5 w-5 shrink-0 mt-0.5 text-green-600 dark:text-green-400" aria-hidden="true" />
            <span class="text-sm text-gray-700 dark:text-gray-300 leading-snug">{{ t('cars.autosync_prompt_b1') }}</span>
          </li>
          <li class="flex items-start gap-3">
            <Battery50Icon class="h-5 w-5 shrink-0 mt-0.5 text-green-600 dark:text-green-400" aria-hidden="true" />
            <span class="text-sm text-gray-700 dark:text-gray-300 leading-snug">{{ t('cars.autosync_prompt_b2') }}</span>
          </li>
          <li class="flex items-start gap-3">
            <BanknotesIcon class="h-5 w-5 shrink-0 mt-0.5 text-green-600 dark:text-green-400" aria-hidden="true" />
            <span class="text-sm text-gray-700 dark:text-gray-300 leading-snug">{{ t('cars.autosync_prompt_b3') }}</span>
          </li>
        </ul>

        <router-link
          to="/upgrade"
          class="block w-full text-center rounded-sm bg-green-600 hover:bg-green-700 text-white font-semibold py-3 px-4 text-[15px] transition-colors"
          @click="onCta"
        >
          {{ t('cars.autosync_prompt_cta') }}
        </router-link>

        <div class="text-center">
          <button type="button"
                  class="min-h-11 px-3 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 underline"
                  @click="onLater">
            {{ t('cars.autosync_prompt_later') }}
          </button>
          <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('cars.autosync_prompt_later_hint') }}</p>
        </div>
      </div>
    </div>
  </div>
  </Teleport>
</template>
