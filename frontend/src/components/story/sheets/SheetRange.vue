<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import { rangeBasisLabel, rangeOriginKey, type RangeEstimate, type RangeSeason } from '../../../composables/storyEnergy'

/**
 * Reichweite pro Ladung von 100 auf 10 %: oben eine Zahl (letzte 4 Wochen) mit dem Herkunftssatz, wenn
 * sie nicht aus den eigenen Fahrten kommt; darunter Sommer und Winter, je Zeile die Herkunft, wenn sie
 * nicht die eigenen Fahrten sind; dann der Verbrauch je Zeile. Alle km rechnet das Backend.
 */
const props = defineProps<{
  range: RangeEstimate
  capacityKwh: number | null
  modelName: string
}>()

const { t } = useI18n()
const { formatConsumption, formatDistance, formatDecimal } = useLocaleFormat()

const own = (row: Pick<RangeSeason, 'source'>) => row.source === 'OWN_DRIVE'
const tilde = (row: Pick<RangeSeason, 'source'>) => (own(row) ? '' : '~')
const origin = (row: RangeSeason) => t(rangeOriginKey(row), { model: props.modelName })

/** Nur wenn die Basis nicht die eigenen Fahrten sind; mit eigenen Fahrten stimmt der Satz unter der Zahl wörtlich. */
const basisNote = computed(() => {
  switch (props.range.source) {
    case 'PEER_DRIVE':
      return t(props.range.scope === 'VARIANT' ? 'story.range_note_peer_variant' : 'story.range_note_peer_model', { model: props.modelName })
    case 'OWN_CHARGES': return t('story.range_note_charges')
    case 'PEER_CHARGES': return t('story.range_note_peer_charges', { model: props.modelName })
    case 'WLTP_ESTIMATE': return t('story.range_note_wltp')
    default: return ''
  }
})

const seasons = computed(() => [
  { key: 'summer', label: t('story.range_season_summer'), row: props.range.summer },
  { key: 'winter', label: t('story.range_season_winter'), row: props.range.winter },
].filter((s): s is { key: string; label: string; row: RangeSeason } => s.row != null))

const bases = computed<Array<{ label: string; row: RangeSeason }>>(() => {
  const season = (key: string, row: RangeSeason | null) =>
    row == null ? null : { label: own(row) ? t(key) : `${t(key)}, ${origin(row)}`, row }
  return [
    { label: rangeBasisLabel(props.range, props.modelName, t), row: props.range as RangeSeason },
    season('story.range_basis_summer', props.range.summer),
    season('story.range_basis_winter', props.range.winter),
  ].filter((r): r is { label: string; row: RangeSeason } => r != null)
})

const LABEL = 'text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400'
</script>

<template>
  <div>
    <p class="mt-1 text-[28px] font-bold leading-[1.1] text-gray-900 tabular-nums dark:text-gray-50" data-testid="story-range-hero">
      {{ tilde(range) + formatDistance(range.km) }}
    </p>
    <p class="mt-1 text-sm text-gray-600 dark:text-gray-300">{{ own(range) ? t('story.range_hero_sub') : t('story.range_hero_sub_plain') }}</p>
    <p v-if="basisNote" class="mt-1 text-xs text-gray-500 dark:text-gray-400" data-testid="story-range-basis-note">{{ basisNote }}</p>

    <table v-if="seasons.length" class="mt-3 w-full text-sm tabular-nums" data-testid="story-range-seasons">
      <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
        <tr v-for="s in seasons" :key="s.key">
          <td class="py-2 text-gray-700 dark:text-gray-300">
            {{ s.label }}
            <span v-if="!own(s.row)" class="block text-xs text-gray-500 dark:text-gray-400">{{ origin(s.row) }}</span>
          </td>
          <td class="py-2 text-right align-top font-bold text-gray-900 dark:text-gray-50">{{ tilde(s.row) + formatDistance(s.row.km) }}</td>
        </tr>
      </tbody>
    </table>

    <p class="mt-6" :class="LABEL">{{ t('story.range_consumption_title') }}</p>
    <table class="mt-1 w-full text-sm tabular-nums" data-testid="story-range-bases">
      <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
        <tr v-for="b in bases" :key="b.label">
          <td class="py-2 text-gray-700 dark:text-gray-300">{{ b.label }}</td>
          <td class="py-2 text-right text-gray-900 dark:text-gray-50">{{ formatConsumption(b.row.consumption) }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="capacityKwh != null" class="mt-3 text-xs text-gray-500 dark:text-gray-400">
      {{ t('story.range_capacity', { capacity: formatDecimal(capacityKwh, 1) }) }}
    </p>
  </div>
</template>
