<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CheckCircleIcon, ExclamationTriangleIcon, ClockIcon, EnvelopeIcon, ChevronRightIcon, ChevronDownIcon,
  XCircleIcon, PauseCircleIcon, ArrowPathIcon, LockClosedIcon,
} from '@heroicons/vue/24/outline'
import euDataActSyncService, { type EudaSyncActivity, type EudaConnectionStatus } from '../../api/euDataActSyncService'
import { classifyEudaHealth, MANUFACTURER_AT_FAULT, type EudaHealth } from '../../composables/useEudaHealth'
import { buildEudaComplaintMail } from '../../composables/useEudaComplaintMail'
import { deriveEudaPrimaryAction, isEudaHistoryOpen } from '../../composables/useEudaPrimaryAction'
import EudaAuthorityComplaint from './EudaAuthorityComplaint.vue'

/**
 * Das komplette Panel einer verbundenen Data-Act-Verbindung, in vier Zonen:
 * Lagebild (ein Satz plus Zahlen), Fakten (Konto, letzte Daten, Historie, Zugang),
 * genau eine primäre Handlung aus dem Zustand abgeleitet plus Sekundärlinks, Details auf Wunsch.
 * Kommt das Protokoll nicht (Connector down), bleiben Fakten und Handlungen aus dem Status-DTO.
 * Herstellerneutral: alles kommt aus dem Activity-Vertrag.
 */
const props = defineProps<{
  connection: EudaConnectionStatus
  /** Vom Elternteil hochgezählt, wenn sich die Verbindung geändert hat (Historie angefordert, Reconnect). */
  version?: number
  trialEndsAt?: string
  historyPending?: boolean
  busy?: boolean
}>()
const emit = defineEmits<{ requestHistory: []; reactivateSmartcar: []; disconnect: [] }>()

const { t, locale } = useI18n()

const activity = ref<EudaSyncActivity | null>(null)
const detailsOpen = ref(false)
const authorityOpen = ref(false)

async function load() {
  try {
    const a = await euDataActSyncService.getActivity(props.connection.carId)
    activity.value = a?.connection ? a : null
  } catch {
    activity.value = null
  }
}
onMounted(load)
watch(() => [props.connection.carId, props.version], load)

const status = computed(() => props.connection.status)
const health = computed<EudaHealth | null>(() => activity.value ? classifyEudaHealth(activity.value) : null)
const manufacturerAtFault = computed(() => health.value !== null && MANUFACTURER_AT_FAULT.has(health.value))
const complaint = computed(() => activity.value ? buildEudaComplaintMail(activity.value, locale.value) : null)

type Tone = 'ok' | 'wait' | 'warn' | 'bad' | 'off'
const TONE: Record<EudaHealth, Tone> = {
  HEALTHY: 'ok', WAITING_FIRST: 'wait', NO_CONTENT: 'warn', STALE: 'warn', HISTORY_FAILED: 'warn',
  NO_REQUEST: 'bad', FAILING: 'bad', AUTH_FAILED: 'bad', PAUSED: 'off',
}
const tone = computed<Tone>(() => {
  if (health.value) return TONE[health.value]
  return status.value === 'ACTIVE' ? 'wait' : status.value === 'EXPIRED' ? 'off' : 'bad'
})
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

/** Überschrift des Lagebilds: aus dem Protokoll, sonst aus dem Verbindungsstatus. */
const headline = computed(() => health.value
  ? t(`eu_data_act_sync.activity.health_${health.value.toLowerCase()}`)
  : t(`eu_data_act_sync.activity.status_only_${status.value.toLowerCase()}`))

const fmt = (iso: string | null | undefined, withTime = true) => iso
  ? new Date(iso).toLocaleDateString(locale.value, { day: '2-digit', month: '2-digit', year: 'numeric', ...(withTime ? { hour: '2-digit', minute: '2-digit' } : {}) })
  : null
const kb = (bytes: number) => `${Math.max(1, Math.round(bytes / 1024))} KB`

const summary = computed(() => activity.value?.summary ?? null)
const conn = computed(() => activity.value?.connection ?? null)
const emptyDeliveries = computed(() => summary.value ? summary.value.deliveriesSeen - summary.value.deliveriesWithContent : 0)

