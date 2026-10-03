<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import RangeGauge from '../RangeGauge.vue'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { RangeEstimate, RangeSeason } from '../../../composables/storyEnergy'

/** Reichweite je SoC-Fenster. Oben drei Basen: letzte 4 Wochen (Kachel), Sommer und Winter ueber alle Jahre. */
const props = defineProps<{
  range: RangeEstimate
  capacityKwh: number | null
}>()

const { t } = useI18n()
const { formatConsumption, formatDistance, formatDecimal } = useLocaleFormat()

const bases = computed<Array<{ key: string; row: RangeSeason | null }>>(() => [
  { key: 'recent', row: { consumption: props.range.consumption, km: props.range.km } },
  { key: 'summer', row: props.range.summer },
  { key: 'winter', row: props.range.winter },
])
</script>

<template>
  <div>
    <p class="text-sm text-gray-600 dark:text-gray-300 mt-1">
      {{ t('story.sheet_range_text', { capacity: capacityKwh != null ? formatDecimal(capacityKwh, 1) : '–', max: 90, min: 10 }) }}
    </p>
    <table class="w-full text-sm mt-3 tabular-nums" data-testid="story-range-bases">
      <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
        <tr v-for="b in bases" :key="b.key">
          <td class="py-2 text-gray-700 dark:text-gray-300">
            {{ t(`story.range_basis_${b.key}`) }}
            <span class="block text-xs text-gray-400">{{ b.row ? formatConsumption(b.row.consumption) : t('story.range_basis_none') }}</span>
          </td>
          <td class="py-2 text-right font-bold text-gray-900 dark:text-gray-50">{{ b.row ? formatDistance(b.row.km) : '–' }}</td>
        </tr>
      </tbody>
    </table>
    <div class="mt-4">
      <RangeGauge :range="range" :label="t('story.range_window', { max: 90, min: 10 })" />
    </div>
    <p class="mt-3 text-xs text-gray-500 dark:text-gray-400">{{ t('story.range_windows_sub') }}</p>
    <table class="w-full text-sm mt-1 tabular-nums">
      <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
        <tr v-for="w in range.windows" :key="w.socMax">
          <td class="py-2 text-gray-700 dark:text-gray-300">
            {{ t('story.range_window', { max: w.socMax, min: w.socMin }) }}
            <span v-if="w.recommended" class="ml-1 text-xs text-gray-400">({{ t('story.range_recommended') }})</span>
          </td>
          <td class="py-2 text-right font-bold text-gray-900 dark:text-gray-50">{{ formatDistance(w.km) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
