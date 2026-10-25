<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ribbonSegments, ribbonWindow, type DayEvent } from '../../../composables/storyDay'

/**
 * Akku-Kurve des Tages, gezoomt auf die aktive Zeit: Fläche unter dem Akkustand, Fahrt dunkel,
 * Ladung grün mit "+x %", Standzeit gestrichelt. aria-hidden, die Liste darunter sagt dasselbe
 * vollständig. Antippbar nur, wenn `interactive` (ab 1024 px).
 */
const props = defineProps<{ events: DayEvent[]; selectedId: string | null; interactive: boolean }>()
const emit = defineEmits<{ select: [event: DayEvent] }>()
const { t } = useI18n()

const W = 1000, H = 100
const r = computed(() => ribbonSegments(props.events))
const win = computed(() => ribbonWindow(props.events))
const x = (min: number) => ((min - win.value.from) / (win.value.to - win.value.from)) * W
const y = (soc: number) => H - soc
const pctX = (min: number) => `${(x(min) / W) * 100}%`
const pctY = (soc: number) => `${100 - soc}%`

const lineClass = (kind: string) => kind === 'charge'
  ? 'stroke-green-600 dark:stroke-green-400'
  : kind === 'trip' ? 'stroke-gray-900 dark:stroke-gray-100' : 'stroke-gray-400 dark:stroke-gray-500'
/** Fläche je Abschnitt bis zur Grundlinie; nebeneinander ergeben sie eine Fläche ohne Lücke */
const area = (l: { x1: number; soc1: number; x2: number; soc2: number }) =>
  `M${x(l.x1)},${H}L${x(l.x1)},${y(l.soc1)}L${x(l.x2)},${y(l.soc2)}L${x(l.x2)},${H}Z`
/** "+x %" über dem Ende jeder Ladung, die nennenswert geladen hat */
const gains = computed(() => props.events
  .filter(e => e.kind === 'charge' && e.socFrom != null && e.socTo != null && e.socTo - e.socFrom >= 5)
  .map(e => ({ id: e.id, at: e.to, soc: e.socTo!, gain: Math.round(e.socTo! - e.socFrom!) })))
const hour = (min: number) => `${Math.floor(min / 60)}:00`
const pct = (v: number) => `${Math.round(v)} %`
const pick = (id: string) => {
  const e = props.events.find(v => v.id === id)
  if (e) emit('select', e)
}
</script>

<template>
  <div>
    <div class="rounded-2xl bg-gray-50 pb-3 pl-12 pr-4 pt-6 dark:bg-white/5" data-testid="day-ribbon" aria-hidden="true">
      <div class="relative h-[110px]">
        <!-- y: 0, 50, 100 % -->
        <div v-for="v in [100, 50, 0]" :key="v" class="absolute inset-x-0 border-t border-dashed border-gray-200 dark:border-gray-700" :style="{ top: pctY(v) }">
          <span class="absolute -left-11 w-9 -translate-y-1/2 whitespace-nowrap text-right text-[10.5px] tabular-nums text-gray-400 dark:text-gray-500">{{ v }} %</span>
        </div>
        <svg :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="none" class="absolute inset-0 h-full w-full overflow-visible">
          <path v-for="(l, i) in r.lines" :key="`a${i}`" :d="area(l)" class="fill-gray-900/[0.06] dark:fill-white/[0.06]" />
          <path v-for="(l, i) in r.lines" :key="`l${i}`" :d="`M${x(l.x1)},${y(l.soc1)}L${x(l.x2)},${y(l.soc2)}`" :class="['fill-none', lineClass(l.kind)]"
            :stroke-width="l.kind === 'idle' ? 1.5 : 3" :stroke-dasharray="l.kind === 'idle' ? '3 4' : undefined"
            stroke-linecap="round" vector-effect="non-scaling-stroke" />
          <template v-if="interactive">
            <rect v-for="e in events" :key="`h${e.id}`" :x="x(e.from) - 12" y="0" :width="Math.max(24, x(e.to) - x(e.from) + 24)" :height="H"
              class="cursor-pointer fill-transparent" @click="pick(e.id)" />
          </template>
        </svg>
        <!-- Punkte als HTML: im gestauchten SVG würden Kreise zu Ellipsen -->
        <i v-for="(d, i) in r.dots" :key="`d${i}`" class="absolute h-2 w-2 -translate-x-1/2 -translate-y-1/2 rounded-full"
          :class="d.kind === 'charge' ? 'bg-green-600 dark:bg-green-400' : 'bg-gray-900 dark:bg-gray-100'" :style="{ left: pctX(d.x), top: pctY(d.soc) }" />
        <span v-for="g in gains" :key="`g${g.id}`" data-testid="day-ribbon-gain"
          class="absolute -translate-x-1/2 -translate-y-full whitespace-nowrap pb-1 text-[12px] font-bold tabular-nums text-green-700 dark:text-green-400"
          :style="{ left: pctX(g.at), top: pctY(g.soc) }">+{{ g.gain }} %</span>
      </div>
      <div class="relative mt-1.5 h-3.5 text-[10.5px] tabular-nums text-gray-400 dark:text-gray-500">
        <span v-for="(m, i) in win.ticks" :key="m" class="absolute whitespace-nowrap"
          :class="i === 0 ? '' : i === win.ticks.length - 1 ? '-translate-x-full' : '-translate-x-1/2'" :style="{ left: pctX(m) }">{{ hour(m) }}</span>
      </div>
    </div>
    <p v-if="r.caption" class="mt-2 text-[12.5px] tabular-nums text-gray-500 dark:text-gray-400">
      {{ t('storyday.caption', { first: pct(r.caption.first!), last: pct(r.caption.last!), min: pct(r.caption.min) }) }}
    </p>
  </div>
</template>
