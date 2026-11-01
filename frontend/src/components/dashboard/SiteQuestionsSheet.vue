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
      <div v-if="current" class="flex-1 overflow-y-auto px-4 pb-5 pt-3">
        <div class="flex items-center justify-between">
          <span class="text-[12.5px] tabular-nums text-gray-500 dark:text-gray-400">
            {{ t('site_question.step', { n: step + 1, total: questions.length }) }}
          </span>
          <button type="button" :aria-label="t('common.close')" @click="close"
            class="-mr-2 grid h-11 w-11 place-items-center text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200">
            <XMarkIcon class="h-5 w-5" aria-hidden="true" />
          </button>
        </div>
        <div v-if="questions.length > 1" class="mb-4 flex gap-1" aria-hidden="true">
          <span v-for="(_, i) in questions" :key="i" class="h-1.5 flex-1 rounded-full"
            :class="i <= step ? 'bg-gray-900 dark:bg-gray-100' : 'bg-gray-200 dark:bg-gray-700'" />
        </div>

        <!-- Kopfkarte wie im Lade-Wizard: Karte randlos, nach unten in den Sheet-Hintergrund verlaufend -->
        <div v-if="area" data-testid="site-question-map" class="relative -mx-4 -mb-6 h-36 overflow-hidden" aria-hidden="true">
          <PlaceMinimap :key="current.id" :lat="area.lat" :lon="area.lon" :area="area" :zoom="15" :class="['absolute inset-x-0 top-1/2 -translate-y-1/2', MINIMAP_MAX_CLASS]" />
          <div class="absolute inset-x-0 bottom-0 z-[500] h-16 bg-gradient-to-t from-white to-transparent dark:from-gray-900" />
        </div>

        <h2 class="relative z-[501] text-xl font-bold leading-tight text-gray-900 dark:text-gray-50">{{ t('site_question.title') }}</h2>
        <p class="mt-1 text-sm text-gray-600 dark:text-gray-300">{{ context }}</p>

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
