<script setup lang="ts">
import { CHIP_ROW, chipClass } from './chipClass'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LogFormData } from '../log-form/logFormData'

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
  { value: 'CITY', label: t('logwizard.d_route_city') },
  { value: 'COMBINED', label: t('logwizard.d_route_mixed') },
  { value: 'HIGHWAY', label: t('logwizard.d_route_highway') },
] as const
const tireChips = [
  { value: 'SUMMER', label: t('logwizard.d_tire_summer') },
  { value: 'ALL_YEAR', label: t('logwizard.d_tire_allyear') },
  { value: 'WINTER', label: t('logwizard.d_tire_winter') },
] as const

const numField = 'w-20 rounded-sm border border-gray-300 dark:border-gray-600 bg-transparent dark:text-gray-100 px-2 py-1.5 text-sm text-right tabular-nums focus:border-indigo-600 focus:ring-0 focus:outline-none [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none'
</script>

<template>
  <dl class="divide-y divide-gray-100 dark:divide-gray-700">
    <!-- Wann -->
    <div v-if="showTime" class="py-3 space-y-2">
      <dt class="text-sm text-gray-500 dark:text-gray-400">{{ t('logwizard.d_when') }}</dt>
      <dd :class="CHIP_ROW">
        <button v-for="c in timeChips" :key="c.value" type="button" :aria-pressed="timePick === c.value" :class="chipClass(timePick === c.value)"
          :data-testid="`time-${c.value}`" @click="pickTime(c.value)">{{ c.label }}</button>
      </dd>
      <dd v-if="timePick === 'other'">
        <input id="wizard-time" v-model="form.loggedAt" type="datetime-local" :max="nowLocal()" :aria-label="t('logfields.timestamp')"
          class="w-full rounded-sm border border-gray-300 dark:border-gray-600 bg-transparent dark:text-gray-100 p-2 text-sm focus:border-indigo-600 focus:ring-0 focus:outline-none" />
      </dd>
    </div>

    <!-- Akku vorher -->
    <div class="py-3 flex items-center justify-between gap-3">
      <dt class="text-sm text-gray-500 dark:text-gray-400">{{ t('logwizard.d_soc_before') }}</dt>
      <dd class="flex items-center gap-1.5">
        <input id="wizard-soc-before-detail" v-model.number="form.socBeforeChargePercent" type="number" inputmode="numeric" min="0" max="100" step="1"
          placeholder="–" :aria-label="t('logwizard.d_soc_before')" :class="numField" />
        <span class="text-sm text-gray-500 dark:text-gray-400">%</span>
      </dd>
    </div>

    <!-- Strecke seit der letzten Ladung -->
    <div class="py-3 space-y-2">
      <dt class="text-sm text-gray-500 dark:text-gray-400">{{ t('logwizard.d_route') }}</dt>
      <dd :class="CHIP_ROW">
        <button v-for="c in routeChips" :key="c.value" type="button" :aria-pressed="form.routeType === c.value" :class="chipClass(form.routeType === c.value)"
          @click="form.routeType = c.value">{{ c.label }}</button>
      </dd>
    </div>

    <!-- Reifen -->
    <div class="py-3 space-y-2">
      <dt class="text-sm text-gray-500 dark:text-gray-400">{{ t('logwizard.d_tires') }}</dt>
      <dd :class="CHIP_ROW">
        <button v-for="c in tireChips" :key="c.value" type="button" :aria-pressed="form.tireType === c.value" :class="chipClass(form.tireType === c.value)"
          @click="form.tireType = c.value">{{ c.label }}</button>
      </dd>
    </div>

    <!-- Dauer und Spitzenleistung -->
    <div class="py-3 flex items-center justify-between gap-3">
      <dt class="text-sm text-gray-500 dark:text-gray-400">{{ t('logwizard.d_duration') }}</dt>
      <dd class="flex items-center gap-1.5">
        <input id="wizard-duration" v-model.number="form.chargeDurationMinutes" type="number" inputmode="numeric" min="0"
          placeholder="–" :aria-label="t('logwizard.d_duration')" :class="numField" />
        <span class="text-sm text-gray-500 dark:text-gray-400">min</span>
      </dd>
    </div>
    <div class="py-3 flex items-center justify-between gap-3">
      <dt class="text-sm text-gray-500 dark:text-gray-400">{{ t('logwizard.d_peak') }}</dt>
      <dd class="flex items-center gap-1.5">
        <input id="wizard-power" v-model.number="form.maxChargingPowerKw" type="number" inputmode="decimal" min="0" step="0.1"
          placeholder="–" :aria-label="t('logwizard.d_peak')" :class="numField" />
        <span class="text-sm text-gray-500 dark:text-gray-400">kW</span>
      </dd>
    </div>
  </dl>
</template>
