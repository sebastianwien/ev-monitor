<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { LogFormData } from '../log-form/logFormData'
import { ClockIcon } from '@heroicons/vue/24/outline'
import { ROUTE_CHIPS, TIRE_CHIPS } from './optionalChips'
import { SEG_GROUP, SEG_LABEL as LABEL, segClass } from './segments'
import { useTimePick, nowLocal } from './useTimePick'

/**
 * "Mehr Details": die optionalen Angaben als Fakten-Zeilen mit Chips statt als Formular.
 * Jede Zeile ist eine Frage in Alltagssprache, Chips sind ganze Wörter, Zahlen bleiben
 * kompakte Felder mit Einheit. Vorbelegungen (Reifen, Strecke aus den letzten Logs) sind
 * als gewählter Chip sichtbar, nicht versteckt.
 */
const props = defineProps<{ showTime?: boolean }>()
const form = defineModel<LogFormData>({ required: true })
const { t } = useI18n()
const { timePick, pickTime, timeChips } = useTimePick(form)

const routeChips = ROUTE_CHIPS.map(c => ({ ...c, label: t(c.key) }))
const tireChips = TIRE_CHIPS.map(c => ({ ...c, label: t(c.key) }))

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
        <span :class="[LABEL, 'truncate !text-[10px] !tracking-tight']">{{ t('logwizard.d_soc_before_tile') }}</span>
        <span class="flex items-baseline gap-1">
          <input id="wizard-soc-before-detail" v-model.number="form.socBeforeChargePercent" type="number" inputmode="numeric" min="0" max="100" step="1" placeholder="–" :aria-label="t('logwizard.d_soc_before')" :class="numField" />
          <span class="text-xs text-gray-500 dark:text-gray-400">%</span>
        </span>
      </label>
      <label class="block rounded-sm border-2 border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-2.5 py-2 focus-within:border-indigo-600">
        <span :class="[LABEL, 'truncate !text-[10px] !tracking-tight']">{{ t('logwizard.d_duration') }}</span>
        <span class="flex items-baseline gap-1">
          <input id="wizard-duration" v-model.number="form.chargeDurationMinutes" type="number" inputmode="numeric" min="0" placeholder="–" :aria-label="t('logwizard.d_duration')" :class="numField" />
          <span class="text-xs text-gray-500 dark:text-gray-400">min</span>
        </span>
      </label>
      <label class="block rounded-sm border-2 border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-2.5 py-2 focus-within:border-indigo-600">
        <span :class="[LABEL, 'truncate !text-[10px] !tracking-tight']">{{ t('logwizard.d_peak_tile') }}</span>
        <span class="flex items-baseline gap-1">
          <input id="wizard-power" v-model.number="form.maxChargingPowerKw" type="number" inputmode="decimal" min="0" step="0.1" placeholder="–" :aria-label="t('logwizard.d_peak')" :class="numField" />
          <span class="text-xs text-gray-500 dark:text-gray-400">kW</span>
        </span>
      </label>
    </div>
  </div>
</template>
