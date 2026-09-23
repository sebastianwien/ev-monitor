<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getAdminSurveys, getAdminSurveyResponses, type AdminSurveySummary, type AdminSurveyResponse } from '../../api/surveyService'
import { surveys, resolveLocalized, type Localized, type SurveyQuestion } from '../../config/surveys'

const { locale } = useI18n()
const loc = (value: Localized) => resolveLocalized(value, locale.value)

const summaries = ref<AdminSurveySummary[]>([])
const selectedSlug = ref('')
const responses = ref<AdminSurveyResponse[]>([])
const loading = ref(false)
const error = ref('')

const config = computed(() => surveys[selectedSlug.value])
const questions = computed<SurveyQuestion[]>(() => config.value?.pages.flatMap(p => p.questions) ?? [])
const total = computed(() => responses.value.length)

const asList = (v: string | string[] | undefined): string[] => (v == null ? [] : Array.isArray(v) ? v : [v])

function optionCount(key: string, value: string): number {
  return responses.value.filter(r => asList(r.answers[key]).includes(value)).length
}

function pct(n: number): number {
  return total.value ? Math.round((n / total.value) * 100) : 0
}

function texts(key: string): { date: string; text: string }[] {
  return responses.value
    .filter(r => typeof r.answers[key] === 'string' && (r.answers[key] as string).trim())
    .map(r => ({ date: r.createdAt.slice(0, 10), text: r.answers[key] as string }))
}

const knownKeys = computed(() => {
  const keys = new Set<string>()
  for (const q of questions.value) {
    keys.add(q.key)
    keys.add(`${q.key}_detail`)
  }
  return keys
})

const unknownKeys = computed(() => {
  const keys = new Set<string>()
  for (const r of responses.value) for (const k of Object.keys(r.answers)) if (!knownKeys.value.has(k)) keys.add(k)
  return [...keys]
})

function surveyTitle(slug: string): string {
  return surveys[slug] ? loc(surveys[slug].title) : slug
}

async function loadSummaries() {
  try {
    summaries.value = await getAdminSurveys()
  } catch {
    error.value = 'Umfragen konnten nicht geladen werden.'
  }
}

async function loadResponses() {
  if (!selectedSlug.value) return
  loading.value = true
  error.value = ''
  try {
    responses.value = await getAdminSurveyResponses(selectedSlug.value)
  } catch {
    error.value = 'Antworten konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

watch(selectedSlug, loadResponses)
onMounted(loadSummaries)
</script>

<template>
  <div>
    <h2 class="text-lg font-semibold text-white mb-4">Umfragen</h2>

      <div class="flex flex-wrap gap-2 mb-6">
        <button
          v-for="s in summaries"
          :key="s.slug"
          type="button"
          :class="[
            'px-3 py-2 rounded-sm text-sm text-left transition',
            selectedSlug === s.slug ? 'bg-indigo-600 text-white' : 'bg-gray-800 text-gray-300 hover:bg-gray-700'
          ]"
          @click="selectedSlug = s.slug"
        >
          {{ surveyTitle(s.slug) }}
          <span class="ml-1 text-xs opacity-70">({{ s.responses }})</span>
        </button>
      </div>

      <p v-if="error" class="text-red-400 text-sm mb-4">{{ error }}</p>
      <p v-else-if="loading" class="text-gray-400 text-sm">Lädt...</p>
      <p v-else-if="!selectedSlug" class="text-gray-400 text-sm">Umfrage wählen.</p>

      <div v-else class="space-y-6">
        <p class="text-sm text-gray-400">
          <span class="font-mono">{{ selectedSlug }}</span> - {{ total }} Antworten
          <span v-if="!config"> - keine Frontend-Konfiguration, nur Rohdaten</span>
        </p>

        <section v-for="q in questions" :key="q.key" class="bg-gray-900 rounded-sm p-4">
          <h2 class="text-base font-semibold text-white mb-3">{{ loc(q.label) }}</h2>

          <template v-if="q.type !== 'text'">
            <div v-for="o in q.options" :key="o.value" class="mb-2">
              <div class="flex justify-between text-sm mb-1">
                <span>{{ loc(o.label) }}</span>
                <span class="text-gray-400 tabular-nums">{{ optionCount(q.key, o.value) }} ({{ pct(optionCount(q.key, o.value)) }}%)</span>
              </div>
              <div class="h-2 bg-gray-800 rounded-sm overflow-hidden">
                <div class="h-full bg-indigo-500" :style="{ width: pct(optionCount(q.key, o.value)) + '%' }" />
              </div>
            </div>
            <ul v-if="texts(`${q.key}_detail`).length" class="mt-3 space-y-2 border-t border-gray-800 pt-3">
              <li v-for="(t, i) in texts(`${q.key}_detail`)" :key="i" class="text-sm">
                <span class="text-gray-500 text-xs mr-2">{{ t.date }}</span>{{ t.text }}
              </li>
            </ul>
          </template>

          <ul v-else class="space-y-2">
            <li v-for="(t, i) in texts(q.key)" :key="i" class="text-sm whitespace-pre-line">
              <span class="text-gray-500 text-xs mr-2">{{ t.date }}</span>{{ t.text }}
            </li>
            <li v-if="!texts(q.key).length" class="text-sm text-gray-500">Keine Antworten</li>
          </ul>
        </section>

        <section v-for="k in unknownKeys" :key="k" class="bg-gray-900 rounded-sm p-4">
          <h2 class="text-base font-semibold text-white mb-3 font-mono">{{ k }}</h2>
          <ul class="space-y-1">
            <li v-for="(r, i) in responses.filter(r => r.answers[k] != null)" :key="i" class="text-sm">
              <span class="text-gray-500 text-xs mr-2">{{ r.createdAt.slice(0, 10) }}</span>{{ asList(r.answers[k]).join(', ') }}
            </li>
          </ul>
        </section>
      </div>
  </div>
</template>
