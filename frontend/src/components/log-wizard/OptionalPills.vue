<script setup lang="ts">
import { computed, ref, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { ClockIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import type { LogFormData } from '../log-form/logFormData'
import { ROUTE_CHIPS, TIRE_CHIPS } from './optionalChips'
import { SEG_GROUP, segClass } from './segments'
import { useTimePick, nowLocal } from './useTimePick'

/**
 * Zeit, Strecke und Reifen als drei Pillen in einer Zeile (Schritt 2). Tipp auf eine Pille klappt
 * an ihrer Stelle die Segmentgruppe auf; die Wahl schließt sie wieder. Kein Formular, kein Modal.
 */
type Field = 'time' | 'route' | 'tires'
const form = defineModel<LogFormData>({ required: true })
const { t, locale } = useI18n()
const { timePick, pickTime, timeChips } = useTimePick(form)
const editing = ref<Field | null>(null)

const timeLabel = computed(() => {
  if (!form.value.loggedAt) return t('logwizard.time_now')
  return new Date(form.value.loggedAt).toLocaleString(locale.value === 'en' ? 'en-GB' : 'de-DE', { day: 'numeric', month: 'numeric', hour: '2-digit', minute: '2-digit' })
})
interface Pill { field: Field; text: string; icon: Component; label: string }
const pills = computed<Pill[]>(() => {
  const route = ROUTE_CHIPS.find(c => c.value === form.value.routeType)!
  const tires = TIRE_CHIPS.find(c => c.value === form.value.tireType)!
  return [
    { field: 'time', text: timeLabel.value, icon: ClockIcon, label: t('logwizard.d_when') },
    { field: 'route', text: t(route.key), icon: route.icon, label: t('logwizard.d_route') },
    { field: 'tires', text: t(tires.key), icon: tires.icon, label: t('logwizard.d_tires') },
  ]
})
const toggle = (f: Field) => { editing.value = editing.value === f ? null : f }
const pickRoute = (v: LogFormData['routeType']) => { form.value.routeType = v; editing.value = null }
const pickTires = (v: LogFormData['tireType']) => { form.value.tireType = v; editing.value = null }
const onTime = (p: typeof timeChips[number]['value']) => { pickTime(p); if (p !== 'other') editing.value = null }
const active = computed(() => pills.value.find(p => p.field === editing.value) ?? null)
</script>

<template>
  <div data-testid="optional-pills" class="space-y-2">
    <!-- Zeile: drei Pillen, die gerade bearbeitete hervorgehoben; aufgeklappt bleibt nur sie stehen, rechts ein X zum Schließen -->
    <div class="flex items-center gap-1.5">
      <button v-for="p in pills" v-show="!editing || editing === p.field" :key="p.field" type="button" :aria-label="p.label" :aria-expanded="editing === p.field"
        :data-testid="`pill-${p.field}`" @click="toggle(p.field)"
        :class="['inline-flex items-center gap-1 min-h-9 rounded-full px-3 text-xs tabular-nums transition',
                 editing === p.field ? 'bg-indigo-600 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-200 hover:bg-indigo-50 dark:hover:bg-indigo-900/30']">
        <component :is="p.icon" class="h-3.5 w-3.5" />{{ p.text }}
      </button>
      <span v-if="active" class="flex-1 text-xs text-gray-400 truncate">{{ active.label }}</span>
      <button v-if="editing" type="button" :aria-label="t('common.close')" class="ml-auto h-9 w-9 inline-flex items-center justify-center rounded-full text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700" @click="editing = null">
        <XMarkIcon class="h-4 w-4" />
      </button>
    </div>

    <div v-if="editing === 'time'" class="space-y-1.5">
      <div :class="[SEG_GROUP, 'grid-cols-2']" role="radiogroup" :aria-label="t('logwizard.d_when')">
        <button v-for="c in timeChips" :key="c.value" type="button" role="radio" :aria-checked="timePick === c.value" :class="segClass(timePick === c.value)"
          :data-testid="`time-${c.value}`" @click="onTime(c.value)">{{ c.label }}</button>
      </div>
      <input v-if="timePick === 'other'" id="wizard-time" v-model="form.loggedAt" type="datetime-local" :max="nowLocal()" :aria-label="t('logfields.timestamp')"
        class="w-full rounded-sm border border-gray-300 dark:border-gray-600 bg-transparent dark:text-gray-100 p-2 text-sm focus:border-indigo-600 focus:ring-0 focus:outline-none" />
    </div>
    <div v-else-if="editing === 'route'" :class="[SEG_GROUP, 'grid-cols-3']" role="radiogroup" :aria-label="t('logwizard.d_route')">
      <button v-for="c in ROUTE_CHIPS" :key="c.value" type="button" role="radio" :aria-checked="form.routeType === c.value" :class="segClass(form.routeType === c.value)"
        @click="pickRoute(c.value)"><component :is="c.icon" class="h-4 w-4 flex-shrink-0" />{{ t(c.key) }}</button>
    </div>
    <div v-else-if="editing === 'tires'" :class="[SEG_GROUP, 'grid-cols-3']" role="radiogroup" :aria-label="t('logwizard.d_tires')">
      <button v-for="c in TIRE_CHIPS" :key="c.value" type="button" role="radio" :aria-checked="form.tireType === c.value" :class="segClass(form.tireType === c.value)"
        @click="pickTires(c.value)"><component :is="c.icon" class="h-4 w-4 flex-shrink-0" />{{ t(c.key) }}</button>
    </div>
  </div>
</template>
