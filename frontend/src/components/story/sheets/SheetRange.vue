<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon } from '@heroicons/vue/24/outline'
import Collapse from '../../shared/Collapse.vue'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { RangeEstimate, RangeSeason } from '../../../composables/storyEnergy'

/**
 * Reichweite 90 -> 10 %: oben eine Zahl (letzte 4 Wochen), darunter die Spanne Winter bis Sommer,
 * die Reserve bis leer und aufklappbar Verbrauch und Ladefenster. Fehlt der eigene Winter,
 * steht der Winter anderer Fahrer desselben Modells mit Tilde da.
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

type PointKey = 'winter' | 'now' | 'summer'
interface Point { key: PointKey; km: number }

/** Nach km sortiert, damit die Leiste auch stimmt, wenn "jetzt" ausserhalb von Winter und Sommer liegt. */
const points = computed<Point[]>(() => {
  const all: Array<{ key: PointKey; km: number | undefined }> = [
    { key: 'winter', km: winter.value?.km },
    { key: 'now', km: props.range.km },
    { key: 'summer', km: props.range.summer?.km },
  ]
  return all.filter((p): p is Point => p.km != null).sort((a, b) => a.km - b.km)
})
const ends = computed(() => points.value.length >= 2 ? [points.value[0], points.value[points.value.length - 1]] : [])
const middle = computed(() => points.value.length === 3 ? points.value[1] : null)

function pct(km: number): number {
  const min = points.value[0].km
  const max = points.value[points.value.length - 1].km
  return max === min ? 50 : ((km - min) / (max - min)) * 100
}
/** Das Label über der Leiste bleibt im Rahmen, auch wenn "jetzt" nah an einem Ende liegt. */
const middleLeft = (km: number) => `${Math.min(80, Math.max(20, pct(km)))}%`
const kmText = (p: Point) => (p.key === 'winter' && winterFromPeers.value ? '~' : '') + formatDistance(p.km)

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

const detailsOpen = ref(false)
const detailsId = useId()
</script>

<template>
  <div>
    <p class="mt-1 text-[28px] font-bold leading-[1.1] text-gray-900 tabular-nums dark:text-gray-50" data-testid="story-range-hero">
      {{ formatDistance(range.km) }}
    </p>
    <p class="mt-1 text-sm text-gray-600 dark:text-gray-300">{{ t('story.range_hero_sub') }}</p>

    <div v-if="ends.length" class="relative mt-5" :class="middle ? 'pt-11' : ''" data-testid="story-range-span">
      <div
        v-if="middle"
        class="absolute top-0 -translate-x-1/2 whitespace-nowrap text-center text-sm tabular-nums"
        :style="{ left: middleLeft(middle.km) }">
        <span class="block text-[12px] uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t(`story.range_point_${middle.key}`) }}</span>
        <span class="font-bold text-gray-900 dark:text-gray-50">{{ kmText(middle) }}</span>
      </div>
      <div class="relative mx-2 h-1.5 rounded-full bg-gray-200 dark:bg-gray-700" aria-hidden="true">
        <span
          v-for="p in points" :key="p.key"
          class="absolute top-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full"
          :class="p.key === 'now'
            ? 'h-4 w-4 bg-story-accent ring-2 ring-white dark:bg-story-accent-dark dark:ring-gray-900'
            : 'h-2.5 w-2.5 bg-gray-900 dark:bg-gray-100'"
          :style="{ left: `${pct(p.km)}%` }" />
      </div>
      <div class="mt-2 flex justify-between gap-4 text-sm tabular-nums">
        <div v-for="(p, i) in ends" :key="p.key" :class="i === 0 ? 'text-left' : 'text-right'">
          <span class="block text-[12px] uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t(`story.range_point_${p.key}`) }}</span>
          <span class="font-bold text-gray-900 dark:text-gray-50">{{ kmText(p) }}</span>
        </div>
      </div>
      <p v-if="winterFromPeers" class="mt-2 text-xs text-gray-500 dark:text-gray-400">
        {{ t('story.range_peer_note', { model: modelName }) }}
      </p>
    </div>

    <p class="mt-5 text-sm text-gray-700 dark:text-gray-300">
      {{ t('story.range_reserve', { km: formatDistance(range.reserveKm) }) }}
    </p>

    <button
      type="button"
      :aria-expanded="detailsOpen"
      :aria-controls="detailsId"
      class="-mx-1 mt-3 inline-flex min-h-11 items-center gap-1 rounded-md px-1 text-sm font-bold text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-gray-900 dark:text-gray-50 dark:focus-visible:ring-gray-100"
      @click="detailsOpen = !detailsOpen">
      {{ t('story.range_details') }}
      <ChevronDownIcon class="h-4 w-4 shrink-0 transition-transform duration-300" :class="detailsOpen ? 'rotate-180 ease-out' : 'ease-in'" aria-hidden="true" />
    </button>
    <Collapse :open="detailsOpen">
      <div :id="detailsId" class="pb-1">
        <p class="mt-2 text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t('story.range_consumption_title') }}</p>
        <table class="mt-1 w-full text-sm tabular-nums" data-testid="story-range-bases">
          <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
            <tr v-for="b in bases" :key="b.label">
              <td class="py-2 text-gray-700 dark:text-gray-300">{{ b.label }}</td>
              <td class="py-2 text-right text-gray-900 dark:text-gray-50">{{ formatConsumption(b.row.consumption) }}</td>
            </tr>
          </tbody>
        </table>
        <p class="mt-4 text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t('story.range_windows_sub') }}</p>
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
    </Collapse>
  </div>
</template>
