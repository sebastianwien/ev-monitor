<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ChevronLeftIcon, CheckCircleIcon } from '@heroicons/vue/24/outline'
import {
    surveys,
    surveyText,
    resolveLocalized,
    isPageComplete,
    MAX_TEXT_ANSWER_LENGTH,
    type Localized,
    type SurveyAnswers,
} from '../config/surveys'
import { getSurveyStatus, submitSurvey } from '../api/surveyService'

const route = useRoute()
const { locale } = useI18n()
const slug = route.params.slug as string
const survey = computed(() => surveys[slug] ?? null)

const loc = (value: Localized) => resolveLocalized(value, locale.value)

const loading = ref(true)
const alreadyResponded = ref(false)
const submitted = ref(false)
const submitting = ref(false)
const error = ref<string | null>(null)
const answers = ref<SurveyAnswers>({})
const freeTextValues = ref<Record<string, string>>({})
const pageIndex = ref(0)

const currentPage = computed(() => survey.value?.pages[pageIndex.value] ?? null)
const pageCount = computed(() => survey.value?.pages.length ?? 0)
const isLastPage = computed(() => pageIndex.value === pageCount.value - 1)
const canContinue = computed(() =>
    currentPage.value ? isPageComplete(currentPage.value, answers.value) : false
)

function isOptionSelected(questionKey: string, optionValue: string): boolean {
    const val = answers.value[questionKey]
    return Array.isArray(val) ? val.includes(optionValue) : val === optionValue
}

onMounted(async () => {
    if (!survey.value) {
        loading.value = false
        return
    }
    // Initialize multiple-choice questions as empty arrays
    for (const page of survey.value.pages) {
        for (const q of page.questions) {
            if (q.type !== 'text' && q.multiple) answers.value[q.key] = []
        }
    }
    try {
        const status = await getSurveyStatus(slug)
        alreadyResponded.value = status.responded
    } catch {
        // ignore - user can still try to submit, backend will handle it
    } finally {
        loading.value = false
    }
})

function goToPage(index: number) {
    pageIndex.value = index
    window.scrollTo({ top: 0, behavior: 'smooth' })
}

function next() {
    if (!canContinue.value) return
    if (isLastPage.value) {
        submit()
        return
    }
    goToPage(pageIndex.value + 1)
}

async function submit() {
    if (!canContinue.value || submitting.value) return
    submitting.value = true
    error.value = null
    try {
        const payload: SurveyAnswers = {}
        for (const [key, val] of Object.entries(answers.value)) {
            if (Array.isArray(val)) {
                if (val.length) payload[key] = val
            } else if (val?.trim()) {
                payload[key] = val.trim()
            }
        }
        for (const [key, val] of Object.entries(freeTextValues.value)) {
            if (val.trim()) payload[key] = val.trim()
        }
        await submitSurvey(slug, payload)
        submitted.value = true
    } catch {
        error.value = loc(surveyText.error)
    } finally {
        submitting.value = false
    }
}
</script>

