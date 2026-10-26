<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { RangeEstimate, RangeSeason } from '../../../composables/storyEnergy'

/**
 * Reichweite 90 -> 10 %: oben eine Zahl (letzte 4 Wochen), darunter Sommer und Winter, wie weit es
 * notfalls bis leer geht, dann Verbrauch und Ladefenster. Fehlt der eigene Winter, steht der Winter
 * anderer Fahrer desselben Modells mit Tilde da; eine fehlende Saison entfällt.
 */
const props = defineProps<{
  range: RangeEstimate
  capacityKwh: number | null
  modelName: string
}>()

const { t } = useI18n()
const { formatConsumption, formatDistance, formatDecimal } = useLocaleFormat()

const winter = computed(() => props.range.winter ?? props.range.peerWinter)
const winterFromPeers = computed(() => !props.range.winter && !!props.range.peerWinter)

const seasons = computed(() => [
  { key: 'summer', label: t('story.range_season_summer'), km: props.range.summer?.km, tilde: false },
  { key: 'winter', label: t('story.range_season_winter'), km: winter.value?.km, tilde: winterFromPeers.value },
].filter((s): s is { key: string; label: string; km: number; tilde: boolean } => s.km != null))

const bases = computed<Array<{ label: string; row: RangeSeason }>>(() => {
  const rows: Array<{ label: string; row: RangeSeason | null }> = [
    { label: t('story.range_basis_recent'), row: { consumption: props.range.consumption, km: props.range.km } },
    { label: t('story.range_basis_summer'), row: props.range.summer },
    {
      label: winterFromPeers.value ? t('story.range_basis_winter_peers', { model: props.modelName }) : t('story.range_basis_winter'),
      row: winter.value,
    },
  ]
  return rows.filter((r): r is { label: string; row: RangeSeason } => r.row != null)
})

const LABEL = 'text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400'
</script>

<template>
  <div>
    <p class="mt-1 text-[28px] font-bold leading-[1.1] text-gray-900 tabular-nums dark:text-gray-50" data-testid="story-range-hero">
      {{ formatDistance(range.km) }}
    </p>
    <p class="mt-1 text-sm text-gray-600 dark:text-gray-300">{{ t('story.range_hero_sub') }}</p>

    <table v-if="seasons.length" class="mt-3 w-full text-sm tabular-nums" data-testid="story-range-seasons">
      <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
        <tr v-for="s in seasons" :key="s.key">
          <td class="py-2 text-gray-700 dark:text-gray-300">{{ s.label }}</td>
          <td class="py-2 text-right font-bold text-gray-900 dark:text-gray-50">{{ (s.tilde ? '~' : '') + formatDistance(s.km) }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="winterFromPeers" class="mt-1 text-xs text-gray-500 dark:text-gray-400">
      {{ t('story.range_peer_note', { model: modelName }) }}
    </p>
    <p class="mt-3 text-sm text-gray-700 dark:text-gray-300">
      {{ t('story.range_to_empty', { km: formatDistance(range.emptyKm) }) }}
    </p>

    <p class="mt-6" :class="LABEL">{{ t('story.range_consumption_title') }}</p>
    <table class="mt-1 w-full text-sm tabular-nums" data-testid="story-range-bases">
      <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
        <tr v-for="b in bases" :key="b.label">
          <td class="py-2 text-gray-700 dark:text-gray-300">{{ b.label }}</td>
          <td class="py-2 text-right text-gray-900 dark:text-gray-50">{{ formatConsumption(b.row.consumption) }}</td>
        </tr>
      </tbody>
    </table>

    <p class="mt-5" :class="LABEL">{{ t('story.range_windows_sub') }}</p>
    <table class="mt-1 w-full text-sm tabular-nums">
      <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
        <tr v-for="w in range.windows" :key="w.socMax">
          <td class="py-2 text-gray-700 dark:text-gray-300">
            {{ t('story.range_window', { max: w.socMax, min: w.socMin }) }}
            <span v-if="w.recommended" class="ml-1 text-xs text-gray-400">({{ t('story.range_recommended') }})</span>
          </td>
          <td class="py-2 text-right text-gray-900 dark:text-gray-50">{{ formatDistance(w.km) }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="capacityKwh != null" class="mt-3 text-xs text-gray-500 dark:text-gray-400">
      {{ t('story.range_capacity', { capacity: formatDecimal(capacityKwh, 1) }) }}
    </p>
  </div>
</template>
