<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ribbonSegments, type DayEvent } from '../../../composables/storyDay'

/**
 * Tagesband: Akkustand über 24 h (Fahrt dunkel, Ladung grün, Standzeit gestrichelt), darunter ein
 * Band je Eintrag. aria-hidden, die Liste daneben sagt dasselbe vollständig. Antippbar nur, wenn
 * `interactive` (ab 1024 px); auf dem Handy wäre eine Stunde rund 15 px breit.
 */
const props = defineProps<{ events: DayEvent[]; selectedId: string | null; interactive: boolean }>()
const emit = defineEmits<{ select: [event: DayEvent] }>()
const { t } = useI18n()

const r = computed(() => ribbonSegments(props.events))
const Y = (soc: number) => 6 + (100 - soc) * 0.74
const lineClass = (kind: string) => kind === 'charge'
  ? 'stroke-green-600 dark:stroke-green-400'
  : kind === 'trip' ? 'stroke-gray-900 dark:stroke-gray-100' : 'stroke-gray-300 dark:stroke-gray-600'
const bandClass = (kind: string, on: boolean) => kind === 'charge'
  ? (on ? 'fill-green-700 dark:fill-green-300' : 'fill-green-500/80 dark:fill-green-500/70')
  : (on ? 'fill-gray-900 dark:fill-gray-100' : 'fill-gray-400 dark:fill-gray-500')
const pct = (v: number) => `${Math.round(v)} %`
const pick = (id: string) => {
  const e = props.events.find(x => x.id === id)
  if (e) emit('select', e)
}
</script>

<template>
  <div>
    <div class="relative rounded-2xl bg-gray-50 px-3 pb-2 pt-3 dark:bg-white/5" aria-hidden="true" data-testid="day-ribbon">
      <div class="pointer-events-none absolute inset-x-3 top-3 h-[74px] border-y border-dashed border-gray-200 dark:border-gray-700" />
      <svg viewBox="0 0 1440 100" preserveAspectRatio="none" class="relative block h-[100px] w-full overflow-visible">
        <line v-for="x in [360, 720, 1080]" :key="x" :x1="x" :x2="x" y1="0" y2="98" class="stroke-gray-200 dark:stroke-gray-700" stroke-width="1" vector-effect="non-scaling-stroke" />
        <path v-for="(l, i) in r.lines" :key="`l${i}`" :d="`M${l.x1},${Y(l.soc1)}L${l.x2},${Y(l.soc2)}`" :class="['fill-none', lineClass(l.kind)]"
          :stroke-width="l.kind === 'idle' ? 1.5 : 3" :stroke-dasharray="l.kind === 'idle' ? '3 4' : undefined" stroke-linecap="round" vector-effect="non-scaling-stroke" />
        <circle v-for="(d, i) in r.dots" :key="`d${i}`" :cx="d.x" :cy="Y(d.soc)" r="3" :class="d.kind === 'charge' ? 'fill-green-600' : 'fill-gray-900 dark:fill-gray-100'" />
        <rect v-for="b in r.bands" :key="`b${b.id}`" :x="b.x" y="88" :width="Math.max(6, b.w)" height="9" rx="2" :class="bandClass(b.kind, b.id === selectedId)" />
        <template v-if="interactive">
          <rect v-for="b in r.bands" :key="`h${b.id}`" :x="b.x + b.w / 2 - Math.max(36, b.w + 12) / 2" y="0" :width="Math.max(36, b.w + 12)" height="100"
            class="cursor-pointer fill-transparent" @click="pick(b.id)" />
        </template>
      </svg>
      <div class="relative mt-1 h-3.5 text-[10.5px] tabular-nums text-gray-400 dark:text-gray-500">
        <span v-for="(h, i) in ['0', '6', '12', '18', t('storyday.axis_24')]" :key="i" class="absolute whitespace-nowrap"
          :class="i === 0 ? '' : i === 4 ? '-translate-x-full' : '-translate-x-1/2'" :style="{ left: `${i * 25}%` }">{{ h }}</span>
      </div>
      <div class="absolute right-3 top-1.5 text-[10.5px] font-semibold text-gray-400 dark:text-gray-500">100 %</div>
    </div>
    <p v-if="r.caption" class="mt-2 text-[12.5px] tabular-nums text-gray-500 dark:text-gray-400">
      {{ t('storyday.caption', { first: pct(r.caption.first!), last: pct(r.caption.last!), min: pct(r.caption.min) }) }}
    </p>
  </div>
</template>
