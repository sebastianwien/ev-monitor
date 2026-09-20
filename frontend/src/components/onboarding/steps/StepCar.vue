<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CheckCircleIcon, MagnifyingGlassIcon, MinusIcon, PlusIcon } from '@heroicons/vue/24/outline'
import type { useCarQuickAdd } from '../../../composables/useCarQuickAdd'

const props = defineProps<{ flow: ReturnType<typeof useCarQuickAdd> }>()
const { t } = useI18n()

const title = computed(() => {
  switch (props.flow.phase.value) {
    case 'model-select': return t('onboarding.car_which_model')
    case 'trim-select': return t('onboarding.car_which_trim')
    case 'year-input': return t('onboarding.car_which_year')
    case 'success': return t('onboarding.car_success_title', {
      car: `${props.flow.selectedBrand.value?.label ?? ''} ${props.flow.selectedModel.value?.label ?? ''}`.trim(),
    })
    default: return t('onboarding.car_title')
  }
})

const RETRY_CLASS = 'mt-2 min-h-[44px] rounded-sm border border-gray-300 dark:border-gray-600 px-4 '
  + 'text-sm font-medium text-gray-700 dark:text-gray-200 transition '
  + 'hover:bg-gray-100 dark:hover:bg-gray-700'

const OPTION_CLASS = 'w-full min-h-[44px] rounded-sm border border-gray-200 dark:border-gray-600 '
  + 'bg-gray-50 dark:bg-gray-700 p-3 text-left text-sm font-medium text-gray-800 dark:text-gray-100 transition '
  + 'hover:border-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 hover:text-indigo-700 dark:hover:text-indigo-300'
</script>

