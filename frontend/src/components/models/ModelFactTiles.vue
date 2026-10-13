<template>
  <ModelBox aria-labelledby="facts-title" data-testid="model-facts">
    <div class="grid gap-1">
      <h2 id="facts-title" class="text-[21px] font-bold tracking-tight text-gray-900 md:text-[24px] dark:text-gray-100">{{ title }}</h2>
      <p class="text-[13.5px] text-gray-500 dark:text-gray-400">{{ sub }}</p>
    </div>
    <dl class="grid grid-cols-2 gap-2 md:grid-cols-4 md:gap-3">
      <div v-for="fact in facts" :key="fact.key"
           class="grid content-start gap-1 rounded-2xl border border-gray-200 p-3 md:p-4 dark:border-gray-800"
           :data-testid="`fact-${fact.key}`">
        <dt class="flex items-center gap-1.5 text-[11.5px] font-semibold uppercase tracking-[0.06em] text-gray-500 dark:text-gray-400">
          {{ fact.label }}
          <span v-if="fact.level === 'PARTIAL' && fact.value != null" class="normal-case tracking-normal text-amber-700 dark:text-amber-400">{{ t('model.page.level_estimated') }}</span>
        </dt>
        <dd class="grid gap-1">
          <span class="flex flex-wrap items-baseline gap-x-1 leading-none">
            <span class="whitespace-nowrap text-[26px] font-bold tracking-tight tabular-nums md:text-[30px]"
                  :class="fact.value == null ? 'text-gray-300 dark:text-gray-600' : fact.level === 'NONE' ? 'text-gray-500 dark:text-gray-400' : 'text-gray-900 dark:text-gray-100'">
              {{ fact.value ?? '-' }}
            </span>
            <span v-if="fact.value != null && fact.unit" class="whitespace-nowrap text-[13px] font-semibold text-gray-500 dark:text-gray-400">{{ fact.unit }}</span>
          </span>
          <span class="text-[13px] leading-snug text-gray-600 dark:text-gray-400">{{ fact.note }}</span>
          <a v-if="fact.evidence" href="#belege"
             class="-my-2 inline-flex min-h-11 items-center gap-1 justify-self-start text-[13px] font-semibold text-green-700 hover:underline dark:text-green-400"
             @click="emit('evidence', fact.key)">
            {{ t('model.page.evidence_link') }}<ArrowDownIcon class="h-3.5 w-3.5" aria-hidden="true" />
          </a>
        </dd>
      </div>
    </dl>
    <slot />
  </ModelBox>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ArrowDownIcon } from '@heroicons/vue/24/outline'
import ModelBox from './ModelBox.vue'
import type { FactKey, FactTile } from '../../utils/modelStory'

/** The data sheet: four tiles, one big figure and one comparison line each. Presentational. */
defineProps<{ title: string; sub: string; facts: FactTile[] }>()
const emit = defineEmits<{ evidence: [key: FactKey] }>()
const { t } = useI18n()
</script>
