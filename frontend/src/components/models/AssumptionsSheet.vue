<template>
  <BottomSheet ref="sheet" :label="t('models_ranking.assumptions.title')" panel-class="sm:max-w-md" testid="assumptions-sheet" @close="emit('close')">
    <div ref="panel" class="flex max-h-[90vh] flex-col" @keydown="onKeydown">
      <div class="flex items-center justify-between gap-3 border-b border-gray-200 px-5 py-3.5 dark:border-gray-700">
        <h2 class="text-[17px] font-bold tracking-tight text-gray-900 dark:text-gray-100">{{ t('models_ranking.assumptions.title') }}</h2>
        <button
          ref="closeButton"
          type="button"
          class="-mr-2 grid h-11 w-11 place-items-center rounded-xl text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-700"
          :aria-label="t('models_ranking.assumptions.close')"
          @click="sheet?.requestClose()"
        >
          <XMarkIcon class="h-5 w-5" aria-hidden="true" />
        </button>
      </div>

      <div class="grid gap-5 overflow-y-auto px-5 py-4">
        <label class="grid gap-1.5">
          <span class="flex items-baseline justify-between gap-3 text-sm text-gray-700 dark:text-gray-300">
            {{ t('models_ranking.assumptions.home_share') }}
            <b class="tabular-nums text-gray-900 dark:text-gray-100">{{ Math.round(homeShare * 100) }} %</b>
          </span>
          <input
            type="range"
            min="0"
            max="100"
            step="5"
            :value="Math.round(homeShare * 100)"
            :aria-valuetext="`${Math.round(homeShare * 100)} %`"
            class="h-7 w-full accent-green-600"
            data-testid="assumption-home-share"
            @input="update('homeShare', Number(($event.target as HTMLInputElement).value) / 100)"
          />
          <span v-if="modelValue.homeShare === null" class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.assumptions.home_share_hint') }}</span>
        </label>

        <div class="grid grid-cols-2 gap-3">
          <label class="grid gap-1.5">
            <span class="text-sm text-gray-700 dark:text-gray-300">{{ t('models_ranking.assumptions.home_price', { unit: currencySymbol }) }}</span>
            <input :value="local(modelValue.homePricePerKwh, 2)" v-bind="numeric" @change="updateMoney('homePricePerKwh', $event, PRICE_MAX)" />
          </label>
          <label class="grid gap-1.5">
            <span class="text-sm text-gray-700 dark:text-gray-300">{{ t('models_ranking.assumptions.public_price', { unit: currencySymbol }) }}</span>
            <input :value="local(modelValue.publicPricePerKwh, 2)" v-bind="numeric" @change="updateMoney('publicPricePerKwh', $event, PRICE_MAX)" />
          </label>
        </div>

        <template v-if="combustion">
          <div class="grid gap-1.5">
            <span :id="fuelId" class="text-sm text-gray-700 dark:text-gray-300">{{ t('models_ranking.assumptions.fuel') }}</span>
            <div class="grid grid-cols-2 gap-px overflow-hidden rounded-xl border border-gray-200 bg-gray-200 dark:border-gray-600 dark:bg-gray-600" role="group" :aria-labelledby="fuelId">
              <button
                v-for="f in (['petrol', 'diesel'] as const)"
                :key="f"
                type="button"
                class="min-h-11 text-sm font-semibold"
                :class="modelValue.fuel === f
                  ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900'
                  : 'bg-white text-gray-800 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700'"
                :aria-pressed="modelValue.fuel === f"
                @click="update('fuel', f)"
              >{{ t(`models_ranking.assumptions.${f}`) }}</button>
            </div>
          </div>

          <div class="grid grid-cols-2 gap-3">
            <label class="grid gap-1.5">
              <span class="text-sm text-gray-700 dark:text-gray-300">{{ t('models_ranking.assumptions.liters') }}</span>
              <input :value="formatDecimal(modelValue.litersPer100km, 1)" v-bind="numeric" @change="updatePlain('litersPer100km', $event, LITERS_MAX)" />
            </label>
            <label class="grid gap-1.5">
              <span class="text-sm text-gray-700 dark:text-gray-300">{{ t('models_ranking.assumptions.fuel_price', { unit: currencySymbol }) }}</span>
              <input
                :value="modelValue.fuelPricePerLiter != null ? local(modelValue.fuelPricePerLiter, 2) : ''"
                v-bind="numeric"
                data-testid="assumption-fuel-price"
                @change="updateMoney('fuelPricePerLiter', $event, FUEL_PRICE_MAX, true)"
              />
            </label>
          </div>
          <p class="-mt-3 text-[12.5px] text-gray-500 dark:text-gray-400">
            {{ modelValue.fuelPricePerLiter == null ? t('models_ranking.assumptions.fuel_price_missing') : (fuelPriceFromApi ? t('models_ranking.assumptions.fuel_price_source') : '') }}
          </p>
        </template>

        <div class="grid gap-1.5">
          <span :id="mainId" class="text-sm text-gray-700 dark:text-gray-300">{{ t('models_ranking.assumptions.main_value') }}</span>
          <div class="grid grid-cols-2 gap-px overflow-hidden rounded-xl border border-gray-200 bg-gray-200 dark:border-gray-600 dark:bg-gray-600" role="group" :aria-labelledby="mainId">
            <button
              v-for="v in (['consumption', 'cost'] as const)"
              :key="v"
              type="button"
              class="min-h-11 text-sm font-semibold"
              :class="modelValue.mainValue === v
                ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900'
                : 'bg-white text-gray-800 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700'"
              :aria-pressed="modelValue.mainValue === v"
              :data-testid="`main-value-${v}`"
              @click="update('mainValue', v)"
            >{{ v === 'cost' ? t('models_ranking.assumptions.main_cost', { unit: distanceUnitLabel() }) : t('models_ranking.assumptions.main_consumption') }}</button>
          </div>
        </div>

        <p class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.assumptions.privacy') }}</p>
      </div>

      <div class="flex items-center justify-between gap-3 border-t border-gray-200 px-5 py-3 dark:border-gray-700">
        <button type="button" class="min-h-11 text-sm font-semibold text-gray-700 underline-offset-2 hover:underline dark:text-gray-300" @click="emit('reset')">
          {{ t('models_ranking.assumptions.reset') }}
        </button>
        <button type="button" class="inline-flex min-h-11 items-center rounded-xl bg-gray-900 px-5 text-sm font-semibold text-white dark:bg-gray-100 dark:text-gray-900" @click="sheet?.requestClose()">
          {{ t('models_ranking.assumptions.close') }}
        </button>
      </div>
    </div>
  </BottomSheet>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { convertCurrency } from '../../utils/unitConversions'
