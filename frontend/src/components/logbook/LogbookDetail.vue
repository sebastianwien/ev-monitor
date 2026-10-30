<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChartBarSquareIcon, LockClosedIcon, ArrowsRightLeftIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import LogEditPanel, { type EvLogResponse } from '../dashboard/LogEditPanel.vue'
import DayTripPanel from '../story/day/DayTripPanel.vue'
import LogbookBundle from './LogbookBundle.vue'
import PowerCurveModal from '../charging/PowerCurveModal.vue'
import DataFeedbackButtons from '../shared/DataFeedbackButtons.vue'
import DataFeedbackPanel from '../shared/DataFeedbackPanel.vue'
import { isRateableLogSource } from '../../composables/useDataFeedback'
import { useCarContext } from '../../composables/useCarContext'
import { usePowerCurve } from '../../composables/usePowerCurve'
import { useAuthStore } from '../../stores/auth'
import { useAnalyticsUpsellLink } from '../../composables/useUpsellTarget'
import type { DayLog, DayTrip } from '../../composables/storyDay'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { tripDetailKeys, chargeDetailKeys } from '../../utils/logbook'
import type { TripFacts } from '../../utils/tripCalculations'

/**
 * Der gewählte Eintrag des Logbuchs: Ladung bearbeiten (mit Ladekurve und "Korrekt erkannt?"), Fahrt
 * bearbeiten oder ein Bündel aus Teilladungen. Aus dem Bündel geöffnet, führt der Pfeil einer Teilladung zurück
 * zum Bündel (`bundleId`). Unter 1024 px als Sheet von unten, ab 1024 px als klebende rechte Spalte neben dem Buch.
 */
export type LogbookSelection =
  | { kind: 'charge'; log: EvLogResponse & { hasPowerCurve?: boolean; hasSocCurve?: boolean }; bundleId?: string }
  | { kind: 'trip'; trip: DayTrip & { dataSource?: string | null } }
  | { kind: 'bundle'; id: string; parts: DayLog[] }

const props = defineProps<{
  selection: LogbookSelection | null
  carId: string
  wide: boolean
  /** Kosten und Verbrauch der gewählten Fahrt, wie in ihrer Zeile */
  tripFacts?: TripFacts | null
  /** Mehrere Autos: Ladung oder Bündel lässt sich umbuchen */
  canReassign?: boolean
}>()
/** `back`: von der Teilladung zurück zum Bündel; `open`: Teilladung aus dem Bündel öffnen */
const emit = defineEmits<{
  close: []; changed: []; back: []; open: [selection: LogbookSelection]; amend: [log: DayLog]; reassign: []
}>()
const { t } = useI18n()
const { stats, formatLogDate } = useCarContext()
const curve = usePowerCurve(stats, formatLogDate)
const authStore = useAuthStore()
const upsellTarget = useAnalyticsUpsellLink('logbook_curve')

const sheet = ref<InstanceType<typeof BottomSheet> | null>(null)
/** Auf dem Handy erst das Sheet zufahren lassen, es meldet dann `close`. */
function dismiss() {
  if (props.wide) emit('close')
  else sheet.value?.requestClose()
}
/** Gespeichert oder gelöscht: zurück zum Bündel, aus dem die Teilladung kam, sonst schließen */
function done() {
  emit('changed')
  if (props.selection?.kind === 'charge' && props.selection.bundleId) emit('back')
  else dismiss()
}
function openPart(part: DayLog) {
  if (props.selection?.kind === 'bundle') emit('open', { kind: 'charge', log: part as EvLogResponse, bundleId: props.selection.id })
}
const key = computed(() => {
  const s = props.selection
  return s?.kind === 'charge' ? s.log.id : s?.kind === 'bundle' ? `b-${s.id}` : s?.trip.id
})
const tripRateable = computed(() => props.selection?.kind === 'trip' && props.selection.trip.dataSource !== 'USER_CREATED')
const stickyTop = { top: 'calc(var(--content-top, var(--top-nav-h)) + 1rem)' }
const { formatConsumption } = useLocaleFormat()
/** Tempo und Verbrauch der Fahrt, nur auf dem Handy (ab 768 px stehen sie in der Zeile); Streckentyp und Besonderheiten zeigt das Formular als Chips */
const speed = computed(() => {
  if (props.selection?.kind !== 'trip') return null
  const k = tripDetailKeys(props.selection.trip).find(x => x.key.startsWith('dashboard.trip_speed'))
  const f = props.tripFacts
  return [k ? t(k.key, k.params ?? {}) : null, f?.kwhPer100km != null ? `${f.estimated ? '~' : ''}${formatConsumption(f.kwhPer100km)}` : null]
    .filter(Boolean).join(' · ') || null
})
/** Höchste Ladeleistung und Effizienz einer Ladung, nur auf dem Handy (ab 768 px in der Zeile) */
const chargeMore = computed(() => props.selection?.kind === 'charge'
  ? chargeDetailKeys(props.selection.log).map(k => t(k.key, k.params ?? {})).join(' · ') || null
  : null)
</script>

