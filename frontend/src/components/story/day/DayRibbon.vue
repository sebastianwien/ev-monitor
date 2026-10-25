<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ribbonSegments, batteryBars, type BatteryBar, type DayEvent } from '../../../composables/storyDay'

/**
 * Akku des Tages wie im Akku-Diagramm des Handys: ein Balken je halbe Stunde, Höhe = Akkustand,
 * grün beim Laden, dunkel beim Fahren, grau im Stand. Wo es keinen Messwert gibt, kein Balken.
 * aria-hidden, die Liste darunter sagt dasselbe vollständig. Antippbar nur, wenn `interactive`.
 */
const props = defineProps<{ events: DayEvent[]; selectedId: string | null; interactive: boolean }>()
const emit = defineEmits<{ select: [event: DayEvent] }>()
const { t } = useI18n()

const bars = computed(() => batteryBars(props.events))
const caption = computed(() => ribbonSegments(props.events).caption)
const barClass = (b: BatteryBar) => b.kind === 'charge'
  ? 'bg-green-600 dark:bg-green-400'
  : b.kind === 'trip' ? 'bg-gray-900 dark:bg-gray-100' : 'bg-gray-300 dark:bg-gray-600'
/** "+x %" über dem Balken, in dem eine Ladung endet */
const gains = computed(() => props.events
  .filter(e => e.kind === 'charge' && e.socFrom != null && e.socTo != null && e.socTo - e.socFrom >= 5)
  .map(e => ({ id: e.id, slot: Math.min(47, Math.floor(Math.max(e.from, e.to - 1) / 30)), soc: e.socTo!, gain: Math.round(e.socTo! - e.socFrom!) })))
const pct = (v: number) => `${Math.round(v)} %`
function pick(b: BatteryBar) {
  const over = props.events.filter(e => e.from < b.from + 30 && e.to > b.from)
  const e = over.find(v => v.kind === 'charge') ?? over[0]
  if (e) emit('select', e)
}
</script>

<template>
  <div>
    <div class="rounded-2xl bg-gray-50 pb-3 pl-12 pr-4 pt-6 dark:bg-white/5" data-testid="day-ribbon" aria-hidden="true">
      <div class="relative h-[100px]">
        <!-- y: 0, 50, 100 % -->
        <div v-for="v in [100, 50, 0]" :key="v" class="absolute inset-x-0 border-t border-dashed border-gray-200 dark:border-gray-700" :style="{ top: `${100 - v}%` }">
          <span class="absolute -left-11 w-9 -translate-y-1/2 whitespace-nowrap text-right text-[10.5px] tabular-nums text-gray-400 dark:text-gray-500">{{ v }} %</span>
        </div>
        <div class="absolute inset-0 flex items-end gap-px">
          <component :is="interactive && b.soc != null ? 'button' : 'span'" v-for="b in bars" :key="b.from" type="button" tabindex="-1"
            class="relative flex h-full flex-1 items-end" :class="interactive && b.soc != null ? 'cursor-pointer' : ''"
            @click="interactive && b.soc != null && pick(b)">
            <i v-if="b.soc != null" data-testid="day-bar" :data-kind="b.kind" class="block w-full rounded-t-[2px]" :class="barClass(b)"
              :style="{ height: `${Math.max(2, b.soc)}%` }" />
          </component>
        </div>
        <span v-for="g in gains" :key="g.id" data-testid="day-ribbon-gain"
          class="absolute -translate-x-1/2 -translate-y-full whitespace-nowrap pb-1 text-[12px] font-bold tabular-nums text-green-700 dark:text-green-400"
          :style="{ left: `${((g.slot + 0.5) / 48) * 100}%`, top: `${100 - g.soc}%` }">+{{ g.gain }} %</span>
      </div>
      <div class="relative mt-1.5 h-3.5 text-[10.5px] tabular-nums text-gray-400 dark:text-gray-500">
        <span v-for="(h, i) in ['0', '6', '12', '18', t('storyday.axis_24')]" :key="i" class="absolute whitespace-nowrap"
          :class="i === 0 ? '' : i === 4 ? '-translate-x-full' : '-translate-x-1/2'" :style="{ left: `${i * 25}%` }">{{ h }}</span>
      </div>
    </div>
    <p v-if="caption" class="mt-2 text-[12.5px] tabular-nums text-gray-500 dark:text-gray-400">
      {{ t('storyday.caption', { first: pct(caption.first!), last: pct(caption.last!), min: pct(caption.min) }) }}
    </p>
  </div>
</template>