import { PRICE_MAX, LITERS_MAX, FUEL_PRICE_MAX, type CostAssumptions } from '../../utils/costMix'

const props = defineProps<{
  modelValue: CostAssumptions
  /** Effective home share (explicit value or the one derived from the needs check) */
  homeShare: number
  /** Combustion comparison possible in this market */
  combustion: boolean
  /** The fuel price still is the German daily average from the API */
  fuelPriceFromApi: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: CostAssumptions]; reset: []; close: [] }>()

const { t } = useI18n()
const { currencySymbol, currency, distanceUnitLabel, formatDecimal, locale } = useLocaleFormat()

const sheet = ref<InstanceType<typeof BottomSheet> | null>(null)
const panel = ref<HTMLElement | null>(null)
const closeButton = ref<HTMLButtonElement | null>(null)
const fuelId = useId()
const mainId = useId()

const numeric = {
  type: 'text',
  inputmode: 'decimal',
  autocomplete: 'off',
  class: 'h-11 w-full rounded-xl border border-gray-200 bg-white px-3 text-base tabular-nums text-gray-900 focus:border-green-600 focus:outline-none focus:ring-2 focus:ring-green-600/30 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100',
} as const

// Money is stored in EUR, shown and typed in the market's currency
const rate = () => convertCurrency(1, currency.value)
const local = (eur: number, decimals: number) => formatDecimal(eur * rate(), decimals)
function parse(event: Event): number | null {
  const raw = (event.target as HTMLInputElement).value.trim().replace(',', '.')
  if (raw === '') return null
  const n = Number(raw)
  return Number.isFinite(n) ? n : null
}
function update<K extends keyof CostAssumptions>(key: K, value: CostAssumptions[K]) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
function updateMoney(key: 'homePricePerKwh' | 'publicPricePerKwh' | 'fuelPricePerLiter', event: Event, max: number, nullable = false) {
  const n = parse(event)
  if (n === null) {
    if (nullable) update(key, null)
    return
  }
  const eur = Math.min(Math.max(0, n / rate()), max)
  update(key, Math.round(eur * 1000) / 1000)
}
function updatePlain(key: 'litersPer100km', event: Event, max: number) {
  const n = parse(event)
  if (n === null) return
  update(key, Math.min(Math.max(0, n), max))
}

// Focus stays inside the dialog; Escape closes it. The caller returns focus to the chip on close.
function focusable(): HTMLElement[] {
  return Array.from(panel.value?.querySelectorAll<HTMLElement>('button, input, [tabindex]:not([tabindex="-1"])') ?? [])
    .filter(el => !el.hasAttribute('disabled'))
}
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    sheet.value?.requestClose()
    return
  }
  if (event.key !== 'Tab') return
  const items = focusable()
  if (!items.length) return
  const first = items[0]
  const last = items[items.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
onMounted(async () => {
  await nextTick()
  closeButton.value?.focus()
})
void locale
</script>