<template>
  <component :is="wide ? 'aside' : BottomSheet" v-if="wide || selection" ref="sheet"
    v-bind="wide
      ? { class: 'sticky flex max-h-[calc(100dvh-var(--content-top,var(--top-nav-h))-2rem)] min-h-[28rem] flex-col overflow-hidden rounded-3xl bg-white edge-box dark:bg-gray-900', style: stickyTop }
      : { edge: true, label: selection?.kind === 'bundle' ? t('storyday.parts_title') : t('dashboard.edit_title'), panelClass: 'h-[88dvh] sm:max-w-xl', testid: 'logbook-detail' }"
    data-testid="logbook-detail" @close="emit('close')">
    <template v-if="selection">
      <LogbookBundle v-if="selection.kind === 'bundle'" :key="key" :parts="selection.parts" can-amend :can-reassign="canReassign"
        @cancel="dismiss" @amend="log => emit('amend', log)" @reassign="emit('reassign')"
        @select="openPart" />
      <LogEditPanel v-else-if="selection.kind === 'charge'" :key="key" :log="selection.log" :dismiss="selection.bundleId ? 'back' : 'close'"
        @cancel="selection.bundleId ? emit('back') : dismiss()" @saved="done" @deleted="done">
        <template #extra>
          <p v-if="chargeMore" class="text-[13px] tabular-nums md:hidden text-gray-600 dark:text-gray-300" data-testid="logbook-charge-more">{{ chargeMore }}</p>
          <div v-if="curve.available(selection.log) || isRateableLogSource(selection.log.dataSource) || canReassign"
            class="flex flex-wrap items-center gap-x-3 gap-y-1 text-[13px] text-gray-500 dark:text-gray-400">
            <button v-if="curve.available(selection.log)" type="button" data-testid="logbook-curve" aria-haspopup="dialog"
              :aria-label="curve.canOpen(selection.log) ? t('dashboard.show_power_curve') : t('dashboard.power_curve_locked')"
              class="inline-flex min-h-11 items-center gap-1.5 rounded-full font-semibold text-gray-800 hover:text-gray-900 focus-visible:outline-2 focus-visible:outline-green-600 dark:text-gray-200"
              @click="curve.open(selection.log)">
              <ChartBarSquareIcon class="h-4 w-4" aria-hidden="true" />{{ t('dashboard.power_curve_label') }}
              <LockClosedIcon v-if="!curve.canOpen(selection.log)" class="h-3 w-3" aria-hidden="true" />
            </button>
            <button v-if="canReassign" type="button" data-testid="logbook-reassign" @click="emit('reassign')"
              class="inline-flex min-h-11 items-center gap-1.5 rounded-full font-semibold text-gray-800 hover:text-gray-900 focus-visible:outline-2 focus-visible:outline-green-600 dark:text-gray-200">
              <ArrowsRightLeftIcon class="h-4 w-4" aria-hidden="true" />{{ t('dashboard.action_reassign') }}
            </button>
            <span v-if="isRateableLogSource(selection.log.dataSource)" class="ml-auto inline-flex items-center gap-0.5">
              <span class="mr-1">{{ t('dashboard.data_feedback_label') }}</span>
              <DataFeedbackButtons subject-type="LOG" :subject-id="selection.log.id" />
            </span>
          </div>
          <DataFeedbackPanel v-if="isRateableLogSource(selection.log.dataSource)" subject-type="LOG" :subject-id="selection.log.id" />
        </template>
      </LogEditPanel>
      <DayTripPanel v-else :key="key" :trip="selection.trip" :car-id="carId" :new-end="new Date()"
        @back="dismiss" @saved="done" @deleted="done">
        <template #facts>
          <p v-if="speed" class="mb-3 text-[13px] tabular-nums md:hidden text-gray-600 dark:text-gray-300" data-testid="logbook-trip-speed">{{ speed }}</p>
        </template>
        <template #extras-end>
          <span v-if="tripRateable" role="group" :aria-label="t('dashboard.data_feedback_label')"
            class="ml-auto inline-flex items-center gap-0.5 self-center text-[13px] text-gray-500 dark:text-gray-400">
            <span class="mr-1">{{ t('dashboard.data_feedback_label') }}</span>
            <DataFeedbackButtons subject-type="TRIP" :subject-id="selection.trip.id" />
          </span>
        </template>
        <template #extra>
          <DataFeedbackPanel v-if="tripRateable" subject-type="TRIP" :subject-id="selection.trip.id" />
        </template>
      </DayTripPanel>
    </template>
    <p v-else class="m-auto px-6 text-center text-[14px] text-gray-500 dark:text-gray-400">{{ t('storyday.pick') }}</p>
  </component>

  <PowerCurveModal
    v-if="curve.entry.value"
    :loading="curve.loading.value.has(curve.entry.value.id)"
    :points="curve.cache.value.get(curve.entry.value.id)?.points ?? []"
    :soc-points="curve.cache.value.get(curve.entry.value.id)?.socPoints ?? []"
    :consumption-kwh-per100km="curve.consumption(curve.entry.value)"
    :subtitle="curve.subtitle.value"
    :soc-before-charge-percent="curve.entry.value.socBeforeChargePercent"
    :soc-after-charge-percent="curve.entry.value.socAfterChargePercent"
    :kwh-charged="curve.entry.value.kwhAtVehicle ?? curve.entry.value.kwhCharged"
    :charge-duration-minutes="curve.entry.value.chargeDurationMinutes"
    :locked="!authStore.canViewLiveAnalytics"
    :upsell-target="upsellTarget ?? undefined"
    :log-id="curve.entry.value.id"
    @close="curve.entry.value = null"
  />
</template>
