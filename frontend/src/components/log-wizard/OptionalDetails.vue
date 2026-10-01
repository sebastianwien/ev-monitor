<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LogFormData } from '../log-form/logFormData'
import { ClockIcon, SunIcon } from '@heroicons/vue/24/outline'
import CityIcon from '../icons/CityIcon.vue'
import RouteIcon from '../icons/RouteIcon.vue'
import RoadIcon from '../icons/RoadIcon.vue'
import WheelIcon from '../icons/WheelIcon.vue'
import SnowflakeIcon from '../icons/SnowflakeIcon.vue'

/**
 * "Mehr Details": die optionalen Angaben als Fakten-Zeilen mit Chips statt als Formular.
 * Jede Zeile ist eine Frage in Alltagssprache, Chips sind ganze Wörter, Zahlen bleiben
 * kompakte Felder mit Einheit. Vorbelegungen (Reifen, Strecke aus den letzten Logs) sind
 * als gewählter Chip sichtbar, nicht versteckt.
 */
const props = defineProps<{ showTime?: boolean }>()
const form = defineModel<LogFormData>({ required: true })
const { t } = useI18n()

const pad = (n: number) => String(n).padStart(2, '0')
const toLocal = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
const nowLocal = () => toLocal(new Date())
const oneHourAgo = () => toLocal(new Date(Date.now() - 3_600_000))
const yesterdayEvening = () => { const d = new Date(); d.setDate(d.getDate() - 1); d.setHours(20, 0, 0, 0); return toLocal(d) }

type TimePick = 'now' | 'hour' | 'yesterday' | 'other'
const timeOther = ref(false)
const timePick = computed<TimePick>(() => {
  if (timeOther.value) return 'other'
  if (!form.value.loggedAt) return 'now'
  if (form.value.loggedAt === oneHourAgo()) return 'hour'
  if (form.value.loggedAt === yesterdayEvening()) return 'yesterday'
  return 'other'
})
const pickTime = (p: TimePick) => {
  timeOther.value = p === 'other'
  if (p === 'now') form.value.loggedAt = null
  else if (p === 'hour') form.value.loggedAt = oneHourAgo()
  else if (p === 'yesterday') form.value.loggedAt = yesterdayEvening()
  else if (!form.value.loggedAt) form.value.loggedAt = nowLocal()
}
const timeChips: { value: TimePick; label: string }[] = [
  { value: 'now', label: t('logfields.timestamp_chip_now') },
  { value: 'hour', label: t('logfields.timestamp_chip_1h_ago') },
  { value: 'yesterday', label: t('logfields.timestamp_chip_yesterday_evening') },
  { value: 'other', label: t('logwizard.d_time_other') },
]
const routeChips = [
  { value: 'CITY', label: t('logwizard.d_route_city'), icon: CityIcon },
  { value: 'COMBINED', label: t('logwizard.d_route_mixed'), icon: RouteIcon },
  { value: 'HIGHWAY', label: t('logwizard.d_route_highway'), icon: RoadIcon },
] as const
const tireChips = [
  { value: 'SUMMER', label: t('logwizard.d_tire_summer'), icon: SunIcon },
  { value: 'ALL_YEAR', label: t('logwizard.d_tire_allyear'), icon: WheelIcon },
  { value: 'WINTER', label: t('logwizard.d_tire_winter'), icon: SnowflakeIcon },
] as const

// Segmente statt Pillen: gleich breite Zellen füllen die Zeile auch auf 360 px, jede Zelle 44 px hoch.
const segClass = (on: boolean) => [
  'min-h-11 inline-flex items-center justify-center gap-1.5 px-2 text-sm transition',
  on ? 'bg-indigo-600 text-white' : 'bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-200 hover:bg-indigo-50 dark:hover:bg-indigo-900/30',
]
const SEG_GROUP = 'grid gap-px overflow-hidden rounded-sm border border-gray-300 dark:border-gray-600 bg-gray-300 dark:bg-gray-600'
const LABEL = 'block text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500'

const numField = 'w-full min-w-0 bg-transparent border-0 p-0 text-right text-xl font-medium tabular-nums text-gray-900 dark:text-gray-100 placeholder:text-gray-300 dark:placeholder:text-gray-600 focus:ring-0 focus:outline-none [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none'
</script>

