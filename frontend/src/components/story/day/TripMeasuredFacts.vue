<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ClockIcon } from '@heroicons/vue/24/outline'
import { splitTripTimes } from '../../../utils/tripForm'

/**
 * Was das Auto bei einer Fahrt gemessen hat, ohne Tabelle: nichts davon ist änderbar.
 * `time`: Tag und Uhrzeit als Zeile (auf der Karte im Verlauf, wie bei der Ladung).
 * `stats`: Strecke, Dauer und Akku als Kennzahlen-Reihe ohne Rahmen, so groß wie die Werte der Ladekacheln.
 */
const props = withDefaults(defineProps<{ form: Record<string, any>; distanceUnit: string; time?: boolean; stats?: boolean }>(), { time: true, stats: true })
const { t, locale } = useI18n()

const has = (v: unknown) => v !== '' && v != null
const timeLabel = computed(() => {
  const { date, start, end } = splitTripTimes(props.form as { tripStartedAt: string; tripEndedAt: string })
  if (!date) return ''
  const [y, m, d] = date.split('-').map(Number)
  const day = new Intl.DateTimeFormat(locale.value, { weekday: 'short', day: 'numeric', month: 'numeric' }).format(new Date(y, m - 1, d))
  return [day, [start, end].filter(Boolean).join('-')].filter(Boolean).join(' · ')
})

/** Dauer wie im Tagesblatt ("1 h 5 min"); ohne beide Zeiten oder bei unsinniger Spanne keine Spalte */
const duration = computed(() => {
  const a = Date.parse(props.form.tripStartedAt), b = Date.parse(props.form.tripEndedAt)
  if (Number.isNaN(a) || Number.isNaN(b) || b <= a) return null
  const min = Math.round((b - a) / 60000), h = Math.floor(min / 60), m = min % 60
  return h && m ? t('storyday.dur_hm', { h, m }) : h ? t('storyday.dur_h', { h }) : t('storyday.dur_m', { m })
})

const cols = computed(() => {
  const f = props.form
  const out: { label: string; value: string }[] = []
  if (has(f.distanceKm)) out.push({ label: t('storyday.form_distance_row'), value: `${new Intl.NumberFormat(locale.value, { maximumFractionDigits: 1 }).format(Number(f.distanceKm))} ${props.distanceUnit}` })
  if (duration.value) out.push({ label: t('storyday.form_duration'), value: duration.value })
  if (has(f.socStart) || has(f.socEnd)) {
    const soc = (v: unknown) => has(v) ? String(Math.round(Number(v))) : '-'
    out.push({ label: t('storyday.form_soc'), value: `${soc(f.socStart)} → ${soc(f.socEnd)} %` })
  }
  return out
})
</script>

<template>
  <p v-if="time && timeLabel" data-testid="trip-measured-time"
    class="flex items-center gap-1.5 text-[13px] font-semibold tabular-nums text-gray-700 dark:text-gray-200">
    <ClockIcon class="h-4 w-4 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />{{ timeLabel }}
  </p>
  <dl v-if="stats && cols.length" data-testid="trip-measured-stats" class="grid grid-flow-col auto-cols-fr gap-3">
    <div v-for="c in cols" :key="c.label" class="min-w-0">
      <dt class="text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ c.label }}</dt>
      <dd class="whitespace-nowrap text-[17px] font-bold tabular-nums text-gray-900 dark:text-gray-50">{{ c.value }}</dd>
    </div>
  </dl>
</template>
