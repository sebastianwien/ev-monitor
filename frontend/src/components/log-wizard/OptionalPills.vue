<script setup lang="ts">
import { computed, ref, watch, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { ClockIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import type { LogFormData } from '../log-form/logFormData'
import { ROUTE_CHIPS, TIRE_CHIPS } from './optionalChips'
import { SEG_GROUP, segClass } from './segments'
import { useTimePick, nowLocal } from './useTimePick'

/**
 * Zeit, Strecke und Reifen als drei Pillen in einer Zeile (Schritt 2). Tipp auf eine Pille verwandelt
 * die Zeile an Ort und Stelle in die Segmentgruppe (Pillen gleiten raus, Segmente rein); die Wahl
 * verwandelt sie zurück. Die Zeile wächst dabei nie, nichts verschiebt sich, nichts scrollt.
 * "Anders" bei der Zeit öffnet direkt den nativen Datum/Zeit-Picker statt einer eigenen Eingabezeile.
 */
type Field = 'time' | 'route' | 'tires'
const form = defineModel<LogFormData>({ required: true })
const { t, locale } = useI18n()
const { timePick, pickTime, timeChips } = useTimePick(form)
const editing = ref<Field | null>(null)
/** Bleibt beim Schließen stehen, damit der Editor während der Zuklapp-Animation noch Inhalt hat */
const shownEditor = ref<Field | null>(null)
watch(editing, f => { if (f) shownEditor.value = f })

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
/** Nativer Picker statt Eingabezeile: showPicker() braucht die Tap-Geste, deshalb direkt im Click-Handler */
const timeInput = ref<HTMLInputElement | null>(null)
/** Drei Zellen passen in die Zeile: "Gestern Abend" steckt im Picker, "Wählen" öffnet ihn */
const segTimeChips = computed(() => timeChips.filter(c => c.value !== 'yesterday').map(c => c.value === 'other' ? { ...c, label: t('logwizard.d_time_pick') } : c))
const onTime = (p: typeof timeChips[number]['value']) => {
  pickTime(p)
  if (p !== 'other') { editing.value = null; return }
  const el = timeInput.value; if (!el) return
  try { el.showPicker() } catch { el.focus(); el.click() }
}
const onTimePicked = () => { editing.value = null }
</script>

<template>
  <!-- Beide Lagen liegen in derselben Grid-Zelle: die Höhe ist die der Segmente (44 px), die Zeile wächst nie -->
  <div data-testid="optional-pills" class="grid overflow-hidden">
    <!-- Lage 1: drei Pillen, gleiten beim Öffnen nach links raus -->
    <div :inert="!!editing" :class="['[grid-area:1/1] flex items-center justify-center gap-1.5 min-h-[46px] transition-[transform,opacity] duration-200 ease-out motion-reduce:transition-none',
             editing ? '-translate-x-6 opacity-0' : 'translate-x-0 opacity-100']">
      <button v-for="p in pills" :key="p.field" type="button" :aria-label="p.label" :aria-expanded="editing === p.field"
        :data-testid="`pill-${p.field}`" @click="toggle(p.field)"
        class="inline-flex items-center gap-1 min-h-9 rounded-full px-3 text-xs tabular-nums transition bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-200 hover:bg-indigo-50 dark:hover:bg-indigo-900/30">
        <component :is="p.icon" class="h-3.5 w-3.5" />{{ p.text }}
      </button>
    </div>
    <!-- Lage 2: Segmentgruppe plus X, gleitet von rechts rein; bleibt beim Schließen gerendert (shownEditor), bis sie draußen ist -->
    <div :inert="!editing" :class="['[grid-area:1/1] flex items-center gap-1.5 transition-[transform,opacity] duration-200 ease-out motion-reduce:transition-none',
             editing ? 'translate-x-0 opacity-100' : 'translate-x-6 opacity-0']">
      <div v-if="shownEditor === 'time'" :class="[SEG_GROUP, 'flex-1 grid-cols-3']" role="radiogroup" :aria-label="t('logwizard.d_when')">
        <button v-for="c in segTimeChips" :key="c.value" type="button" role="radio" :aria-checked="timePick === c.value" :class="segClass(timePick === c.value)"
          :data-testid="`time-${c.value}`" @click="onTime(c.value)">{{ c.label }}</button>
      </div>
      <div v-else-if="shownEditor === 'route'" :class="[SEG_GROUP, 'flex-1 grid-cols-3']" role="radiogroup" :aria-label="t('logwizard.d_route')">
        <button v-for="c in ROUTE_CHIPS" :key="c.value" type="button" role="radio" :aria-checked="form.routeType === c.value" :class="segClass(form.routeType === c.value)"
          @click="pickRoute(c.value)"><component :is="c.icon" class="h-4 w-4 flex-shrink-0" />{{ t(c.key) }}</button>
      </div>
      <div v-else-if="shownEditor === 'tires'" :class="[SEG_GROUP, 'flex-1 grid-cols-3']" role="radiogroup" :aria-label="t('logwizard.d_tires')">
        <button v-for="c in TIRE_CHIPS" :key="c.value" type="button" role="radio" :aria-checked="form.tireType === c.value" :class="segClass(form.tireType === c.value)"
          @click="pickTires(c.value)"><component :is="c.icon" class="h-4 w-4 flex-shrink-0" />{{ t(c.key) }}</button>
      </div>
      <button type="button" :aria-label="t('common.close')" class="h-11 w-9 inline-flex items-center justify-center rounded-full text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700" @click="editing = null">
        <XMarkIcon class="h-4 w-4" />
      </button>
    </div>
    <!-- Unsichtbares Feld nur für den nativen Picker; kein display:none, sonst öffnet Safari nichts -->
    <input ref="timeInput" id="wizard-time" v-model="form.loggedAt" type="datetime-local" :max="nowLocal()" :aria-label="t('logfields.timestamp')"
      class="sr-only" tabindex="-1" @change="onTimePicked" />
  </div>
</template>
