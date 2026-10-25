<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronLeftIcon, ChevronRightIcon, XMarkIcon, PlusIcon } from '@heroicons/vue/24/outline'
import api from '../../../api/axios'
import BottomSheet from '../../shared/BottomSheet.vue'
import LogEditPanel, { type EvLogResponse } from '../../dashboard/LogEditPanel.vue'
import DayRibbon from './DayRibbon.vue'
import DayTimeline from './DayTimeline.vue'
import DayTripPanel from './DayTripPanel.vue'
import { useIsMobile } from '../../../composables/useIsMobile'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import { useLogsRefreshStore } from '../../../stores/logsRefresh'
import { analytics } from '../../../services/analytics'
import { toLocalDatetimeInput } from '../../../utils/tripForm'
import {
  dayRange, shiftDay, eventsOfDay, daySummary, dayAtNow, type DayEvent, type DayLog, type DayTrip,
} from '../../../composables/storyDay'

/**
 * Tagesblatt: alles, was an einem Tag war. Handy und Tablet: Sheet mit zwei Ebenen (Tag, dann
 * Eintrag von rechts). Ab 1024 px Fenster mit zwei Spalten. Die Zurück-Taste geht erst eine Ebene
 * zurück, dann schließt sie das Sheet, nie die Seite.
 */
const props = defineProps<{ carId: string; today: string }>()
const day = defineModel<string>('day', { required: true })
const emit = defineEmits<{ close: []; changed: []; 'add-charge': [loggedAt: string] }>()
const { t, locale } = useI18n()
const { formatDistance, formatCurrency, formatDecimal } = useLocaleFormat()
const wide = useIsMobile('(min-width: 1024px)')

type Detail = { kind: 'charge'; log: EvLogResponse } | { kind: 'trip'; trip: DayTrip | null }
const detail = ref<Detail | null>(null)

// ── Daten des Tages ──────────────────────────────────────────────────────────
const logs = ref<(EvLogResponse & DayLog)[]>([])
const trips = ref<DayTrip[]>([])
const loading = ref(false)
const failed = ref(false)
let request = 0
let opened = false
async function load() {
  const mine = ++request
  const { from, to } = dayRange(day.value)
  const params = { carId: props.carId, from, to }
  loading.value = true
  failed.value = false
  try {
    const [l, tr] = await Promise.all([api.get('/logs', { params }), api.get('/trips', { params })])
    if (mine !== request) return
    logs.value = l.data
    trips.value = tr.data
    if (!opened) {
      opened = true
      analytics.track('story_day_open', { has_entries: events.value.length ? 'ja' : 'nein' })
    }
  } catch {
    if (mine === request) failed.value = true
  } finally {
    if (mine === request) loading.value = false
  }
}
const events = computed(() => eventsOfDay(day.value, trips.value, logs.value))
const summary = computed(() => daySummary(events.value))
watch(day, () => { detail.value = null; void load() }, { immediate: true })
// Rückgängig im Lösch-Toast stellt wieder her, auch wenn wir offen sind
watch(() => useLogsRefreshStore().version, () => void load())

const date = computed(() => {
  const [y, m, d] = day.value.split('-').map(Number)
  const dt = new Date(y, m - 1, d)
  return {
    weekday: dt.toLocaleDateString(locale.value, { weekday: 'long' }),
    long: dt.toLocaleDateString(locale.value, { day: 'numeric', month: 'long', year: 'numeric' }),
  }
})
const canNext = computed(() => shiftDay(day.value, 1) <= props.today)
const title = ref<HTMLElement | null>(null)
function go(n: number) {
  if (n > 0 && !canNext.value) return
  day.value = shiftDay(day.value, n)
  void nextTick(() => title.value?.focus())
}

// ── Ebenen und Zurück-Taste ──────────────────────────────────────────────────
const sheet = ref<InstanceType<typeof BottomSheet> | null>(null)
let layers = 0
function pushLayer() {
  history.pushState({ ...history.state, storyDayLayer: ++layers }, '')
}
function onPop() {
  if (layers === 0) return
  layers--
  if (detail.value) detail.value = null
  else sheet.value?.requestClose()
}
onMounted(() => { pushLayer(); window.addEventListener('popstate', onPop) })
onUnmounted(() => {
  window.removeEventListener('popstate', onPop)
  // Geschlossen per Kreuz, Hintergrund oder Nachtragen: eigene Einträge aus dem Verlauf nehmen
  if (layers > 0) history.go(-layers)
})
function openDetail(d: Detail) {
  const had = !!detail.value
  detail.value = d
  if (!had) pushLayer()
}
const back = () => history.back()
function select(e: DayEvent) {
  openDetail(e.kind === 'charge' ? { kind: 'charge', log: e.log as EvLogResponse & DayLog } : { kind: 'trip', trip: e.trip })
}

