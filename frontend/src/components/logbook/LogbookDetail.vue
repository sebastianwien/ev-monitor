<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChartBarSquareIcon, LockClosedIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import LogEditPanel, { type EvLogResponse } from '../dashboard/LogEditPanel.vue'
import DayTripPanel from '../story/day/DayTripPanel.vue'
import PowerCurveModal from '../charging/PowerCurveModal.vue'
import DataFeedbackButtons from '../shared/DataFeedbackButtons.vue'
import DataFeedbackPanel from '../shared/DataFeedbackPanel.vue'
import { isRateableLogSource } from '../../composables/useDataFeedback'
import { useCarContext } from '../../composables/useCarContext'
import { usePowerCurve } from '../../composables/usePowerCurve'
import { useAuthStore } from '../../stores/auth'
import { useAnalyticsUpsellTarget } from '../../composables/useUpsellTarget'
import type { DayTrip } from '../../composables/storyDay'

/**
 * Der gewählte Eintrag des Logbuchs: Ladung bearbeiten (mit Ladekurve und "Korrekt erkannt?") oder Fahrt
 * bearbeiten. Unter 1024 px als Sheet von unten, ab 1024 px als klebende rechte Spalte neben dem Buch.
 */
export type LogbookSelection =
  | { kind: 'charge'; log: EvLogResponse & { hasPowerCurve?: boolean; hasSocCurve?: boolean } }
  | { kind: 'trip'; trip: DayTrip & { dataSource?: string | null } }

const props = defineProps<{ selection: LogbookSelection | null; carId: string; wide: boolean }>()
const emit = defineEmits<{ close: []; changed: [] }>()
const { t } = useI18n()
const { stats, formatLogDate } = useCarContext()
const curve = usePowerCurve(stats, formatLogDate)
const authStore = useAuthStore()
const upsellTarget = useAnalyticsUpsellTarget()

const sheet = ref<InstanceType<typeof BottomSheet> | null>(null)
/** Auf dem Handy erst das Sheet zufahren lassen, es meldet dann `close`. */
function dismiss() {
  if (props.wide) emit('close')
  else sheet.value?.requestClose()
}
function done() {
  emit('changed')
  dismiss()
}
const key = computed(() => props.selection?.kind === 'charge' ? props.selection.log.id : props.selection?.trip.id)
const tripRateable = computed(() => props.selection?.kind === 'trip' && props.selection.trip.dataSource !== 'USER_CREATED')
const stickyTop = { top: 'calc(var(--content-top, var(--top-nav-h)) + 1rem)' }
</script>

<template>
  <component :is="wide ? 'aside' : BottomSheet" v-if="wide || selection" ref="sheet"
    v-bind="wide
      ? { class: 'sticky flex max-h-[calc(100dvh-var(--content-top,var(--top-nav-h))-2rem)] min-h-[28rem] flex-col overflow-hidden rounded-3xl bg-white edge-box dark:bg-gray-900', style: stickyTop }
      : { edge: true, label: t('dashboard.edit_title'), panelClass: 'h-[88dvh] sm:max-w-xl' }"
    data-testid="logbook-detail" @close="emit('close')">
    <template v-if="selection">
      <LogEditPanel v-if="selection.kind === 'charge'" :key="key" :log="selection.log" dismiss="close"
        @cancel="dismiss" @saved="done" @deleted="done">
        <template #extra>
          <div v-if="curve.available(selection.log) || isRateableLogSource(selection.log.dataSource)"
            class="flex flex-wrap items-center gap-x-3 gap-y-1 text-[13px] text-gray-500 dark:text-gray-400">
            <button v-if="curve.available(selection.log)" type="button" data-testid="logbook-curve" aria-haspopup="dialog"
              :aria-label="curve.canOpen(selection.log) ? t('dashboard.show_power_curve') : t('dashboard.power_curve_locked')"
              class="inline-flex min-h-11 items-center gap-1.5 rounded-full font-semibold text-gray-800 hover:text-gray-900 focus-visible:outline-2 focus-visible:outline-green-600 dark:text-gray-200"
              @click="curve.open(selection.log)">
              <ChartBarSquareIcon class="h-4 w-4" aria-hidden="true" />{{ t('dashboard.power_curve_label') }}
              <LockClosedIcon v-if="!curve.canOpen(selection.log)" class="h-3 w-3" aria-hidden="true" />
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
    :upsell-target="upsellTarget"
    :log-id="curve.entry.value.id"
    @close="curve.entry.value = null"
  />
</template>
