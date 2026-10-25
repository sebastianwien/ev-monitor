<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { TripMonth } from '../../../composables/storyInsights'

/**
 * Fahrten des Monats im Detail: Fahrten, Strecke, längste Fahrt, Temperatur und der Abgleich
 * Verbrauch beim Fahren gegen Verbrauch aus Ladungen. Der Standverlust nur mit Freischaltung.
 */
const props = defineProps<{ trips: TripMonth; chargedKwhPer100km: number | null }>()
const { t } = useI18n()
const { formatConsumption, formatDistance, formatDecimal } = useLocaleFormat()

const rows = computed(() => {
  const m = props.trips
  const list: { key: string; label: string; value: string }[] = [
    { key: 'trips', label: t('story.sheet_trips_trips'), value: String(m.trips) },
    { key: 'km', label: t('story.sheet_trips_km'), value: formatDistance(m.km, { round: true }) },
    { key: 'longest', label: t('story.sheet_trips_longest'), value: formatDistance(m.longestKm, { round: true }) },
  ]
  if (m.tempMin != null && m.tempMax != null) {
    list.push({ key: 'temp', label: t('story.sheet_trips_temp'), value: t('story.sheet_trips_temp_range', { min: formatDecimal(m.tempMin, 0), max: formatDecimal(m.tempMax, 0) }) })
  }
  if (m.driveKwhPer100km != null) list.push({ key: 'drive', label: t('story.sheet_trips_drive'), value: formatConsumption(m.driveKwhPer100km) })
  if (props.chargedKwhPer100km != null) list.push({ key: 'charged', label: t('story.sheet_trips_charged'), value: formatConsumption(props.chargedKwhPer100km) })
  if (m.standbyKwh != null) list.push({ key: 'standby', label: t('story.sheet_trips_standby'), value: `${formatDecimal(m.standbyKwh, 1)} kWh` })
  return list
})
</script>

<template>
  <div data-testid="story-sheet-trips">
    <p class="mt-1 text-sm text-gray-600 dark:text-gray-300">{{ t('story.sheet_trips_text') }}</p>
    <dl class="mt-4 grid grid-cols-[1fr_auto] gap-x-6 gap-y-2 text-[15px] tabular-nums">
      <template v-for="r in rows" :key="r.key">
        <dt class="text-gray-600 dark:text-gray-300">{{ r.label }}</dt>
        <dd class="text-right font-semibold text-gray-900 dark:text-gray-50">{{ r.value }}</dd>
      </template>
    </dl>
  </div>
</template>
