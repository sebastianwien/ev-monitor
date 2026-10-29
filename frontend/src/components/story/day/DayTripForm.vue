<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRightIcon } from '@heroicons/vue/24/outline'
import ChoiceChip from '../../shared/ChoiceChip.vue'
import { joinTripTimes, splitTripTimes, LOAD_EXTRAS, toggleLoadExtra } from '../../../utils/tripForm'

/**
 * Fahrt-Formular in der App-CI (Tagesblatt, Log-Feed, Fahrt-Sheet). Modell aus useLogList:
 * Zeiten als datetime-local, leere Felder als ''.
 * Eine Karte mit Zeilen, die Werte stehen rechts und werden direkt bearbeitet. Ein Datum, zwei Uhrzeiten:
 * liegt das Ende vor dem Start, endet die Fahrt am nächsten Tag.
 * `title` setzt eine Überschrift, wenn das Formular nicht in einem Sheet mit eigenem Titel steht;
 * der Slot `extra` nimmt Zusätze vor den Knöpfen auf (Bewerten, Zusammenführen im Feed).
 * `actions: false`, wenn der Aufrufer die Knöpfe in seine Fußleiste stellt (Absenden per `form`-Attribut auf die id).
 * Gemessene Fahrten (`form.measured`) zeigen Zeit, Strecke und Akku nur an; Routentyp und Besonderheiten bleiben wählbar.
 */
withDefaults(defineProps<{ mode: 'add' | 'edit'; error?: string | null; saving?: boolean; distanceUnit: string; title?: string; actions?: boolean }>(), { actions: true })
const emit = defineEmits<{ save: []; cancel: [] }>()
const form = defineModel<Record<string, any>>({ required: true })
const { t, locale } = useI18n()

const ROUTES = ['CITY', 'COMBINED', 'HIGHWAY'] as const
const label = 'text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400'
const row = 'flex min-h-[52px] items-center gap-2 px-4 focus-within:ring-2 focus-within:ring-inset focus-within:ring-green-600'
const rowLabel = 'flex-1 text-[15px] text-gray-600 dark:text-gray-300'
// Das fokussierte Feld bekommt eine leichte Fläche, der Rand der Zeile allein zeigt bei zwei Feldern nicht, welches gemeint ist
const value = 'trip-value min-h-11 min-w-0 shrink-0 rounded-md bg-transparent px-1 focus:bg-green-50 dark:focus:bg-green-400/10 text-right text-[17px] font-semibold tabular-nums text-gray-900 outline-none dark:text-gray-50'
const unit = 'text-[15px] font-semibold text-gray-500 dark:text-gray-400'
const shown = 'shrink-0 px-1 text-right text-[17px] font-semibold tabular-nums text-gray-900 dark:text-gray-50'
/** SoC-Feld so breit wie sein Wert: Telemetrie liefert Nachkommastellen (78,76), Hand-Eingaben meist ganze Zahlen */
const socWidth = (v: unknown) => ({ width: `calc(${Math.max(2, String(v ?? '').length)}ch + 0.5rem)` })

// Datum lokal halten: eine geleerte Uhrzeit darf den Tag nicht mitnehmen
const times = computed(() => splitTripTimes(form.value as { tripStartedAt: string; tripEndedAt: string }))
const day = ref(times.value.date)
watch(() => form.value.tripStartedAt, v => { if (v) day.value = v.slice(0, 10) })
function setTimes(p: { date?: string; start?: string; end?: string }) {
  const n = { ...times.value, date: day.value, ...p }
  day.value = n.date
  // Ohne Datum nichts überschreiben, sonst gingen die Uhrzeiten mit
  if (n.date) Object.assign(form.value, joinTripTimes(n.date, n.start, n.end))
}
const date = computed({ get: () => day.value, set: v => setTimes({ date: v }) })
const start = computed({ get: () => times.value.start, set: v => setTimes({ start: v }) })
const end = computed({ get: () => times.value.end, set: v => setTimes({ end: v }) })

const measured = computed(() => form.value.measured === true)
const dateLabel = computed(() => {
  if (!day.value) return ''
  const [y, m, d] = day.value.split('-').map(Number)
  return new Intl.DateTimeFormat(locale.value, { weekday: 'short', day: 'numeric', month: 'long', year: 'numeric' }).format(new Date(y, m - 1, d))
})
const has = (v: unknown) => v !== '' && v != null
const socShown = (v: unknown) => Math.round(Number(v))
const distanceShown = computed(() => new Intl.NumberFormat(locale.value, { maximumFractionDigits: 1 }).format(Number(form.value.distanceKm)))
const extras = computed<string[]>(() => form.value.loadExtras ?? [])
function toggleExtra(x: string) { form.value.loadExtras = toggleLoadExtra(extras.value, x) }
</script>

