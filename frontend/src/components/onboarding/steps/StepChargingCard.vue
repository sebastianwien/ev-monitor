<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { CheckCircleIcon } from '@heroicons/vue/24/outline'
import { KNOWN_EMPS, HOME_TARIFF_NAME } from '../../../composables/useChargingProviders'
import { CUSTOM_PROVIDER, type useInlineChargingCard } from '../../../composables/useInlineChargingCard'

defineProps<{
  card: ReturnType<typeof useInlineChargingCard>
  priceUnit: string
  savedName: string | null
}>()

const { t } = useI18n()

// Heimstrom ist keine Ladekarte und braucht das Privat-Kennzeichen aus den Einstellungen.
const providers = KNOWN_EMPS.filter(emp => emp !== CUSTOM_PROVIDER && emp !== HOME_TARIFF_NAME)

const FIELD_CLASS = 'w-full min-h-[44px] rounded-sm border border-gray-300 dark:border-gray-600 '
  + 'bg-white dark:bg-gray-700 px-3 py-2.5 text-sm text-gray-800 dark:text-gray-100 '
  + 'outline-none transition focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500'
</script>

<template>
  <div class="px-4 pb-4 md:px-8">
    <h2 id="onboarding-title" class="text-xl md:text-2xl font-bold text-gray-800 dark:text-gray-100 text-balance">
      {{ savedName ? t('onboarding.card_success_title', { name: savedName }) : t('onboarding.card_title') }}
    </h2>

    <div v-if="savedName" class="mt-6 flex flex-col items-center gap-3 text-center">
      <CheckCircleIcon class="h-14 w-14 text-green-500" aria-hidden="true" />
      <p class="text-sm text-gray-500 dark:text-gray-400 max-w-sm">{{ t('onboarding.card_success_hint') }}</p>
    </div>

    <template v-else>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400 text-balance">{{ t('onboarding.card_desc') }}</p>

      <div class="mt-4 flex flex-col gap-3">
        <div>
          <label for="onboarding-card-provider" class="block text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">
            {{ t('onboarding.card_provider_label') }}
          </label>
          <select id="onboarding-card-provider" v-model="card.draft.value.providerName" :class="FIELD_CLASS">
            <option value="" disabled>{{ t('onboarding.card_provider_placeholder') }}</option>
            <option v-for="provider in providers" :key="provider" :value="provider">{{ provider }}</option>
            <option :value="CUSTOM_PROVIDER">{{ t('onboarding.card_other_provider') }}</option>
          </select>
        </div>

        <div v-if="card.isCustom.value">
          <label for="onboarding-card-name" class="block text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">
            {{ t('onboarding.card_custom_name_label') }}
          </label>
          <input id="onboarding-card-name" v-model="card.draft.value.customProviderName" type="text" maxlength="100"
            :placeholder="t('onboarding.card_custom_name_placeholder')" :class="FIELD_CLASS" />
        </div>

        <div class="grid grid-cols-2 gap-3">
          <div>
            <label for="onboarding-card-ac" class="block text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">
              {{ t('onboarding.card_ac_label', { unit: priceUnit }) }}
            </label>
            <input id="onboarding-card-ac" v-model="card.draft.value.acPrice" type="number" inputmode="decimal"
              step="0.1" min="0" :class="FIELD_CLASS" />
          </div>
          <div>
            <label for="onboarding-card-dc" class="block text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">
              {{ t('onboarding.card_dc_label', { unit: priceUnit }) }}
            </label>
            <input id="onboarding-card-dc" v-model="card.draft.value.dcPrice" type="number" inputmode="decimal"
              step="0.1" min="0" :class="FIELD_CLASS" />
          </div>
        </div>

        <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('onboarding.card_price_hint') }}</p>
        <p v-if="card.failed.value" role="alert" class="text-sm text-red-600 dark:text-red-400">
          {{ t('onboarding.card_save_failed') }}
        </p>
      </div>
    </template>
  </div>
</template>
