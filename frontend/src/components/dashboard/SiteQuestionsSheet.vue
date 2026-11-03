<script setup lang="ts">
import { computed, defineAsyncComponent, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CheckIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import StationTile from '../log-wizard/StationTile.vue'
import { siteQuestionService, type SiteQuestion } from '../../api/siteQuestionService'
import { useCoinStore } from '../../stores/coins'
import { analytics } from '../../services/analytics'
import { geohashArea, MINIMAP_MAX_CLASS } from '../log-wizard/minimapTiles'
const PlaceMinimap = defineAsyncComponent(() => import('../log-wizard/PlaceMinimap.vue'))

/**
 * Frage nach der Säule: ein Ort je Schritt, die Säulen dort als Kacheln. Antippen ordnet alle
 * offenen Ladungen an diesem Ort zu und springt weiter, "Weiß ich nicht" ebenso. Ist am Ende
 * etwas zugeordnet, steht das da; sonst schließt das Sheet von selbst.
 */
const props = defineProps<{ questions: SiteQuestion[] }>()
const emit = defineEmits<{ close: []; answered: [] }>()
const { t, locale } = useI18n()
const coinStore = useCoinStore()

const sheet = ref<InstanceType<typeof BottomSheet> | null>(null)
const step = ref(0)
/** Name der Säule, deren Antwort gerade läuft, bzw. 'unknown' */
const busy = ref<string | null>(null)
const failed = ref(false)
const assigned = ref(0)
const watt = ref(0)

const current = computed<SiteQuestion | null>(() => props.questions[step.value] ?? null)

/** Ort der Frage als Fläche: die Zelle, nicht eine Position darin */
const area = computed(() => current.value?.geohash ? geohashArea(current.value.geohash) : null)

const context = computed(() => {
  const q = current.value
  if (!q) return ''
  const date = new Date(q.lastLoggedAt).toLocaleDateString(locale.value, { weekday: 'short', day: 'numeric', month: 'numeric' })
  const type = q.chargingType === 'AC' || q.chargingType === 'DC' ? ` · ${q.chargingType}` : ''
  return t('site_question.context', { count: q.logCount, date }, q.logCount) + type
})

function next() {
  step.value++
  if (!current.value && assigned.value === 0) sheet.value?.requestClose()
}

async function run(key: string, action: () => Promise<void>) {
  if (busy.value) return
  busy.value = key
  failed.value = false
  try {
    await action()
    next()
  } catch {
    failed.value = true
  } finally {
    busy.value = null
  }
}

function choose(name: string) {
  const q = current.value
  if (!q) return
  return run(name, async () => {
    const result = await siteQuestionService.answer(q.id, name)
    assigned.value += result.assigned
    watt.value += result.watt
    if (result.watt > 0) coinStore.refresh()
    analytics.track('site_question_answered', { logs: result.assigned })
    emit('answered')
  })
}

function unknown() {
  const q = current.value
  if (!q) return
  return run('unknown', async () => {
    await siteQuestionService.dismiss(q.id)
    analytics.track('site_question_unknown')
  })
}
</script>

<template>
  <BottomSheet ref="sheet" edge :label="t('site_question.title')" testid="site-questions-sheet" @close="emit('close')">
    <template #default="{ close }">
      <div v-if="current" class="relative flex-1 overflow-y-auto px-4 pb-5">
        <!-- Griff, Titel und Schließen liegen wie im Lade-Wizard oben; mit Ort schweben sie auf der Karte -->
        <div :class="['z-[600]', area ? 'absolute inset-x-0 top-0 bg-gradient-to-b from-white via-white/80 to-transparent px-4 pb-6 dark:from-gray-800 dark:via-gray-800/80' : '-mx-4 px-4']">
          <!-- Platz für den Griff des Sheets -->
          <div class="h-3.5 sm:hidden" aria-hidden="true" />
          <div class="mt-1 flex items-center">
            <div class="-ml-2 w-11 shrink-0" />
            <h2 class="min-w-0 flex-1 truncate px-2 text-center text-[17px] font-bold text-gray-900 dark:text-gray-50">{{ t('site_question.title') }}</h2>
            <button type="button" :aria-label="t('common.close')" @click="close"
              class="-mr-2 grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10">
              <XMarkIcon class="h-5 w-5" aria-hidden="true" />
            </button>
          </div>
          <div v-if="questions.length > 1" class="mt-2 flex gap-1" role="progressbar" :aria-valuenow="step + 1" aria-valuemin="1" :aria-valuemax="questions.length"
            :aria-label="t('site_question.step', { n: step + 1, total: questions.length })">
            <i v-for="(_, i) in questions" :key="i" class="h-1 flex-1 rounded-full"
              :class="i <= step ? 'bg-gray-900 dark:bg-gray-100' : 'bg-gray-200 dark:bg-gray-700'" />
          </div>
        </div>

        <!-- Karte bis an die Oberkante, die Unterzeile steht im Verlauf nach unten -->
        <div v-if="area" data-testid="site-question-map" class="relative -mx-4 h-60 overflow-hidden">
          <PlaceMinimap :key="current.id" aria-hidden="true" :lat="area.lat" :lon="area.lon" :area="area" :zoom="14" :class="['absolute inset-x-0 top-1/2 -translate-y-1/2', MINIMAP_MAX_CLASS]" />
          <div class="absolute inset-x-0 bottom-0 z-[500] bg-gradient-to-t from-white via-white/70 to-transparent px-4 pb-1 pt-12 dark:from-gray-800 dark:via-gray-800/70">
            <p class="text-sm text-gray-700 dark:text-gray-200">{{ context }}</p>
          </div>
        </div>
        <p v-else class="mt-2 text-center text-sm text-gray-600 dark:text-gray-300">{{ context }}</p>

        <div class="mt-4 space-y-3">
          <StationTile v-for="c in current.candidates" :key="c.name" data-testid="site-question-candidate"
            :station="c" :selected="busy === c.name" :disabled="!!busy" @click="choose(c.name)" />
        </div>

        <p v-if="failed" role="alert" data-testid="site-question-error" class="mt-3 text-sm text-gray-900 dark:text-gray-100">
          {{ t('site_question.error') }}
        </p>

        <button type="button" data-testid="site-question-unknown" :disabled="!!busy" @click="unknown"
          class="mt-3 min-h-11 w-full text-center text-sm font-semibold text-gray-600 underline underline-offset-2 disabled:opacity-50 dark:text-gray-300">
          {{ t('site_question.unknown') }}
        </button>
      </div>

      <div v-else class="flex flex-col items-center px-6 pb-6 pt-8 text-center" data-testid="site-question-done">
        <span class="grid h-14 w-14 place-items-center rounded-full bg-green-100 text-green-700 dark:bg-green-400/15 dark:text-green-300">
          <CheckIcon class="h-7 w-7" aria-hidden="true" />
        </span>
        <h2 class="mt-4 text-xl font-bold text-gray-900 dark:text-gray-50">{{ t('site_question.done_title', { count: assigned }, assigned) }}</h2>
        <p class="mt-1 max-w-xs text-sm text-gray-600 dark:text-gray-300">{{ t('site_question.done_text') }}</p>
        <p v-if="watt > 0" class="mt-2 text-sm font-semibold tabular-nums text-gray-900 dark:text-gray-100">+{{ watt }} W</p>
        <button type="button" @click="close"
          class="btn-3d mt-6 min-h-11 w-full rounded-xl border-2 border-edge bg-green-600 px-4 text-sm font-semibold text-white hover:bg-green-700">
          {{ t('site_question.done') }}
        </button>
      </div>
    </template>
  </BottomSheet>
</template>