<template>
    <div class="min-h-screen py-0 sm:py-12 px-0 sm:px-4">
        <div class="max-w-md mx-auto bg-white dark:bg-gray-800 shadow-sm border-y sm:border sm:rounded-sm border-gray-200 dark:border-gray-700">

            <!-- Unknown slug -->
            <div v-if="!survey" class="p-8 text-center">
                <p class="text-gray-500 dark:text-gray-400">{{ loc(surveyText.unknown) }}</p>
            </div>

            <!-- Loading -->
            <div v-else-if="loading" class="text-center py-16 text-gray-400 dark:text-gray-500">{{ loc(surveyText.loading) }}</div>

            <!-- Already responded -->
            <div v-else-if="alreadyResponded || submitted" class="p-8 text-center space-y-3">
                <CheckCircleIcon class="w-14 h-14 text-green-600 dark:text-green-400 mx-auto" aria-hidden="true" />
                <h2 class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ loc(surveyText.thanksTitle) }}</h2>
                <p class="text-gray-500 dark:text-gray-400 text-sm">{{ loc(surveyText.thanksBody) }}</p>
            </div>

            <!-- Survey form -->
            <template v-else-if="currentPage">
                <div class="p-5 sm:p-6 border-b border-gray-200 dark:border-gray-700 space-y-4">
                    <div>
                        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ loc(survey.title) }}</h1>
                        <p class="text-gray-500 dark:text-gray-400 text-sm mt-1">{{ loc(survey.description) }}</p>
                    </div>

                    <!-- Progress (multi-page only) -->
                    <div v-if="pageCount > 1" class="space-y-2">
                        <p class="text-xs text-gray-500 dark:text-gray-400">
                            {{ loc(surveyText.step) }} {{ pageIndex + 1 }} {{ loc(surveyText.of) }} {{ pageCount }}
                        </p>
                        <div class="h-1 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden"
                             role="progressbar" :aria-valuenow="pageIndex + 1" aria-valuemin="1" :aria-valuemax="pageCount">
                            <div class="h-full bg-green-600 transition-all duration-300"
                                 :style="{ width: `${((pageIndex + 1) / pageCount) * 100}%` }" />
                        </div>
                    </div>
                </div>

                <div class="p-5 sm:p-6 space-y-6">
                    <h2 v-if="currentPage.title" class="text-lg font-semibold text-gray-900 dark:text-gray-100">
                        {{ loc(currentPage.title) }}
                    </h2>

                    <div v-if="currentPage.info" class="bg-gray-50 dark:bg-gray-700/50 rounded-sm p-4 space-y-2 text-sm text-gray-600 dark:text-gray-400">
                        <p v-for="(paragraph, i) in currentPage.info" :key="i">{{ loc(paragraph) }}</p>
                    </div>

                    <div v-for="question in currentPage.questions" :key="question.key" class="space-y-3">
                        <p class="font-medium text-gray-900 dark:text-gray-100 text-sm" :id="`q-${question.key}`">
                            {{ loc(question.label) }}
                            <span v-if="question.optional" class="font-normal text-gray-400 dark:text-gray-500">({{ loc(surveyText.optionalHint) }})</span>
                        </p>

                        <!-- Free text question -->
                        <template v-if="question.type === 'text'">
                            <textarea
                                v-if="question.multiline"
                                v-model="(answers[question.key] as string)"
                                rows="4"
                                :aria-labelledby="`q-${question.key}`"
                                :maxlength="question.maxLength ?? MAX_TEXT_ANSWER_LENGTH"
                                :placeholder="question.placeholder ? loc(question.placeholder) : ''"
                                class="w-full px-3 py-2 text-sm rounded-sm border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-green-500"
                            />
                            <input
                                v-else
                                v-model="(answers[question.key] as string)"
                                type="text"
                                :aria-labelledby="`q-${question.key}`"
                                :maxlength="question.maxLength ?? MAX_TEXT_ANSWER_LENGTH"
                                :placeholder="question.placeholder ? loc(question.placeholder) : ''"
                                class="w-full px-3 py-2 text-sm rounded-sm border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-green-500"
                            />
                        </template>

                        <!-- Choice question -->
                        <div v-else class="space-y-2">
                            <div v-for="option in question.options" :key="option.value" class="space-y-2">
                                <label
                                    class="flex items-center gap-3 p-3 rounded-sm border cursor-pointer transition-colors"
                                    :class="isOptionSelected(question.key, option.value)
                                        ? 'border-green-500 bg-green-50 dark:bg-green-900/20'
                                        : 'border-gray-200 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500'">
                                    <input
                                        v-if="question.multiple"
                                        type="checkbox"
                                        :value="option.value"
                                        v-model="(answers[question.key] as string[])"
                                        @change="() => { if (!answers[question.key]) answers[question.key] = [] }"
                                        class="accent-green-600"
                                    />
                                    <input
                                        v-else
                                        type="radio"
                                        :name="question.key"
                                        :value="option.value"
                                        v-model="answers[question.key]"
                                        class="accent-green-600"
                                    />
                                    <span class="text-sm text-gray-700 dark:text-gray-300">{{ loc(option.label) }}</span>
                                </label>
                                <input
                                    v-if="option.freeText && isOptionSelected(question.key, option.value)"
                                    v-model="freeTextValues[`${question.key}_detail`]"
                                    type="text"
                                    :maxlength="MAX_TEXT_ANSWER_LENGTH"
                                    :placeholder="loc(option.freeTextPlaceholder ?? '')"
                                    class="w-full px-3 py-2 text-sm rounded-sm border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-green-500"
                                />
                            </div>
                        </div>
                    </div>
                </div>

                <div class="p-5 sm:p-6 border-t border-gray-200 dark:border-gray-700 space-y-3">
                    <p v-if="error" class="text-sm text-red-600 dark:text-red-400 text-center">{{ error }}</p>

                    <div class="flex items-center gap-3">
                        <button
                            v-if="pageIndex > 0"
                            @click="goToPage(pageIndex - 1)"
                            :disabled="submitting"
                            class="flex items-center gap-1 px-4 py-3 text-sm font-medium text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-gray-100 disabled:opacity-50"
                        >
                            <ChevronLeftIcon class="w-4 h-4" aria-hidden="true" />
                            {{ loc(surveyText.back) }}
                        </button>
                        <button
                            @click="next"
                            :disabled="!canContinue || submitting"
                            class="flex-1 bg-green-600 hover:bg-green-700 disabled:bg-gray-300 dark:disabled:bg-gray-700 disabled:cursor-not-allowed text-white font-semibold py-3 rounded-sm transition-colors"
                        >
                            {{ submitting ? loc(surveyText.submitting) : isLastPage ? loc(surveyText.submit) : loc(surveyText.next) }}
                        </button>
                    </div>
                </div>
            </template>
        </div>
    </div>
</template>
