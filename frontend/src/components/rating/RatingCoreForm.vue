<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import StarRating from './StarRating.vue'
import ChipGroup from './ChipGroup.vue'
import { STAR_QUESTIONS, REBUY_ANSWERS, REBUY_REASONS, TOTAL_COST_VERDICTS } from '../../config/ratingQuestions'
import { setRebuy, type RatingDraft } from '../../utils/ratingForm'
import type { RebuyReason } from '../../api/ratingService'

/** Die sieben Kernfragen auf einem Screen. Fortschritt und Absenden liegen in der View. */
const draft = defineModel<RatingDraft>('draft', { required: true })
defineProps<{ model: string }>()

const { t } = useI18n()

const starLabels = (key: string) => [1, 2, 3, 4, 5].map(n => t(`rating.q.${key}.s${n}`))
const reasonOptions = computed(() =>
    REBUY_REASONS.map(value => ({ value, label: t(`rating.q.rebuy.reasons.${value}`) })))

function updateReasons(value: RebuyReason[]) {
    draft.value.rebuyReasons = value
}
</script>

<template>
    <div class="divide-y divide-gray-200 dark:divide-gray-700">
        <!-- 1 Wiederkauf -->
        <section class="py-5 space-y-3">
            <h2 id="q-rebuy" class="text-base font-semibold text-gray-900 dark:text-gray-100">
                {{ t('rating.q.rebuy.title', { model }) }}
            </h2>
            <div role="radiogroup" aria-labelledby="q-rebuy" class="grid gap-2">
                <button
                    v-for="answer in REBUY_ANSWERS"
                    :key="answer"
                    type="button"
                    role="radio"
                    :aria-checked="draft.rebuy === answer"
                    class="min-h-12 px-4 py-2 rounded-sm border text-left text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-green-600"
                    :class="draft.rebuy === answer
                        ? 'border-green-600 bg-green-50 dark:bg-green-900/30 text-gray-900 dark:text-gray-100'
                        : 'border-gray-200 dark:border-gray-600 text-gray-700 dark:text-gray-300'"
                    @click="setRebuy(draft, answer)"
                >
                    {{ t(`rating.q.rebuy.${answer}`) }}
                </button>
            </div>
            <!-- Kein Layout-Sprung: Warum-Chips blenden über Höhe ein, nicht über v-if-Sprung mitten im Fluss -->
            <div
                class="grid transition-[grid-template-rows] duration-200"
                :class="draft.rebuy === 'BRAND_OTHER_MODEL' ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'"
                :aria-hidden="draft.rebuy !== 'BRAND_OTHER_MODEL'"
            >
                <div class="overflow-hidden">
                    <div v-if="draft.rebuy === 'BRAND_OTHER_MODEL'" class="pt-1 space-y-2">
                        <p id="q-rebuy-why" class="text-sm text-gray-700 dark:text-gray-300">
                            {{ t('rating.q.rebuy.why') }}
                            <span class="text-gray-400 dark:text-gray-500">{{ t('rating.optional') }}</span>
                        </p>
                        <ChipGroup
                            :model-value="draft.rebuyReasons"
                            :options="reasonOptions"
                            :max="REBUY_REASONS.length"
                            labelled-by="q-rebuy-why"
                            @update:model-value="updateReasons"
                        />
                    </div>
                </div>
            </div>
        </section>

        <!-- 2 bis 6 Sterne -->
        <section v-for="q in STAR_QUESTIONS" :key="q.key" class="py-5 space-y-1">
            <h2 :id="`q-${q.key}`" class="text-base font-semibold text-gray-900 dark:text-gray-100">
                {{ t(`rating.q.${q.key}.title`) }}
            </h2>
            <p class="text-sm text-gray-500 dark:text-gray-400">{{ t(`rating.q.${q.key}.sub`) }}</p>
            <StarRating
                v-model="draft[q.field]"
                class="pt-2"
                :labels="starLabels(q.key)"
                :labelled-by="`q-${q.key}`"
                :skip-label="q.skippable ? t(`rating.q.${q.key}.skip`) : undefined"
            />
        </section>

        <!-- 7 Gesamtkosten -->
        <section class="py-5 space-y-3">
            <div>
                <h2 id="q-total-cost" class="text-base font-semibold text-gray-900 dark:text-gray-100">
                    {{ t('rating.q.total_cost.title') }}
                </h2>
                <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('rating.q.total_cost.sub') }}</p>
            </div>
            <div role="radiogroup" aria-labelledby="q-total-cost" class="grid grid-cols-3 gap-2">
                <button
                    v-for="verdict in TOTAL_COST_VERDICTS"
                    :key="verdict"
                    type="button"
                    role="radio"
                    :aria-checked="draft.totalCost === verdict"
                    class="min-h-12 px-2 py-2 rounded-sm border text-center text-sm font-medium leading-tight transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-green-600"
                    :class="draft.totalCost === verdict
                        ? 'border-green-600 bg-green-50 dark:bg-green-900/30 text-gray-900 dark:text-gray-100'
                        : 'border-gray-200 dark:border-gray-600 text-gray-700 dark:text-gray-300'"
                    @click="draft.totalCost = verdict"
                >
                    {{ t(`rating.q.total_cost.${verdict}`) }}
                </button>
            </div>
        </section>
    </div>
</template>
