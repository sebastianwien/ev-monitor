<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon, PlusIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import ChipGroup from './ChipGroup.vue'
import { RATING_CHIPS } from '../../config/ratingQuestions'
import {
    reviewKind, MAX_BODY_CHARS, MAX_ITEM_CHARS, MAX_LIST_ITEMS, REPORT_MIN_CHARS, type RatingDraft,
} from '../../utils/ratingForm'
import type { ReviewState } from '../../api/ratingService'

/** Stärken, Schwächen und der Erfahrungstext. Alles optional. */
const draft = defineModel<RatingDraft>('draft', { required: true })
const props = defineProps<{ model: string; review: ReviewState | null }>()
const { t, n } = useI18n()

const guideOpen = ref(false)
const chipOptions = computed(() => RATING_CHIPS.map(value => ({ value, label: t(`rating.chips.${value}`) })))
const kind = computed(() => reviewKind(draft.value.body))
const length = computed(() => draft.value.body.trim().length)
/** Ein bestehender Text bleibt, wenn das Feld geleert wird - löschen geht nicht über das Formular. */
const keepsExisting = computed(() => !!props.review && length.value === 0)

function addItem(list: 'pros' | 'cons') {
    if (draft.value[list].length < MAX_LIST_ITEMS) draft.value[list] = [...draft.value[list], '']
}

function removeItem(list: 'pros' | 'cons', index: number) {
    draft.value[list] = draft.value[list].filter((_, i) => i !== index)
}
</script>

<template>
    <div class="divide-y divide-gray-200 dark:divide-gray-700">
        <section class="py-5 space-y-3">
            <label for="deep-body" class="block text-base font-semibold text-gray-900 dark:text-gray-100">{{ t('rating.deep.experience') }}</label>

            <button
                type="button"
                class="flex items-center gap-1 min-h-11 text-sm font-medium text-green-700 dark:text-green-400"
                :aria-expanded="guideOpen"
                aria-controls="deep-guide"
                @click="guideOpen = !guideOpen"
            >
                <ChevronDownIcon class="w-4 h-4 transition-transform" :class="{ 'rotate-180': guideOpen }" aria-hidden="true" />
                {{ t('rating.deep.guide_toggle') }}
            </button>
            <ol v-show="guideOpen" id="deep-guide" class="list-decimal pl-5 space-y-1 text-sm text-gray-600 dark:text-gray-300">
                <li v-for="i in 5" :key="i">{{ t(`rating.deep.guide_${i}`, { model }) }}</li>
            </ol>

            <textarea
                id="deep-body"
                v-model="draft.body"
                rows="8"
                :maxlength="MAX_BODY_CHARS"
                :placeholder="t('rating.deep.placeholder', { model })"
                aria-describedby="deep-body-count deep-moderation"
                class="w-full px-3 py-2 text-base rounded-sm border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-green-500"
            />
            <div id="deep-body-count" class="flex justify-between gap-3 text-xs text-gray-500 dark:text-gray-400" aria-live="polite">
                <span>
                    <template v-if="kind === 'report'">{{ t('rating.deep.is_report') }}</template>
                    <template v-else-if="kind === 'quote'">{{ t('rating.deep.is_quote', { n: REPORT_MIN_CHARS - length }) }}</template>
                    <template v-else-if="keepsExisting">{{ t('rating.deep.empty_keeps') }}</template>
                    <template v-else>{{ t('rating.deep.report_hint') }}</template>
                </span>
                <span class="tabular-nums shrink-0">{{ n(length) }} / {{ n(MAX_BODY_CHARS) }}</span>
            </div>

            <div class="grid gap-4 sm:grid-cols-2">
                <div v-for="list in (['pros', 'cons'] as const)" :key="list" class="space-y-2">
                    <p :id="`deep-${list}`" class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ t(`rating.deep.${list}`) }}</p>
                    <div v-for="(_, index) in draft[list]" :key="index" class="flex gap-2">
                        <input
                            v-model="draft[list][index]"
                            type="text"
                            :maxlength="MAX_ITEM_CHARS"
                            :aria-labelledby="`deep-${list}`"
                            :placeholder="t(`rating.deep.${list}_placeholder`)"
                            class="flex-1 min-w-0 min-h-11 px-3 text-base rounded-sm border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:border-green-500"
                        />
                        <button
                            type="button"
                            class="w-11 h-11 flex items-center justify-center rounded-sm text-gray-500 hover:text-gray-900 dark:hover:text-gray-100"
                            :aria-label="t('rating.deep.remove_item')"
                            @click="removeItem(list, index)"
                        >
                            <XMarkIcon class="w-5 h-5" aria-hidden="true" />
                        </button>
                    </div>
                    <button
                        v-if="draft[list].length < MAX_LIST_ITEMS"
                        type="button"
                        class="flex items-center gap-1 min-h-11 text-sm font-medium text-green-700 dark:text-green-400"
                        @click="addItem(list)"
                    >
                        <PlusIcon class="w-4 h-4" aria-hidden="true" />
                        {{ t('rating.deep.add_item') }}
                    </button>
                </div>
            </div>

            <label class="flex items-start gap-3 min-h-11 pt-2 text-sm text-gray-700 dark:text-gray-300">
                <input v-model="draft.deleteWithCar" type="checkbox" class="mt-0.5 w-5 h-5 accent-green-600" />
                <span>{{ t('rating.deep.delete_with_car') }}</span>
            </label>
            <p id="deep-moderation" class="text-xs text-gray-500 dark:text-gray-400">{{ t('rating.deep.moderation') }}</p>
        </section>
        <section class="py-5 space-y-2">
            <h2 id="deep-strengths" class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ t('rating.deep.strengths') }}</h2>
            <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('rating.deep.up_to') }}</p>
            <ChipGroup v-model="draft.strengths" :options="chipOptions" labelled-by="deep-strengths" />
        </section>

        <section class="py-5 space-y-2">
            <h2 id="deep-weaknesses" class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ t('rating.deep.weaknesses') }}</h2>
            <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('rating.deep.up_to') }}</p>
            <ChipGroup v-model="draft.weaknesses" :options="chipOptions" labelled-by="deep-weaknesses" />
        </section>

    </div>
</template>
