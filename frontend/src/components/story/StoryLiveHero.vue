<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon } from '@heroicons/vue/24/outline'
import SocRing from './SocRing.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { LiveChargingData } from '../../composables/useChargingLive'
import { prefersReducedEffects } from '../../composables/prefersReducedEffects'

/**
 * Der Hero waehrend einer Ladung (AC oder DC): SoC-Ring, Leistung, Restzeit, Reichweite.
 * Ersetzt die Monatszahl, solange geladen wird. Keine Kosten: die Live-Daten tragen keine.
 */
const props = defineProps<{ live: LiveChargingData }>()

const { t } = useI18n()
const { formatDecimal, formatDistance } = useLocaleFormat()

const reduceMotion = prefersReducedEffects()
const shownKw = ref(0)
let raf = 0
function tickTo(target: number) {
  cancelAnimationFrame(raf)
  if (reduceMotion) { shownKw.value = target; return }
  const from = shownKw.value
  const start = performance.now()
  const step = (now: number) => {
    const p = Math.min(1, (now - start) / 500)
    shownKw.value = from + (target - from) * (1 - Math.pow(1 - p, 3))
    if (p < 1) raf = requestAnimationFrame(step)
  }
  raf = requestAnimationFrame(step)
}
onMounted(() => tickTo(props.live.powerKw ?? 0))
watch(() => props.live.powerKw, v => tickTo(v ?? 0))
</script>

<template>
  <section class="pt-3 pb-5 border-b border-gray-200 dark:border-gray-700" data-testid="story-live-hero">
    <p class="flex items-center gap-2 text-xs uppercase tracking-wider text-story-accent dark:text-story-accent-dark">
      <span class="w-1.5 h-1.5 rounded-full bg-current motion-safe:animate-pulse" />
      {{ t('story.live_eyebrow') }}<span v-if="live.chargingType" class="text-gray-400">{{ live.chargingType }}</span>
    </p>

    <div class="mt-3 flex items-center gap-5 md:gap-8">
      <SocRing :soc="live.socPercent ?? 0" :soc-start="live.socAtSessionStart" :limit="live.chargeLimitSoc">
        <span class="block font-extrabold text-4xl md:text-5xl tabular-nums leading-none text-gray-900 dark:text-gray-50">
          {{ Math.round(live.socPercent ?? 0) }}<span class="text-lg font-semibold text-gray-500">%</span>
        </span>
        <span v-if="live.socAtSessionStart != null" class="block text-[11px] text-gray-500 dark:text-gray-400 mt-1">
          {{ t('story.live_soc_from', { start: Math.round(live.socAtSessionStart) }) }}
        </span>
      </SocRing>

      <div class="min-w-0 flex-1">
        <p class="font-extrabold tracking-tight leading-none text-gray-900 dark:text-gray-50 tabular-nums text-[44px] md:text-[56px]">
          {{ formatDecimal(shownKw, 0) }}<span class="text-lg font-semibold text-gray-500 ml-1">kW</span>
        </p>
        <p v-if="live.timeToFullMinutes != null" class="mt-2 flex items-center gap-1.5 text-[15px] text-gray-800 dark:text-gray-200">
          <BoltIcon class="w-4 h-4 text-story-accent dark:text-story-accent-dark shrink-0" />
          {{ t('story.live_eta', { min: live.timeToFullMinutes, limit: Math.round(live.chargeLimitSoc ?? 100) }) }}
        </p>
        <p v-if="live.estRangeKm != null" class="mt-1 text-sm text-gray-600 dark:text-gray-300">
          {{ t('story.live_range', { km: formatDistance(live.estRangeKm) }) }}
        </p>
      </div>
    </div>
  </section>
</template>
