<template>
  <div class="grid gap-5 border-t border-gray-200 px-4 pb-5 pt-4 md:px-7 dark:border-gray-800">
    <div class="grid gap-5" :class="answers.length ? 'lg:grid-cols-2 lg:gap-8' : ''">
      <!-- 1. The reader's answers, signed in only: guests have the one gate in chapter 1 -->
      <section v-if="answers.length" class="grid content-start gap-2.5" :aria-label="t('models_ranking.detail.for_you')" data-testid="for-you">
        <h3 :class="HEAD">{{ t('models_ranking.detail.for_you') }}</h3>
        <dl class="grid gap-2 sm:grid-cols-3">
          <div v-for="a in answers" :key="a.key" class="grid content-start gap-0.5 rounded-xl border border-gray-200 bg-white px-3.5 py-3 dark:border-gray-700 dark:bg-gray-900" data-testid="for-you-answer">
            <dt class="text-[13px] text-gray-600 dark:text-gray-400">{{ a.label }}</dt>
            <dd class="text-[20px] font-extrabold leading-tight tracking-tight tabular-nums text-gray-900 dark:text-gray-100">{{ a.value }}</dd>
            <dd v-for="line in a.sub" :key="line" class="text-[12.5px] leading-snug text-gray-500 dark:text-gray-400">{{ line }}</dd>
          </div>
        </dl>
      </section>

      <!-- 2. What the drivers measure, free for everyone: a short list of what the closed row leaves out;
           alone (guest) centred above the button -->
      <section class="grid content-start gap-2.5" :class="answers.length ? '' : 'w-full max-w-xl justify-self-center'" :aria-label="t('models_ranking.detail.measured')">
        <h3 :class="HEAD">{{ t('models_ranking.detail.measured') }}</h3>
        <p v-if="item.singleDriver" class="flex items-start gap-1.5 rounded-xl bg-amber-50 px-3 py-2 text-[13px] text-amber-900 dark:bg-amber-900/30 dark:text-amber-200" data-testid="single-driver-note">
          <InformationCircleIcon class="mt-px h-4 w-4 flex-none" aria-hidden="true" />{{ t('models_ranking.row.single_driver') }}
        </p>
        <dl class="grid text-[13.5px] leading-snug" data-testid="detail-measured">
          <div v-for="f in facts" :key="f.key" class="grid grid-cols-[7rem_minmax(0,1fr)] gap-3 border-b border-gray-100 py-2 first:pt-0 last:border-0 last:pb-0 dark:border-gray-800" data-testid="detail-fact">
            <dt class="text-gray-500 dark:text-gray-400">{{ f.label }}</dt>
            <dd class="font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ f.value }}</dd>
          </div>
        </dl>
      </section>
    </div>

    <!-- 3. One way on, centred under both columns: the model page. The phone compares from here. -->
    <div class="grid justify-items-center gap-2.5 text-center">
      <div class="grid w-full gap-2.5 sm:flex sm:w-auto sm:flex-wrap sm:justify-center sm:gap-3">
      <ModelPageButton :href="href" :label="t('models_ranking.detail.model_page', { name: m.modelDisplayName })" @click="analytics.track('ranking_model_page')" />
      <button
        type="button"
        class="inline-flex min-h-11 items-center justify-center gap-1.5 rounded-xl border-2 px-4 font-semibold disabled:cursor-not-allowed disabled:opacity-40 lg:hidden"
        :class="inCompare ? 'border-green-600 bg-green-600 text-white dark:border-green-400 dark:bg-green-400 dark:text-gray-950' : 'border-edge bg-white text-gray-900 dark:bg-white/10 dark:text-gray-100'"
        :aria-pressed="inCompare"
        :disabled="!inCompare && !canAddCompare"
        data-testid="compare-open"
        @click="emit('compare')"
      >
        <CheckIcon v-if="inCompare" class="h-4 w-4" stroke-width="2.5" aria-hidden="true" />
        <PlusIcon v-else class="h-4 w-4" stroke-width="2.5" aria-hidden="true" />
        {{ t(inCompare ? 'models_ranking.detail.compare_remove' : 'models_ranking.detail.compare_add') }}
      </button>
      </div>
      <p class="text-[12.5px] leading-snug text-gray-500 dark:text-gray-400">{{ t('models_ranking.detail.model_page_more') }}</p>
    </div>
    <!-- The row's photo is CC licensed: the credit links to the source -->
    <p v-if="credit" class="-mt-3 text-center text-[11px] text-gray-500 dark:text-gray-400">
      <a :href="credit.sourceUrl" target="_blank" rel="noopener" class="hover:underline">{{ t('models_ranking.detail.photo_credit', { author: credit.author, license: credit.license }) }}</a>
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CheckIcon, InformationCircleIcon, PlusIcon } from '@heroicons/vue/24/outline'
import ModelPageButton from './ModelPageButton.vue'
import { useRankedModelText, type RankedModelAnswer } from '../../composables/useRankedModelText'
import { officialModelImageAttribution } from '../../config/modelImages'
import { analytics } from '../../services/analytics'
import type { RankedModel } from '../../composables/useModelRanking'
import type { FuelKind } from '../../utils/costMix'

const props = defineProps<{
  item: RankedModel
  /** Link to the model detail page */
  href: string
  longestTripKm: number | null
  fuel: FuelKind
  /** Guest: no answers here, the one gate sits in chapter 1 */
  locked?: boolean
  inCompare?: boolean
  canAddCompare?: boolean
}>()

const emit = defineEmits<{ compare: [] }>()

const HEAD = 'text-[12px] font-semibold uppercase tracking-[0.08em] text-gray-500 dark:text-gray-400'

const { t } = useI18n()
const { m, answers: allAnswers, facts } =
  useRankedModelText(() => props.item, () => props.longestTripKm, () => props.fuel)

const credit = computed(() => officialModelImageAttribution(m.value.model))
// guests see no answers here, the one gate sits in chapter 1
const answers = computed<RankedModelAnswer[]>(() => props.locked ? [] : allAnswers.value)
</script>
