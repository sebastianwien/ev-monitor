<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CheckCircleIcon, ExclamationTriangleIcon, ClockIcon, EnvelopeIcon, ChevronRightIcon, ChevronDownIcon, XCircleIcon, PauseCircleIcon,
} from '@heroicons/vue/24/outline'
import euDataActSyncService, { type EudaSyncActivity } from '../../api/euDataActSyncService'
import { classifyEudaHealth, MANUFACTURER_AT_FAULT, type EudaHealth } from '../../composables/useEudaHealth'
import { buildEudaComplaintMail } from '../../composables/useEudaComplaintMail'

/**
 * Sync-Protokoll einer Data-Act-Verbindung: Lagebild in Klartext, Fehler sichtbar, auf Wunsch
 * jede Abfrage und jede Lieferung. Dazu die Beschwerde an den Hersteller, wenn der in der
 * Pflicht ist (keine Datenanfrage, nur leere Lieferungen, eingeschlafen).
 * Herstellerneutral: alles kommt aus dem Activity-Vertrag, nichts ist VW-spezifisch.
 */
const props = defineProps<{
  carId: string
  /** Vom Elternteil hochgezaehlt, wenn sich die Verbindung geaendert hat (Historie angefordert, Reconnect). */
  version?: number
}>()

const { t, locale } = useI18n()

const activity = ref<EudaSyncActivity | null>(null)
const loadError = ref(false)
const detailsOpen = ref(false)

async function load() {
  loadError.value = false
  try {
    activity.value = await euDataActSyncService.getActivity(props.carId)
  } catch {
    activity.value = null
    loadError.value = true
  }
}
onMounted(load)
watch(() => [props.carId, props.version], load)

const health = computed<EudaHealth | null>(() => activity.value ? classifyEudaHealth(activity.value) : null)
const manufacturerAtFault = computed(() => health.value !== null && MANUFACTURER_AT_FAULT.has(health.value))
const complaint = computed(() => activity.value ? buildEudaComplaintMail(activity.value, locale.value) : null)

const TONE: Record<EudaHealth, 'ok' | 'wait' | 'warn' | 'bad' | 'off'> = {
  HEALTHY: 'ok', WAITING_FIRST: 'wait', NO_CONTENT: 'warn', STALE: 'warn', HISTORY_FAILED: 'warn',
  NO_REQUEST: 'bad', FAILING: 'bad', AUTH_FAILED: 'bad', PAUSED: 'off',
}
const tone = computed(() => health.value ? TONE[health.value] : 'wait')
const toneClass = computed(() => ({
  ok: 'border-emerald-500 bg-emerald-50 dark:bg-emerald-950/40 text-emerald-900 dark:text-emerald-200',
  wait: 'border-gray-400 bg-gray-50 dark:bg-gray-800/60 text-gray-800 dark:text-gray-200',
  warn: 'border-amber-500 bg-amber-50 dark:bg-amber-950/40 text-amber-900 dark:text-amber-200',
  bad: 'border-red-500 bg-red-50 dark:bg-red-950/40 text-red-900 dark:text-red-200',
  off: 'border-gray-300 bg-gray-50 dark:bg-gray-800/60 text-gray-600 dark:text-gray-400',
}[tone.value]))
const toneIcon = computed(() => ({
  ok: CheckCircleIcon, wait: ClockIcon, warn: ExclamationTriangleIcon, bad: XCircleIcon, off: PauseCircleIcon,
}[tone.value]))

const fmt = (iso: string | null, withTime = true) => iso
  ? new Date(iso).toLocaleDateString(locale.value, { day: '2-digit', month: '2-digit', year: 'numeric', ...(withTime ? { hour: '2-digit', minute: '2-digit' } : {}) })
  : '-'
const kb = (bytes: number) => `${Math.max(1, Math.round(bytes / 1024))} KB`

const summary = computed(() => activity.value?.summary ?? null)
const conn = computed(() => activity.value?.connection ?? null)
const emptyDeliveries = computed(() => summary.value ? summary.value.deliveriesSeen - summary.value.deliveriesWithContent : 0)

