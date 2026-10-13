<template>
  <ModelBox aria-labelledby="facts-title" data-testid="model-facts">
    <div class="grid gap-1">
      <h2 id="facts-title" class="text-[21px] font-bold tracking-tight text-gray-900 md:text-[24px] dark:text-gray-100">{{ title }}</h2>
      <p class="text-[13.5px] text-gray-500 dark:text-gray-400">{{ sub }}</p>
    </div>
    <dl class="grid grid-cols-2 gap-2 md:grid-cols-4 md:gap-3">
      <div v-for="fact in facts" :key="fact.key"
           class="grid content-start gap-1.5 rounded-2xl bg-gray-100/80 p-3.5 md:p-5 dark:bg-gray-800/60"
           :data-testid="`fact-${fact.key}`">
        <dt class="flex items-center gap-1.5 text-[12.5px] font-semibold uppercase tracking-[0.06em] text-gray-500 dark:text-gray-400">
          {{ fact.label }}
          <span v-if="fact.level === 'PARTIAL' && fact.value != null" class="normal-case tracking-normal text-amber-700 dark:text-amber-400">{{ t('model.page.level_estimated') }}</span>
        </dt>
        <dd class="grid gap-1.5">
          <span v-for="(fig, i) in figures(fact)" :key="i" class="flex flex-wrap items-baseline gap-x-1.5 leading-none">
            <span class="whitespace-nowrap text-[28px] font-extrabold tracking-tight tabular-nums md:text-[34px]"
                  :class="fig.value == null ? 'text-gray-300 dark:text-gray-600' : fact.level === 'NONE' ? 'text-gray-500 dark:text-gray-400' : 'text-gray-900 dark:text-gray-100'">
              {{ fig.value ?? '-' }}
            </span>
            <span v-if="fig.value != null && fig.unit" class="whitespace-nowrap text-[15px] font-semibold text-gray-500 dark:text-gray-400">{{ fig.unit }}</span>
          </span>
          <span v-if="fact.note" class="text-[15px] leading-snug text-gray-600 md:text-[16px] dark:text-gray-400">{{ keepUnits(fact.note) }}</span>
        </dd>
      </div>
    </dl>
    <slot />
  </ModelBox>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ModelBox from './ModelBox.vue'
import { keepUnits, type FactTile } from '../../utils/modelStory'

/** The data sheet: four tiles, one big figure and one comparison line each. Presentational. */
defineProps<{ title: string; sub: string; facts: FactTile[] }>()
const { t } = useI18n()

/** The figure, and a second one of equal weight where the tile has it (cost: home and fast charge) */
function figures(fact: FactTile): { value: string | null; unit: string }[] {
  return fact.extra ? [{ value: fact.value, unit: fact.unit }, fact.extra] : [{ value: fact.value, unit: fact.unit }]
}
</script>
