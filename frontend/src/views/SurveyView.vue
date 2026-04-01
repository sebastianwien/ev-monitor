<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { surveys } from '../config/surveys'
import { getSurveyStatus, submitSurvey } from '../api/surveyService'

const route = useRoute()
const slug = route.params.slug as string
const survey = computed(() => surveys[slug] ?? null)

const loading = ref(true)
const alreadyResponded = ref(false)
const submitted = ref(false)
const submitting = ref(false)
const error = ref<string | null>(null)
const answers = ref<Record<string, string>>({})
const freeTextValues = ref<Record<string, string>>({})

onMounted(async () => {
    if (!survey.value) {
        loading.value = false
        return
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

const allAnswered = computed(() =>
    survey.value?.questions.every(q => answers.value[q.key]) ?? false
)

async function submit() {
    if (!allAnswered.value || submitting.value) return
    submitting.value = true
    error.value = null
    try {
        const payload = { ...answers.value }
        for (const [key, val] of Object.entries(freeTextValues.value)) {
            if (val.trim()) payload[key] = val.trim()
        }
        await submitSurvey(slug, payload)
        submitted.value = true
    } catch {
        error.value = 'Fehler beim Speichern. Bitte versuche es erneut.'
    } finally {
        submitting.value = false
    }
}
</script>

<template>
    <div class="min-h-screen py-12 px-4">
        <div class="max-w-md mx-auto">

            <!-- Unknown slug -->
            <div v-if="!survey" class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 p-8 text-center">
                <p class="text-gray-500 dark:text-gray-400">Diese Umfrage existiert nicht.</p>
            </div>

            <!-- Loading -->
            <div v-else-if="loading" class="text-center py-16 text-gray-400 dark:text-gray-500">Lädt...</div>

            <!-- Already responded -->
            <div v-else-if="alreadyResponded || submitted" class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 p-8 text-center space-y-3">
                <div class="w-14 h-14 bg-green-100 dark:bg-green-900/40 rounded-full flex items-center justify-center mx-auto">
                    <svg class="w-7 h-7 text-green-600 dark:text-green-400" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" d="m4.5 12.75 6 6 9-13.5" />
                    </svg>
                </div>
                <h2 class="text-xl font-bold text-gray-900 dark:text-gray-100">Danke!</h2>
                <p class="text-gray-500 dark:text-gray-400 text-sm">Deine Antworten helfen mir dabei, das richtige Angebot zu bauen.</p>
            </div>

            <!-- Survey form -->
            <div v-else class="space-y-6">
                <div>
                    <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ survey.title }}</h1>
                    <p class="text-gray-500 dark:text-gray-400 text-sm mt-1">{{ survey.description }}</p>
                </div>

                <div v-for="question in survey.questions" :key="question.key"
                     class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 p-5 space-y-3">
                    <p class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ question.label }}</p>
                    <div class="space-y-2">
                        <div v-for="option in question.options" :key="option.value" class="space-y-2">
                            <label
                                class="flex items-center gap-3 p-3 rounded-xl border cursor-pointer transition-colors"
                                :class="answers[question.key] === option.value
                                    ? 'border-green-500 bg-green-50 dark:bg-green-900/20'
                                    : 'border-gray-200 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500'">
                                <input
                                    type="radio"
                                    :name="question.key"
                                    :value="option.value"
                                    v-model="answers[question.key]"
                                    class="accent-green-600"
                                />
                                <span class="text-sm text-gray-700 dark:text-gray-300">{{ option.label }}</span>
                            </label>
                            <input
                                v-if="option.freeText && answers[question.key] === option.value"
                                v-model="freeTextValues[`${question.key}_detail`]"
                                type="text"
                                placeholder="Welches Tool?"
                                class="w-full px-3 py-2 text-sm rounded-xl border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-green-500"
                            />
                        </div>
                    </div>
                </div>

                <p v-if="error" class="text-sm text-red-600 dark:text-red-400 text-center">{{ error }}</p>

                <button
                    @click="submit"
                    :disabled="!allAnswered || submitting"
                    class="w-full bg-green-600 hover:bg-green-700 disabled:bg-gray-300 dark:disabled:bg-gray-700 disabled:cursor-not-allowed text-white font-semibold py-3 rounded-xl transition-colors"
                >
                    {{ submitting ? 'Wird gespeichert...' : 'Absenden' }}
                </button>
            </div>
        </div>
    </div>
</template>