// ── Aktionen ─────────────────────────────────────────────────────────────────
type Action = 'edit_charge' | 'edit_trip' | 'delete' | 'merge' | 'add_charge' | 'add_trip'
function done(action: Action) {
  analytics.track('story_day_action', { action })
  emit('changed')
  if (detail.value) back()
  void load()
}
const merging = ref(false)
const mergeFailed = ref(false)
async function merge(a: DayEvent, b: DayEvent) {
  merging.value = true
  mergeFailed.value = false
  try {
    await api.post(`/trips/${a.id}/merge`, { mergeWithTripId: b.id })
    done('merge')
  } catch {
    mergeFailed.value = true
  } finally {
    merging.value = false
  }
}
const newEnd = computed(() => dayAtNow(day.value))
function addCharge() {
  analytics.track('story_day_action', { action: 'add_charge' })
  emit('add-charge', toLocalDatetimeInput(dayAtNow(day.value).toISOString()))
}
const selectedId = computed(() => detail.value?.kind === 'charge' ? detail.value.log.id : detail.value?.trip?.id ?? null)
</script>

<template>
  <BottomSheet ref="sheet" :label="`${date.weekday}, ${date.long}`" testid="story-day-sheet" edge
    panel-class="h-[88dvh] sm:max-w-xl lg:h-[82vh] lg:max-w-5xl" @close="emit('close')">
    <div class="relative flex min-h-0 flex-1 bg-white dark:bg-gray-900 lg:grid lg:grid-cols-2">
      <!-- Ebene 1: der Tag -->
      <section class="flex min-h-0 min-w-0 flex-1 flex-col" :inert="!wide && !!detail" data-testid="day-pane">
        <header class="shrink-0 px-4 pb-3 pt-2 lg:px-6 lg:pt-5">
          <div class="mx-auto mb-1 h-1.5 w-10 rounded-full bg-gray-200 sm:hidden dark:bg-gray-700" aria-hidden="true" />
          <div class="flex items-center gap-1">
            <button type="button" data-testid="day-prev" :aria-label="t('storyday.prev')" @click="go(-1)"
              class="-ml-2 grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10">
              <ChevronLeftIcon class="h-5 w-5" />
            </button>
            <div class="min-w-0 flex-1 text-center">
              <p class="text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ date.weekday }}<template v-if="day === today"> · {{ t('storyday.today') }}</template></p>
              <h2 ref="title" tabindex="-1" data-testid="day-title" class="text-[22px] font-bold leading-tight tracking-tight outline-none lg:text-[24px]">{{ date.long }}</h2>
            </div>
            <button type="button" data-testid="day-next" :aria-label="t('storyday.next')" :disabled="!canNext" @click="go(1)"
              class="grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-600 hover:bg-gray-100 disabled:opacity-25 dark:text-gray-300 dark:hover:bg-white/10">
              <ChevronRightIcon class="h-5 w-5" />
            </button>
            <button type="button" data-testid="day-close" :aria-label="t('story.sheet_close')" @click="sheet?.requestClose()"
              class="btn-3d -mr-1 ml-1 grid h-11 w-11 shrink-0 place-items-center rounded-full border-2 border-gray-900 bg-white dark:border-transparent dark:bg-gray-800">
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>
          <div v-if="summary.trips || summary.charges" class="mt-2 flex flex-wrap justify-center gap-1.5 text-[13px] font-semibold tabular-nums" data-testid="day-pills">
            <span v-if="summary.trips" class="rounded-full bg-gray-100 px-2.5 py-1 dark:bg-white/10">
              {{ t('storyday.pill_trips', { n: summary.trips }, summary.trips) }} · {{ formatDistance(summary.km, { round: true }) }}
            </span>
            <span v-if="summary.charges" class="rounded-full bg-gray-100 px-2.5 py-1 dark:bg-white/10">
              {{ t('storyday.pill_charges', { n: summary.charges }, summary.charges) }}<template v-if="summary.kwh"> · {{ formatDecimal(summary.kwh, 0) }} kWh</template><template v-if="summary.eur"> · {{ formatCurrency(summary.eur) }}</template>
            </span>
          </div>
        </header>

        <div class="min-h-0 flex-1 overflow-y-auto overscroll-contain px-4 pb-4 lg:px-6" aria-live="polite" :aria-busy="loading">
          <p v-if="failed" class="py-10 text-center text-[15px] text-gray-600 dark:text-gray-300">
            {{ t('storyday.load_failed') }}
            <button type="button" class="ml-1 min-h-11 font-semibold underline" @click="load">{{ t('story.retry') }}</button>
          </p>
          <template v-else-if="events.length">
            <DayRibbon :events="events" :selected-id="selectedId" :interactive="wide" @select="select" />
            <h3 class="mb-1 mt-5 text-[13px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t('storyday.flow') }}</h3>
            <p v-if="mergeFailed" class="text-sm text-gray-700 dark:text-gray-200">{{ t('storyday.merge_failed') }}</p>
            <DayTimeline :events="events" :selected-id="selectedId" :merging="merging" @select="select" @merge="merge" />
          </template>
          <div v-else-if="!loading" class="grid place-items-center gap-2 py-10 text-center" data-testid="day-empty">
            <p class="text-[17px] font-semibold">{{ t('storyday.empty_title') }}</p>
            <p class="max-w-xs text-[14px] text-gray-500 dark:text-gray-400">{{ t('storyday.empty_text') }}</p>
          </div>
        </div>

        <footer class="shrink-0 border-t-2 border-gray-100 px-4 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-3 dark:border-gray-800 lg:px-6">
          <div class="flex gap-2">
            <button type="button" data-testid="day-add-charge" @click="addCharge"
              class="btn-3d inline-flex min-h-11 flex-1 items-center justify-center gap-1.5 whitespace-nowrap rounded-xl border-2 border-gray-900 bg-green-600 px-2 text-[14px] font-semibold text-white dark:border-transparent">
              <PlusIcon class="h-4 w-4" aria-hidden="true" />{{ t('storyday.add_charge') }}
            </button>
            <button type="button" data-testid="day-add-trip" @click="openDetail({ kind: 'trip', trip: null })"
              class="btn-3d inline-flex min-h-11 flex-1 items-center justify-center gap-1.5 whitespace-nowrap rounded-xl border-2 border-gray-900 bg-white px-2 text-[14px] font-semibold dark:border-transparent dark:bg-white/10">
              <PlusIcon class="h-4 w-4" aria-hidden="true" />{{ t('storyday.add_trip') }}
            </button>
          </div>
        </footer>
      </section>

      <!-- Ebene 2: ein Eintrag. Handy/Tablet schiebt von rechts, ab 1024 px rechte Spalte -->
      <Transition :name="wide ? '' : 'day-detail'">
        <section v-if="detail" :key="selectedId ?? 'new'" data-testid="day-detail"
          class="absolute inset-0 z-10 flex min-h-0 flex-col bg-white dark:bg-gray-900 lg:static lg:border-l-2 lg:border-gray-100 lg:dark:border-gray-800">
          <LogEditPanel v-if="detail.kind === 'charge'" :log="detail.log" dismiss="back"
            @cancel="back" @saved="done('edit_charge')" @deleted="done('delete')" />
          <DayTripPanel v-else :trip="detail.trip" :car-id="carId" :new-end="newEnd"
            @back="back" @saved="done" @deleted="done('delete')" />
        </section>
      </Transition>
      <p v-if="wide && !detail" class="hidden place-self-center px-6 text-center text-[14px] text-gray-500 lg:block dark:text-gray-400">
        {{ t('storyday.pick') }}
      </p>
    </div>
  </BottomSheet>
</template>

<style scoped>
.day-detail-enter-active,
.day-detail-leave-active {
  transition: transform 0.28s cubic-bezier(0.32, 0.72, 0, 1);
}
.day-detail-enter-from,
.day-detail-leave-to {
  transform: translateX(100%);
}
@media (prefers-reduced-motion: reduce) {
  .day-detail-enter-active,
  .day-detail-leave-active { transition: none; }
}
</style>
