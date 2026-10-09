<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CheckCircleIcon } from '@heroicons/vue/24/outline'
import { STAR_QUESTIONS } from '../../config/ratingQuestions'
import { MIN_FOR_AVERAGE } from '../../utils/ratingForm'
import type { RatingResult } from '../../api/ratingService'

/**
 * Persönliches Ergebnis nach dem Absenden. Öffentlich nie Prozente, hier nur "kritischer als X Prozent"
 * und das erst ab 15 Bewertungen (liefert der Server als standing).
 */
const props = defineProps<{ result: RatingResult; model: string; coins: number }>()
const { t, n } = useI18n()


const rows = computed(() => {
    const categories = props.result.categories
    if (!categories) return []
    return STAR_QUESTIONS
        .map(q => ({ key: q.key, avg: categories[q.field], mine: props.result.mine[q.field] }))
        .filter(r => r.avg.average !== null)
})

/** Die größere der beiden Aussagen zeigen, bei Gleichstand die zufriedenere. */
const standingText = computed(() => {
    const s = props.result.standing
    if (!s) return null
    return s.moreCriticalThanPercent > s.moreSatisfiedThanPercent
        ? t('rating.result.more_critical', { p: s.moreCriticalThanPercent, model: props.model })
        : t('rating.result.more_satisfied', { p: s.moreSatisfiedThanPercent, model: props.model })
})

const formatAvg = (value: number) => n(value, { minimumFractionDigits: 1, maximumFractionDigits: 1 })
</script>

<template>
    <div class="divide-y divide-gray-200 dark:divide-gray-700">
        <section class="py-5 space-y-2">
            <h2 class="text-base font-semibold text-gray-900 dark:text-gray-100">
                {{ t('rating.result.title', { count: result.count, model }, result.count) }}
            </h2>
            <p class="flex flex-wrap items-baseline gap-x-2">
                <span class="text-4xl font-bold tabular-nums whitespace-nowrap text-green-700 dark:text-green-400">
                    {{ t('rating.result.rebuy_main', { yes: result.rebuy.yes, count: result.count }) }}
                </span>
                <span class="text-sm text-gray-600 dark:text-gray-300">{{ t('rating.result.rebuy_label', result.count) }}</span>
            </p>
            <ul class="text-sm text-gray-500 dark:text-gray-400 space-y-0.5">
                <li v-if="result.rebuy.brandOtherModel">
                    {{ t('rating.result.rebuy_brand', { n: result.rebuy.brandOtherModel }, result.rebuy.brandOtherModel) }}
                </li>
                <li v-if="result.rebuy.no">{{ t('rating.result.rebuy_no', { n: result.rebuy.no }, result.rebuy.no) }}</li>
            </ul>
        </section>

        <section class="py-5 space-y-4">
            <p v-if="!result.categories" class="text-sm text-gray-600 dark:text-gray-300">
                {{ t('rating.result.first_drivers', { n: result.count, model, missing: MIN_FOR_AVERAGE - result.count }) }}
            </p>
            <template v-else>
                <p v-if="standingText" class="rounded-sm bg-indigo-50 dark:bg-indigo-900/30 p-3 text-sm font-medium text-indigo-800 dark:text-indigo-200">
                    {{ standingText }}
                </p>
                <div v-for="row in rows" :key="row.key" class="space-y-1">
                    <div class="flex justify-between text-sm">
                        <span class="text-gray-900 dark:text-gray-100">{{ t(`rating.result.categories.${row.key}`) }}</span>
                        <span class="tabular-nums text-gray-500 dark:text-gray-400">
                            {{ t('rating.result.avg', { v: formatAvg(row.avg.average!) }) }}<template v-if="row.mine !== null"> · {{ t('rating.result.you', { v: row.mine }) }}</template>
                        </span>
                    </div>
                    <div class="relative h-2.5 rounded-full bg-gray-200 dark:bg-gray-700" aria-hidden="true">
                        <div class="absolute inset-y-0 left-0 rounded-full bg-green-600/40 dark:bg-green-400/40"
                             :style="{ width: `${(row.avg.average! / 5) * 100}%` }" />
                        <div v-if="row.mine !== null"
                             class="absolute -top-1 h-4.5 w-1 -translate-x-1/2 rounded-sm bg-indigo-600 dark:bg-indigo-300"
                             :style="{ left: `${(row.mine / 5) * 100}%` }" />
                    </div>
                </div>
                <p class="flex gap-4 text-xs text-gray-500 dark:text-gray-400">
                    <span class="flex items-center gap-1.5"><i class="inline-block w-2.5 h-2.5 rounded-sm bg-green-600/40 dark:bg-green-400/40" />{{ t('rating.result.legend_avg') }}</span>
                    <span class="flex items-center gap-1.5"><i class="inline-block w-2.5 h-2.5 rounded-sm bg-indigo-600 dark:bg-indigo-300" />{{ t('rating.result.legend_you') }}</span>
                </p>
            </template>
        </section>

        <section class="py-5">
            <p class="flex items-center gap-3 rounded-sm bg-green-50 dark:bg-green-900/30 p-3 text-sm text-gray-900 dark:text-gray-100">
                <CheckCircleIcon class="w-6 h-6 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
                <span v-if="coins > 0">{{ t('rating.result.coins', { n: coins }) }}</span>
                <span v-else>{{ t('rating.result.updated') }}</span>
            </p>
        </section>
    </div>
</template>