<template>
  <form class="grid gap-4" data-testid="day-trip-form" @submit.prevent="emit('save')">
    <p v-if="title" class="text-[15px] font-bold text-gray-900 dark:text-gray-50">{{ title }}</p>
    <p v-if="error" role="alert" class="rounded-xl bg-amber-50 px-3 py-2 text-[14px] text-amber-900 dark:bg-amber-400/10 dark:text-amber-200">{{ error }}</p>
    <div class="divide-y-2 divide-gray-100 overflow-hidden rounded-2xl border-2 border-edge bg-white dark:divide-gray-700 dark:border-gray-700 dark:bg-gray-800">
      <template v-if="measured">
        <div :class="row"><span :class="rowLabel">{{ t('storyday.form_date') }}</span><span :class="shown">{{ dateLabel }}</span></div>
        <div :class="row"><span :class="rowLabel">{{ t('storyday.form_time') }}</span>
          <span :class="shown">{{ start }}</span>
          <ArrowRightIcon class="h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
          <span :class="shown">{{ end }}</span></div>
        <div v-if="has(form.distanceKm)" :class="row"><span :class="rowLabel">{{ t('storyday.form_distance_row') }}</span>
          <span :class="shown">{{ distanceShown }}</span><span :class="unit">{{ distanceUnit }}</span></div>
        <div v-if="has(form.socStart) || has(form.socEnd)" :class="row"><span :class="rowLabel">{{ t('storyday.form_soc') }}</span>
          <span :class="shown">{{ has(form.socStart) ? socShown(form.socStart) : '-' }}</span><span :class="unit">%</span>
          <ArrowRightIcon class="h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
          <span :class="shown">{{ has(form.socEnd) ? socShown(form.socEnd) : '-' }}</span><span :class="unit">%</span></div>
        <p data-testid="trip-measured-note" class="px-4 py-2.5 text-[13px] text-gray-500 dark:text-gray-400">{{ t('storyday.form_measured') }}</p>
      </template>
      <template v-else>
        <label :class="row"><span :class="rowLabel">{{ t('storyday.form_date') }}</span>
          <input v-model="date" type="date" data-testid="trip-date" :class="value" /></label>
        <div :class="row" role="group" :aria-label="t('storyday.form_time')">
          <span :class="rowLabel" aria-hidden="true">{{ t('storyday.form_time') }}</span>
          <input v-model="start" type="time" data-testid="trip-start" :aria-label="t('storyday.form_start')" :class="value" />
          <ArrowRightIcon class="h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
          <input v-model="end" type="time" data-testid="trip-end" :aria-label="t('storyday.form_end')" :class="value" />
        </div>
        <label :class="row"><span :class="rowLabel">{{ t('storyday.form_distance_row') }}</span>
          <input v-model="form.distanceKm" type="number" inputmode="decimal" min="0" max="9999" step="any" placeholder="0" data-testid="trip-distance" :class="[value, 'w-20']" />
          <span :class="unit">{{ distanceUnit }}</span></label>
        <div v-if="mode === 'edit'" :class="row" role="group" :aria-label="t('storyday.form_soc')">
          <span :class="rowLabel" aria-hidden="true">{{ t('storyday.form_soc') }}</span>
          <input v-model="form.socStart" type="number" inputmode="decimal" min="0" max="100" step="any" placeholder="-" :aria-label="t('storyday.form_soc_start')" :class="value" :style="socWidth(form.socStart)" />
          <span :class="unit">%</span>
          <ArrowRightIcon class="h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
          <input v-model="form.socEnd" type="number" inputmode="decimal" min="0" max="100" step="any" placeholder="-" :aria-label="t('storyday.form_soc_end')" :class="value" :style="socWidth(form.socEnd)" />
          <span :class="unit">%</span>
        </div>
      </template>
    </div>
    <div class="grid gap-1.5">
      <span :class="label">{{ t('storyday.form_route') }}</span>
      <div role="group" :aria-label="t('storyday.form_route')" class="flex flex-wrap gap-2">
        <ChoiceChip v-for="r in ROUTES" :key="r" size="md" :active="form.routeType === r" @click="form.routeType = form.routeType === r ? '' : r">
          {{ t(`dashboard.trip_route_${r.toLowerCase()}`) }}
        </ChoiceChip>
      </div>
    </div>
    <div class="grid gap-1.5">
      <span :class="label">{{ t('storyday.form_extras') }}</span>
      <div role="group" :aria-label="t('storyday.form_extras')" class="flex flex-wrap gap-2">
        <ChoiceChip v-for="x in LOAD_EXTRAS" :key="x" size="md" :active="extras.includes(x)" :data-testid="`trip-extra-${x}`" @click="toggleExtra(x)">
          {{ t(`storyday.form_extra_${x.toLowerCase()}`) }}
        </ChoiceChip>
      </div>
    </div>
    <slot name="extra" />
    <div v-if="actions" class="flex gap-2 pt-1">
      <button type="button" class="btn-3d cta-shadow-sm min-h-12 flex-1 rounded-xl border-2 border-edge bg-white px-3 text-[15px] font-semibold dark:bg-white/10" @click="emit('cancel')">
        {{ t('common.cancel') }}
      </button>
      <button type="submit" data-testid="day-trip-save" :disabled="saving"
        class="btn-3d cta-shadow-sm min-h-12 flex-1 rounded-xl border-2 border-edge bg-green-600 px-3 text-[15px] font-semibold text-white disabled:opacity-40">
        {{ t('logfields.save') }}
      </button>
    </div>
  </form>
</template>

<style scoped>
/* Werte rechtsbündig wie in einer Einstellungsliste, auch in den nativen Datums- und Zeitfeldern (iOS) */
.trip-value::-webkit-date-and-time-value { text-align: right; }
.trip-value::-webkit-inner-spin-button, .trip-value::-webkit-outer-spin-button { -webkit-appearance: none; margin: 0; }
.trip-value[type='number'] { -moz-appearance: textfield; appearance: textfield; }
.trip-value::placeholder { color: rgb(156 163 175); font-weight: 400; }
</style>
