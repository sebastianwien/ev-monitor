<template>
  <!-- The two assumptions to play with once the answers are open: wallbox and price, as one tile of the answers -->
  <div class="grid gap-5" data-testid="personal-range-tune">
    <div class="grid gap-5 md:grid-cols-2 md:gap-10">
    <div class="grid content-start gap-3">
      <p id="pr-wallbox-q" class="text-[17px] font-bold leading-snug text-gray-900 md:text-[18px] dark:text-gray-100">{{ t('personal_range.wallbox_question') }}</p>
      <div class="flex gap-2" role="group" aria-labelledby="pr-wallbox-q">
        <button v-for="opt in WALLBOX" :key="String(opt)" type="button" :aria-pressed="inputs.homeCharging === opt"
                class="inline-flex min-h-11 min-w-[96px] items-center justify-center rounded-full border-2 px-5 font-semibold transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600"
                :class="inputs.homeCharging === opt
                  ? 'btn-3d cta-shadow-sm border-gray-900 bg-gray-900 text-white dark:border-gray-100 dark:bg-gray-100 dark:text-gray-900'
                  : 'btn-3d cta-shadow-sm border-gray-900 bg-white text-gray-800 dark:border-gray-200 dark:bg-gray-900 dark:text-gray-200'"
                :data-testid="`wallbox-${opt ? 'yes' : 'no'}`"
                @click="setHome(opt)">
          {{ opt ? t('personal_range.wallbox_yes') : t('personal_range.wallbox_no') }}
        </button>
      </div>
      <p class="max-w-[52ch] text-[13px] leading-relaxed text-gray-600 dark:text-gray-400">{{ t('personal_range.wallbox_why') }}</p>
    </div>
    <div class="grid gap-1">
      <ProfileSlider v-if="shownPrice != null" id="pr-price" test-key="price" :label="t('personal_range.price_label')"
                     :min="PRICE.min" :max="PRICE.max" :step="PRICE.step" :start="shownPrice" :value="shownPrice"
                     :format="v => formatCostPerKwh(v)" @commit="v => emit('price', Math.round(v * 100) / 100)" />
      <p class="text-[12.5px] leading-snug text-gray-600 dark:text-gray-400">
        <template v-if="inputs.pricePerKwh == null">{{ t('personal_range.price_hint_community') }}</template>
        <button v-else type="button" class="min-h-11 font-semibold text-green-700 underline dark:text-green-400" @click="emit('price', null)">{{ t('personal_range.price_reset') }}</button>
      </p>
    </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ProfileSlider from './ProfileSlider.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { analytics } from '../../services/analytics'
import type { PersonalRangeInputs } from '../../api/personalRangeService'

const props = defineProps<{
  modelName: string
  inputs: PersonalRangeInputs
  /** the community price the shown result rests on; null until a result is there */
  communityPrice: number | null
}>()
const emit = defineEmits<{
  home: [value: boolean]
  price: [value: number | null]
}>()

const { t } = useI18n()
const { formatCostPerKwh } = useLocaleFormat()

const WALLBOX = [true, false] as const
/** Within the backend bounds (0,05 to 1,50 €), narrower so 35 ct can be set with a thumb, in EUR */
const PRICE = { min: 0.1, max: 1, step: 0.01 }

/** The reader's own price, else the community price the backend used (follows the wallbox answer) */
const shownPrice = computed(() => props.inputs.pricePerKwh ?? props.communityPrice)

function setHome(value: boolean) {
  analytics.track('personal_range_wallbox', { model: props.modelName, home: value })
  emit('home', value)
}
</script>