<template>
  <div class="px-4 pb-4 md:px-8">
    <h2 id="onboarding-title" class="text-xl md:text-2xl font-bold text-gray-800 dark:text-gray-100 text-balance">
      {{ title }}
    </h2>

    <!-- Marke -->
    <template v-if="flow.phase.value === 'brand-select'">
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ t('onboarding.car_desc') }}</p>

      <label class="mt-4 flex items-center gap-2 rounded-sm border border-gray-200 dark:border-gray-600 bg-gray-100 dark:bg-gray-700 px-4 min-h-[44px]">
        <MagnifyingGlassIcon class="h-5 w-5 text-gray-400 flex-shrink-0" aria-hidden="true" />
        <span class="sr-only">{{ t('onboarding.car_search_placeholder') }}</span>
        <input v-model="flow.searchQuery.value" type="search" autocomplete="off"
          :placeholder="t('onboarding.car_search_placeholder')"
          class="flex-1 bg-transparent py-2.5 text-sm text-gray-700 dark:text-gray-200 outline-none placeholder-gray-400 dark:placeholder-gray-500" />
      </label>

      <p v-if="flow.brandsLoading.value" class="mt-4 text-sm text-gray-400">{{ t('onboarding.car_brands_loading') }}</p>
      <!-- Ladefehler vor dem leeren Ergebnis pruefen: sonst behauptet der Text, die Marke gaebe es nicht. -->
      <div v-else-if="flow.brandsFailed.value" role="alert" class="mt-4">
        <p class="text-sm text-red-600 dark:text-red-400">{{ t('onboarding.car_load_failed') }}</p>
        <button type="button" data-testid="car-retry-brands" @click="flow.loadBrands()" :class="RETRY_CLASS">
          {{ t('onboarding.car_retry') }}
        </button>
      </div>
      <p v-else-if="flow.filteredBrands.value.length === 0" class="mt-4 text-sm text-gray-500 dark:text-gray-400">
        {{ t('onboarding.car_no_brand_match') }}
      </p>
      <div v-else class="mt-3 flex flex-wrap gap-2">
        <button v-for="brand in flow.filteredBrands.value" :key="brand.value" type="button"
          @click="flow.selectBrand(brand)"
          class="min-h-[44px] rounded-full border border-gray-200 dark:border-gray-600 bg-gray-100 dark:bg-gray-700 px-4 text-sm
                 text-gray-700 dark:text-gray-200 transition hover:border-indigo-300 dark:hover:border-indigo-600
                 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 hover:text-indigo-700 dark:hover:text-indigo-300">
          {{ brand.label }}
        </button>
      </div>
    </template>

    <!-- Modell -->
    <template v-else-if="flow.phase.value === 'model-select'">
      <p v-if="flow.modelsLoading.value" class="mt-4 text-sm text-gray-400">{{ t('onboarding.car_models_loading') }}</p>
      <div v-else-if="flow.modelsFailed.value" role="alert" class="mt-4">
        <p class="text-sm text-red-600 dark:text-red-400">{{ t('onboarding.car_load_failed') }}</p>
        <button type="button" data-testid="car-retry-models" @click="flow.retryModels()" :class="RETRY_CLASS">
          {{ t('onboarding.car_retry') }}
        </button>
      </div>
      <div v-else class="mt-4 grid grid-cols-2 gap-2">
        <button v-for="model in flow.models.value" :key="model.value" type="button" :class="OPTION_CLASS"
          @click="flow.selectModel(model)">
          {{ model.label }}
        </button>
      </div>
    </template>

    <!-- Variante: erst die Ausstattungsgruppen, darin die Kapazitaeten -->
    <template v-else-if="flow.phase.value === 'trim-select'">
      <div class="mt-4 flex flex-col gap-2">
        <template v-if="flow.isGrouped.value && !flow.selectedTrimLevel.value">
          <button v-for="group in flow.trimGroups.value" :key="group.trimLevel" type="button" :class="OPTION_CLASS"
            @click="flow.selectTrim(group.trimLevel)">
            {{ group.trimLevel }}
            <span class="ml-1 text-xs text-gray-500 dark:text-gray-400">
              ({{ group.options.map(o => o.kWh + ' kWh').join(' / ') }})
            </span>
          </button>
        </template>
        <template v-else>
          <button v-for="spec in flow.visibleSpecs.value" :key="spec.vehicleSpecificationId ?? spec.kWh" type="button"
            :class="OPTION_CLASS" @click="flow.selectSpec(spec)">
            {{ spec.variantName || spec.kWh + ' kWh' }}
            <span v-if="spec.availableFrom" class="ml-1 text-xs text-gray-500 dark:text-gray-400">
              {{ spec.availableFrom }}{{ spec.availableTo ? ' - ' + spec.availableTo : '+' }}
            </span>
          </button>
        </template>
      </div>
    </template>

    <!-- Baujahr -->
    <template v-else-if="flow.phase.value === 'year-input'">
      <div class="mt-6 flex items-center justify-center gap-5">
        <button type="button" :aria-label="t('onboarding.car_year_decrease')" :disabled="!flow.canDecreaseYear.value"
          @click="flow.decreaseYear()"
          class="w-11 h-11 flex items-center justify-center rounded-full border border-gray-300 dark:border-gray-600
                 text-gray-600 dark:text-gray-300 transition hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-30">
          <MinusIcon class="h-5 w-5" />
        </button>
        <output class="text-3xl font-bold text-gray-800 dark:text-gray-100 tabular-nums w-20 text-center">
          {{ flow.year.value }}
        </output>
        <button type="button" :aria-label="t('onboarding.car_year_increase')" :disabled="!flow.canIncreaseYear.value"
          @click="flow.increaseYear()"
          class="w-11 h-11 flex items-center justify-center rounded-full border border-gray-300 dark:border-gray-600
                 text-gray-600 dark:text-gray-300 transition hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-30">
          <PlusIcon class="h-5 w-5" />
        </button>
      </div>
      <p v-if="flow.failed.value" role="alert" class="mt-4 text-sm text-red-600 dark:text-red-400 text-center">
        {{ flow.errorMessage.value ?? t('onboarding.car_create_error') }}
      </p>
    </template>

    <!-- Angelegt -->
    <template v-else>
      <div class="mt-6 flex flex-col items-center gap-3 text-center">
        <CheckCircleIcon class="h-14 w-14 text-green-500" aria-hidden="true" />
        <p class="text-sm text-gray-500 dark:text-gray-400 max-w-sm">{{ t('onboarding.car_success_hint') }}</p>
      </div>
    </template>
  </div>
</template>
