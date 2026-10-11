<template>
  <!-- Three arcs on one scale: the manufacturer's figure is the full track, the year average
       and the winter range are drawn on top. The numbers are repeated in the legend as text,
       so the drawing itself is decorative for screen readers. -->
  <figure class="w-full">
    <div class="relative mx-auto w-full max-w-[320px] md:max-w-[400px]">
    <svg viewBox="0 0 400 224" class="block h-auto w-full" role="img" :aria-label="ariaLabel">
      <path :d="ARC" fill="none" stroke-width="18" stroke-linecap="round" class="stroke-gray-200 dark:stroke-gray-800" />
      <path v-if="realKm != null" :d="ARC" fill="none" stroke-width="18" stroke-linecap="round" pathLength="100"
            class="gauge-arc stroke-green-600 dark:stroke-green-500" :style="arcStyle(realKm, '0.2s')" />
      <template v-if="winterKm != null">
        <path :d="INNER_ARC" fill="none" stroke-width="8" stroke-linecap="round" class="stroke-gray-200 dark:stroke-gray-800" />
        <path :d="INNER_ARC" fill="none" stroke-width="8" stroke-linecap="round" pathLength="100"
              class="gauge-arc stroke-sky-600 dark:stroke-sky-400" :style="arcStyle(winterKm, '0.6s')" />
      </template>
    </svg>
    <!-- Centre: the figure and the battery window it rests on; the SVG keeps the arcs only -->
    <div class="pointer-events-none absolute inset-x-0 bottom-[8%] grid justify-items-center gap-1" aria-hidden="true">
      <p class="flex items-baseline gap-1.5 leading-none">
        <span class="text-[clamp(44px,13vw,64px)] font-bold tracking-tight tabular-nums text-gray-900 dark:text-gray-100">{{ formatDistance(shownKm, { showUnit: false }) }}</span>
        <span class="text-[15px] font-semibold text-gray-500 dark:text-gray-400">{{ distanceUnitLabel() }}</span>
      </p>
      <p class="flex items-center gap-1 text-[12px] font-semibold text-gray-500 dark:text-gray-400">
        <Battery100Icon class="h-4 w-4" /><span>100 %</span>
        <ArrowRightIcon class="h-3 w-3" />
        <Battery0Icon class="h-4 w-4" /><span>10 %</span>
        <span v-if="realKm == null" class="ml-1 font-normal">{{ t('model.story.gauge_wltp_only') }}</span>
      </p>
    </div>
    </div>
    <!-- One line on every width: short labels, no wrap -->
    <figcaption class="mt-1 flex flex-nowrap justify-center gap-x-3 whitespace-nowrap text-[12px] text-gray-600 sm:text-[13px] dark:text-gray-400">
      <span v-if="realKm != null" class="inline-flex items-center gap-1.5"><i class="h-2.5 w-2.5 rounded-sm bg-green-600 dark:bg-green-500" aria-hidden="true"></i>{{ t('model.story.legend_year', { value: formatDistance(realKm) }) }}</span>
      <span v-if="winterKm != null" class="inline-flex items-center gap-1.5"><i class="h-2.5 w-2.5 rounded-sm bg-sky-600 dark:bg-sky-400" aria-hidden="true"></i>{{ t('model.story.legend_winter', { value: formatDistance(winterKm) }) }}</span>
      <span class="inline-flex items-center gap-1.5"><i class="h-2.5 w-2.5 rounded-sm bg-gray-300 dark:bg-gray-700" aria-hidden="true"></i>{{ t('model.story.legend_wltp', { label: ratingLabel, value: formatDistance(wltpKm) }) }}</span>
    </figcaption>
  </figure>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRightIcon, Battery0Icon, Battery100Icon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { useTweenedNumber } from '../../composables/useTweenedNumber'

const props = defineProps<{
  wltpKm: number
  realKm: number | null
  winterKm: number | null
  /** WLTP or EPA, printed at the end of the scale */
  ratingLabel: string
}>()

const { t } = useI18n()
const { formatDistance, distanceUnitLabel } = useLocaleFormat()

/** Half circle, radius 160, drawn left to right. pathLength="100" lets the dash offset work in percent. */
const ARC = 'M40 210 A160 160 0 0 1 360 210'
/** Winter on its own ring inside the main one, so blue never sits on green */
const INNER_ARC = 'M66 210 A134 134 0 0 1 334 210'

/** The scale ends at the larger of manufacturer and measurement, so a car beating its WLTP still fits. */
const maxKm = computed(() => Math.max(props.wltpKm, props.realKm ?? 0, props.winterKm ?? 0))
const centerKm = computed(() => props.realKm ?? props.wltpKm)
/** Counts to the new figure when the variant changes */
const shownKm = useTweenedNumber(centerKm)

/** Arcs start empty and glide to their value; on a variant change they glide again, without the start delay */
const drawn = ref(false)
const firstDraw = ref(true)
let firstDrawTimer: ReturnType<typeof setTimeout> | null = null
onMounted(() => {
  requestAnimationFrame(() => { drawn.value = true })
  firstDrawTimer = setTimeout(() => { firstDraw.value = false }, 2000)
})
onBeforeUnmount(() => { if (firstDrawTimer) clearTimeout(firstDrawTimer) })
const ariaLabel = computed(() => props.realKm != null
  ? t('model.story.gauge_label', { wltp: formatDistance(props.wltpKm), real: formatDistance(props.realKm), winter: props.winterKm != null ? formatDistance(props.winterKm) : '-' })
  : t('model.story.gauge_label_none', { wltp: formatDistance(props.wltpKm) }))

function arcStyle(km: number, delay: string) {
  const offset = 100 - Math.min(100, km / maxKm.value * 100)
  return { strokeDashoffset: drawn.value ? offset : 100, transitionDelay: firstDraw.value ? delay : '0s' }
}
</script>

<style scoped>
.gauge-arc {
  stroke-dasharray: 100;
}
/* One transition for the first draw and for every later change of the values */
@media (prefers-reduced-motion: no-preference) {
  .gauge-arc {
    transition: stroke-dashoffset 1s cubic-bezier(0.2, 0.7, 0.2, 1);
  }
}
</style>