<template>
  <!-- Drei Blöcke: Zeit, zwei Segmentreihen, drei Zahlenkacheln. Label klein über jedem Block wie in der Zusammenfassung. -->
  <div class="pt-1 pb-2 space-y-4">
    <div v-if="showTime" class="space-y-1.5">
      <span :class="LABEL">{{ t('logwizard.d_when') }}</span>
      <div :class="[SEG_GROUP, 'grid-cols-2']" role="radiogroup" :aria-label="t('logwizard.d_when')">
        <button v-for="c in timeChips" :key="c.value" type="button" role="radio" :aria-checked="timePick === c.value" :class="segClass(timePick === c.value)"
          :data-testid="`time-${c.value}`" @click="pickTime(c.value)">
          <ClockIcon v-if="c.value === 'other'" class="h-4 w-4 flex-shrink-0" />{{ c.label }}
        </button>
      </div>
      <input v-if="timePick === 'other'" id="wizard-time" v-model="form.loggedAt" type="datetime-local" :max="nowLocal()" :aria-label="t('logfields.timestamp')"
        class="w-full rounded-sm border border-gray-300 dark:border-gray-600 bg-transparent dark:text-gray-100 p-2 text-sm focus:border-indigo-600 focus:ring-0 focus:outline-none" />
    </div>

    <div class="space-y-1.5">
      <span :class="LABEL">{{ t('logwizard.d_route') }}</span>
      <div :class="[SEG_GROUP, 'grid-cols-3']" role="radiogroup" :aria-label="t('logwizard.d_route')">
        <button v-for="c in routeChips" :key="c.value" type="button" role="radio" :aria-checked="form.routeType === c.value" :class="segClass(form.routeType === c.value)"
          @click="form.routeType = c.value"><component :is="c.icon" class="h-4 w-4 flex-shrink-0" />{{ c.label }}</button>
      </div>
    </div>

    <div class="space-y-1.5">
      <span :class="LABEL">{{ t('logwizard.d_tires') }}</span>
      <div :class="[SEG_GROUP, 'grid-cols-3']" role="radiogroup" :aria-label="t('logwizard.d_tires')">
        <button v-for="c in tireChips" :key="c.value" type="button" role="radio" :aria-checked="form.tireType === c.value" :class="segClass(form.tireType === c.value)"
          @click="form.tireType = c.value"><component :is="c.icon" class="h-4 w-4 flex-shrink-0" />{{ c.label }}</button>
      </div>
    </div>

    <!-- Zahlen nebeneinander: Label oben, Wert groß, Einheit daneben - dieselbe Sprache wie die Rädchen-Zeilen -->
    <div class="grid grid-cols-3 gap-2">
      <label class="block rounded-sm border-2 border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-2.5 py-2 focus-within:border-indigo-600">
        <span :class="[LABEL, 'truncate']">{{ t('logwizard.d_soc_before_tile') }}</span>
        <span class="flex items-baseline gap-1">
          <input id="wizard-soc-before-detail" v-model.number="form.socBeforeChargePercent" type="number" inputmode="numeric" min="0" max="100" step="1" placeholder="–" :aria-label="t('logwizard.d_soc_before')" :class="numField" />
          <span class="text-xs text-gray-500 dark:text-gray-400">%</span>
        </span>
      </label>
      <label class="block rounded-sm border-2 border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-2.5 py-2 focus-within:border-indigo-600">
        <span :class="[LABEL, 'truncate']">{{ t('logwizard.d_duration') }}</span>
        <span class="flex items-baseline gap-1">
          <input id="wizard-duration" v-model.number="form.chargeDurationMinutes" type="number" inputmode="numeric" min="0" placeholder="–" :aria-label="t('logwizard.d_duration')" :class="numField" />
          <span class="text-xs text-gray-500 dark:text-gray-400">min</span>
        </span>
      </label>
      <label class="block rounded-sm border-2 border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-2.5 py-2 focus-within:border-indigo-600">
        <span :class="[LABEL, 'truncate']">{{ t('logwizard.d_peak_tile') }}</span>
        <span class="flex items-baseline gap-1">
          <input id="wizard-power" v-model.number="form.maxChargingPowerKw" type="number" inputmode="decimal" min="0" step="0.1" placeholder="–" :aria-label="t('logwizard.d_peak')" :class="numField" />
          <span class="text-xs text-gray-500 dark:text-gray-400">kW</span>
        </span>
      </label>
    </div>
  </div>
</template>