const OUTCOME_TONE: Record<string, string> = {
  OK: 'text-emerald-700 dark:text-emerald-300', IMPORTED: 'text-emerald-700 dark:text-emerald-300',
  NO_NEW_DATA: 'text-gray-500 dark:text-gray-400', NO_CHARGING_DATA: 'text-gray-500 dark:text-gray-400',
  PORTAL_ERROR: 'text-red-700 dark:text-red-300', IMPORT_ERROR: 'text-red-700 dark:text-red-300',
  AUTH_FAILED: 'text-red-700 dark:text-red-300', FAILED: 'text-red-700 dark:text-red-300',
}
const outcomeClass = (outcome: string | null) => OUTCOME_TONE[outcome ?? ''] ?? 'text-gray-500 dark:text-gray-400'
const outcomeLabel = (outcome: string | null) => t(`eu_data_act_sync.activity.outcome_${(outcome ?? 'PENDING').toLowerCase()}`)
</script>

<template>
  <section v-if="activity && conn && summary && health" class="space-y-3" data-testid="euda-activity">
    <!-- Lagebild -->
    <div :class="['border-l-2 px-3 py-2 rounded-r-sm text-sm', toneClass]" data-testid="euda-health" :data-health="health">
      <div class="flex items-start gap-2">
        <component :is="toneIcon" class="h-5 w-5 shrink-0 mt-0.5" aria-hidden="true" />
        <p class="font-medium">{{ t(`eu_data_act_sync.activity.health_${health.toLowerCase()}`) }}</p>
      </div>
    </div>

    <div class="text-sm text-gray-700 dark:text-gray-300 space-y-1">
      <p>{{ t('eu_data_act_sync.activity.summary_since', { since: fmt(conn.connectedAt, false), seen: summary.deliveriesSeen, empty: emptyDeliveries, content: summary.deliveriesWithContent }) }}</p>
      <p>
        {{ t('eu_data_act_sync.activity.sessions_imported', { n: summary.sessionsImported }) }}
        <template v-if="conn.lastDataAt"> · {{ t('eu_data_act_sync.activity.last_data', { date: fmt(conn.lastDataAt) }) }}</template>
      </p>
      <p v-if="conn.lastError" class="text-red-700 dark:text-red-300 break-words" data-testid="euda-last-error">
        {{ t('eu_data_act_sync.activity.last_error', { n: conn.consecutiveFailures, date: fmt(conn.lastPolledAt) }) }}
        <span class="font-mono text-xs">{{ conn.lastError }}</span>
      </p>
      <p v-if="conn.history" class="text-gray-600 dark:text-gray-400" data-testid="euda-history-state">
        <template v-if="conn.history.importedAt">{{ t('eu_data_act_sync.history_done', { date: fmt(conn.history.importedAt, false) }) }}</template>
        <template v-else-if="conn.history.running">{{ t('eu_data_act_sync.activity.history_running') }}</template>
        <template v-else-if="conn.history.attemptsExhausted">
          {{ t('eu_data_act_sync.activity.history_exhausted') }}
          <span class="font-mono text-xs">{{ conn.history.error }}</span>
        </template>
        <template v-else>{{ t('eu_data_act_sync.activity.history_requested', { date: fmt(conn.history.requestedAt, false) }) }}</template>
      </p>
    </div>

    <!-- Beschwerde an den Hersteller -->
    <div v-if="complaint && conn.status === 'ACTIVE'" class="space-y-1">
      <a
        :href="complaint.href"
        data-testid="euda-complaint"
        :class="manufacturerAtFault
          ? 'inline-flex items-center gap-1.5 bg-gray-950 dark:bg-white text-white dark:text-gray-950 font-bold uppercase tracking-wider text-[11px] px-4 py-2.5 rounded-sm border-2 border-gray-950 dark:border-white'
          : 'inline-flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-gray-600 dark:text-gray-300 underline hover:no-underline'"
      >
        <EnvelopeIcon class="h-4 w-4" aria-hidden="true" />
        {{ t('eu_data_act_sync.activity.complaint_btn') }}
      </a>
      <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('eu_data_act_sync.activity.complaint_note', { contact: activity.manufacturerContact }) }}</p>
    </div>

    <!-- Details -->
    <button type="button" class="flex items-center gap-1 text-xs font-bold uppercase tracking-wider text-gray-600 dark:text-gray-300" @click="detailsOpen = !detailsOpen" data-testid="euda-activity-toggle">
      <component :is="detailsOpen ? ChevronDownIcon : ChevronRightIcon" class="h-3.5 w-3.5" aria-hidden="true" />
      {{ t('eu_data_act_sync.activity.details_toggle') }}
    </button>
    <div v-if="detailsOpen" class="space-y-4 text-xs">
      <div>
        <h4 class="font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1">{{ t('eu_data_act_sync.activity.polls_title') }}</h4>
        <p v-if="activity.polls.length === 0" class="text-gray-500 dark:text-gray-400">{{ t('eu_data_act_sync.activity.empty') }}</p>
        <ul v-else class="divide-y divide-gray-200 dark:divide-gray-700">
          <li v-for="p in activity.polls" :key="p.at + p.outcome" class="py-1.5 flex flex-wrap gap-x-3 gap-y-0.5">
            <span class="text-gray-500 dark:text-gray-400 tabular-nums">{{ fmt(p.at) }}</span>
            <span :class="['font-medium', outcomeClass(p.outcome)]">{{ p.history ? t('eu_data_act_sync.activity.history_label') + ' · ' : '' }}{{ outcomeLabel(p.outcome) }}</span>
            <span v-if="!p.history" class="text-gray-600 dark:text-gray-300">{{ t('eu_data_act_sync.activity.poll_counts', { seen: p.deliveriesSeen, content: p.deliveriesWithContent, imported: p.sessionsImported }) }}</span>
            <span v-else class="text-gray-600 dark:text-gray-300">{{ t('eu_data_act_sync.activity.sessions_imported', { n: p.sessionsImported }) }}</span>
            <span v-if="p.error" class="w-full font-mono text-red-700 dark:text-red-300 break-words">{{ p.error }}</span>
          </li>
        </ul>
      </div>
      <div>
        <h4 class="font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1">{{ t('eu_data_act_sync.activity.deliveries_title') }}</h4>
        <p v-if="activity.deliveries.length === 0" class="text-gray-500 dark:text-gray-400">{{ t('eu_data_act_sync.activity.empty_deliveries') }}</p>
        <ul v-else class="divide-y divide-gray-200 dark:divide-gray-700">
          <li v-for="d in activity.deliveries" :key="d.filename" class="py-1.5 flex flex-wrap gap-x-3 gap-y-0.5">
            <span class="text-gray-500 dark:text-gray-400 tabular-nums">{{ fmt(d.createdOn) }}</span>
            <span :class="['font-medium', outcomeClass(d.outcome)]">{{ outcomeLabel(d.outcome) }}</span>
            <span v-if="d.sessionsImported !== null" class="text-gray-600 dark:text-gray-300">{{ t('eu_data_act_sync.activity.sessions_imported', { n: d.sessionsImported }) }}</span>
            <span class="text-gray-500 dark:text-gray-400">{{ kb(d.sizeBytes) }}</span>
            <span class="w-full font-mono text-gray-500 dark:text-gray-400 truncate">{{ d.filename }}</span>
            <span v-if="d.error && d.outcome === 'FAILED'" class="w-full font-mono text-red-700 dark:text-red-300 break-words">{{ d.error }}</span>
          </li>
        </ul>
      </div>
      <div>
        <h4 class="font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1">{{ t('eu_data_act_sync.activity.identifiers_title') }}</h4>
        <dl class="grid grid-cols-1 gap-y-0.5">
          <div v-for="id in activity.identifiers" :key="id.label" class="flex flex-wrap gap-x-2">
            <dt class="text-gray-500 dark:text-gray-400">{{ id.label }}</dt>
            <dd class="font-mono break-all text-gray-800 dark:text-gray-200">{{ id.value }}</dd>
          </div>
        </dl>
      </div>
    </div>
  </section>
  <p v-else-if="loadError" class="text-sm text-gray-500 dark:text-gray-400">{{ t('eu_data_act_sync.activity.load_error') }}</p>
</template>