/** Erste Fehlerzeile ohne Antwortkörper: "Anfrage anlegen HTTP 400". Der volle Text steht in den Details. */
const shortError = computed(() => {
  const e = conn.value?.lastError ?? props.connection.lastError
  if (!e) return null
  const head = e.split(/[:{]/)[0].trim()
  return head.length > 0 && head.length < e.length ? head : e.slice(0, 80)
})

const lastDataLabel = computed(() => fmt(conn.value?.lastDataAt ?? props.connection.lastSuccessAt) ?? t('eu_data_act_sync.activity.no_data_yet'))

const historyLabel = computed(() => {
  const h = conn.value?.history
  const importedAt = h?.importedAt ?? props.connection.historyImportedAt
  if (importedAt) return t('eu_data_act_sync.activity.history_fact_done', { date: fmt(importedAt, false) })
  if (h?.running) return t('eu_data_act_sync.activity.history_running')
  if (h?.attemptsExhausted) return t('eu_data_act_sync.activity.history_fact_failed')
  if (h?.requestedAt || props.historyPending) return t('eu_data_act_sync.activity.history_fact_requested')
  return t('eu_data_act_sync.activity.history_fact_none')
})
const historyOpen = computed(() => isEudaHistoryOpen(conn.value?.history, props.connection.historyImportedAt, props.historyPending ?? false))
const primary = computed(() => deriveEudaPrimaryAction({
  status: status.value, health: health.value, hasComplaint: complaint.value !== null, historyOpen: historyOpen.value,
}))
const primaryClass = 'inline-flex items-center justify-center gap-1.5 w-full sm:w-auto font-bold uppercase tracking-wider text-[11px] px-4 py-2.5 rounded-sm border-2 disabled:opacity-60'
const secondaryClass = 'inline-flex items-center gap-1.5 min-h-[44px] text-[11px] font-bold uppercase tracking-wider px-3.5 py-2 rounded-sm border-2 border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-200 hover:border-gray-500 dark:hover:border-gray-400 disabled:opacity-60'

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
  <section class="space-y-4" data-testid="euda-activity">
    <!-- 1. Lagebild -->
    <div :class="['border-l-2 px-3 py-2.5 rounded-r-sm text-sm', toneClass]" data-testid="euda-health" :data-health="health ?? status">
      <div class="flex items-start gap-2">
        <component :is="toneIcon" class="h-5 w-5 shrink-0 mt-0.5" aria-hidden="true" />
        <div class="min-w-0 space-y-0.5">
          <p class="font-medium">{{ headline }}</p>
          <p v-if="summary && conn" class="text-xs opacity-80">
            {{ t('eu_data_act_sync.activity.summary_since', { since: fmt(conn.connectedAt, false), seen: summary.deliveriesSeen, empty: emptyDeliveries, content: summary.deliveriesWithContent }) }}
            {{ t('eu_data_act_sync.activity.sessions_imported', { n: summary.sessionsImported }) }}.
          </p>
          <p v-if="shortError" class="text-xs" data-testid="euda-last-error">
            {{ t('eu_data_act_sync.activity.last_error_short', { error: shortError }) }}
          </p>
        </div>
      </div>
    </div>

    <!-- 2. Fakten -->
    <dl class="text-sm divide-y divide-gray-100 dark:divide-gray-800">
      <div class="flex justify-between gap-4 py-1.5">
        <dt class="text-gray-500 dark:text-gray-400 shrink-0">{{ t('eu_data_act_sync.label_account') }}</dt>
        <dd class="text-gray-900 dark:text-gray-100 truncate text-right">{{ connection.email }}</dd>
      </div>
      <div class="flex justify-between gap-4 py-1.5">
        <dt class="text-gray-500 dark:text-gray-400 shrink-0">{{ t('eu_data_act_sync.activity.fact_last_data') }}</dt>
        <dd class="text-gray-900 dark:text-gray-100 text-right">{{ lastDataLabel }}</dd>
      </div>
      <div class="flex justify-between gap-4 py-1.5">
        <dt class="text-gray-500 dark:text-gray-400 shrink-0">{{ t('eu_data_act_sync.activity.fact_history') }}</dt>
        <dd class="text-gray-900 dark:text-gray-100 text-right" data-testid="euda-history-state">{{ historyLabel }}</dd>
      </div>
      <div v-if="trialEndsAt && status === 'ACTIVE'" class="flex justify-between gap-4 py-1.5" data-testid="euda-trial-hint">
        <dt class="text-gray-500 dark:text-gray-400 shrink-0">{{ t('eu_data_act_sync.activity.fact_access') }}</dt>
        <dd class="text-gray-900 dark:text-gray-100 text-right">{{ t('eu_data_act_sync.activity.access_trial', { date: trialEndsAt }) }}</dd>
      </div>
    </dl>
    <p v-if="trialEndsAt && status === 'ACTIVE'" class="text-xs text-gray-500 dark:text-gray-400 -mt-2">{{ t('eu_data_act_sync.activity.access_trial_note') }}</p>
    <p v-if="status === 'EXPIRED'" class="text-xs text-gray-500 dark:text-gray-400 -mt-2" data-testid="euda-expired-hint">{{ t('eu_data_act_sync.expired_hint') }}</p>
    <p v-else-if="status === 'AUTH_FAILED'" class="text-xs text-gray-500 dark:text-gray-400 -mt-2">{{ t('eu_data_act_sync.auth_failed_hint') }}</p>

    <!-- 3. Eine Handlung -->
    <div class="space-y-2">
      <router-link v-if="primary === 'upgrade'" to="/upgrade" data-testid="euda-upgrade" :class="[primaryClass, 'bg-amber-500 hover:bg-amber-400 text-gray-950 border-amber-500']">
        {{ t('eu_data_act_sync.teaser_cta') }}
        <ChevronRightIcon class="h-3.5 w-3.5" aria-hidden="true" />
      </router-link>
      <button v-else-if="primary === 'relogin'" type="button" :disabled="busy" data-testid="euda-relogin" :class="[primaryClass, 'bg-gray-950 dark:bg-white text-white dark:text-gray-950 border-gray-950 dark:border-white']" @click="emit('disconnect')">
        <LockClosedIcon class="h-4 w-4" aria-hidden="true" />
        {{ t('eu_data_act_sync.activity.btn_relogin') }}
      </button>
      <a v-else-if="primary === 'complaint' && complaint" :href="complaint.href" data-testid="euda-complaint" :class="[primaryClass, 'bg-gray-950 dark:bg-white text-white dark:text-gray-950 border-gray-950 dark:border-white']">
        <EnvelopeIcon class="h-4 w-4" aria-hidden="true" />
        {{ t('eu_data_act_sync.activity.complaint_btn') }}
      </a>
      <button v-else-if="primary === 'history'" type="button" :disabled="busy" data-testid="euda-history" :class="[primaryClass, 'bg-gray-950 dark:bg-white text-white dark:text-gray-950 border-gray-950 dark:border-white']" @click="emit('requestHistory')">
        <ArrowPathIcon class="h-4 w-4" aria-hidden="true" />
        {{ t('eu_data_act_sync.btn_history') }}
      </button>
      <p v-if="primary === 'complaint'" class="text-xs text-gray-500 dark:text-gray-400">{{ t('eu_data_act_sync.activity.complaint_note', { contact: activity?.manufacturerContact }) }}</p>

      <div class="flex flex-wrap gap-2">
        <button v-if="primary !== 'history' && historyOpen && status === 'ACTIVE'" type="button" :disabled="busy" data-testid="euda-history" :class="secondaryClass" @click="emit('requestHistory')">{{ t('eu_data_act_sync.btn_history') }}</button>
        <a v-if="primary !== 'complaint' && complaint && status === 'ACTIVE'" :href="complaint.href" data-testid="euda-complaint" :class="secondaryClass">{{ t('eu_data_act_sync.activity.complaint_btn') }}</a>
        <button v-if="manufacturerAtFault && status === 'ACTIVE'" type="button" data-testid="euda-authority" :class="secondaryClass" @click="authorityOpen = true">{{ t('eu_data_act_sync.activity.authority_btn') }}</button>
        <button v-if="status !== 'EXPIRED'" type="button" :disabled="busy" data-testid="euda-reactivate-smartcar" :class="secondaryClass" @click="emit('reactivateSmartcar')">{{ t('eu_data_act_sync.btn_reactivate_smartcar') }}</button>
        <button type="button" :disabled="busy" data-testid="euda-disconnect" :class="[secondaryClass, 'border-red-300 dark:border-red-800 text-red-700 dark:text-red-300 hover:border-red-500']" @click="emit('disconnect')">{{ t('eu_data_act_sync.btn_disconnect') }}</button>
      </div>
    </div>
    <EudaAuthorityComplaint v-if="authorityOpen && activity" :activity="activity" @close="authorityOpen = false" />

    <!-- 4. Details -->
    <div v-if="activity && conn">
      <button type="button" class="flex items-center gap-1 min-h-[44px] text-xs font-bold uppercase tracking-wider text-gray-600 dark:text-gray-300" @click="detailsOpen = !detailsOpen" data-testid="euda-activity-toggle">
        <component :is="detailsOpen ? ChevronDownIcon : ChevronRightIcon" class="h-3.5 w-3.5" aria-hidden="true" />
        {{ t('eu_data_act_sync.activity.details_toggle') }}
      </button>
      <div v-if="detailsOpen" class="space-y-4 text-xs pt-2">
        <div v-if="conn.lastError || conn.history?.error">
          <h4 class="font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1">{{ t('eu_data_act_sync.activity.errors_title') }}</h4>
          <p v-if="conn.lastError" class="font-mono text-red-700 dark:text-red-300 break-words">{{ t('eu_data_act_sync.activity.last_error', { n: conn.consecutiveFailures, date: fmt(conn.lastPolledAt) }) }} {{ conn.lastError }}</p>
          <p v-if="conn.history?.error" class="font-mono text-red-700 dark:text-red-300 break-words">{{ t('eu_data_act_sync.activity.history_label') }}: {{ conn.history.error }}</p>
        </div>
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
        <p class="text-gray-500 dark:text-gray-400">{{ t('eu_data_act_sync.history_hint') }}</p>
      </div>
    </div>
  </section>
</template>
