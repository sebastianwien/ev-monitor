<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DayCoverage } from '../../composables/storyModel'
import { useLocaleFormat } from '../../composables/useLocaleFormat'

/**
 * Der Zeitstrahl unter dem Hero: ein Feld je Tag des Monats (Ladung grün, sonst grau, Tage nach
 * heute heller), darüber eine Säule je Fahrtag nach km, darunter die Achse und eine Legende. Beim
 * Öffnen ist der letzte Ladetag gewählt; seine Tagesanzeige steht als Sprechblase unter dem
 * Zeitstrahl mit Spitze zum Tag. Überfahren, Antippen oder Pfeiltasten wählen einen anderen Tag,
 * Klick, Tipp oder Enter öffnen das Tagesblatt.
 */
const props = defineProps<{ days: DayCoverage[] }>()
/** Klick, Tap oder Enter auf einen Tag bis heute: Tagesblatt öffnen */
const emit = defineEmits<{ 'open-day': [day: number] }>()
const { t } = useI18n()
const { formatDecimal, formatDistance } = useLocaleFormat()

/** Im laufenden Monat nur die Tage bis heute. */
const elapsed = computed(() => props.days.filter(d => !d.future).length)
/** Tage nach heute heller. */
const cellClass = (d: DayCoverage) => {
  if (d.charges > 0) return 'bg-green-700 dark:bg-green-400'
  if (d.future) return 'bg-gray-100 dark:bg-gray-800'
  return 'bg-gray-200 dark:bg-gray-600'
}

/** Der letzte Ladetag: beim Öffnen gewählt, und wieder, sobald Zeiger oder Fokus gehen. */
const lastCharged = computed<number | null>(() => {
  for (let i = props.days.length - 1; i >= 0; i--) if (props.days[i].charges > 0 && !props.days[i].future) return i
  return null
})
const active = ref<number | null>(lastCharged.value)
watch(lastCharged, v => { active.value = v })
const bar = ref<HTMLElement | null>(null)

function indexAt(clientX: number): number | null {
  const el = bar.value
  if (!el || !props.days.length) return null
  const r = el.getBoundingClientRect()
  if (r.width <= 0) return null
  return Math.min(props.days.length - 1, Math.max(0, Math.floor(((clientX - r.left) / r.width) * props.days.length)))
}
function onPointer(e: PointerEvent) {
  const i = indexAt(e.clientX)
  if (i != null) active.value = i
}
function onLeave(e: PointerEvent) {
  if (e.pointerType !== 'touch') active.value = lastCharged.value
}
function open(i: number | null) {
  const d = i != null ? props.days[i] : null
  if (d && !d.future) emit('open-day', d.day)
}
const onClick = (e: MouseEvent) => open(indexAt(e.clientX))
function onKey(e: KeyboardEvent) {
  if (e.key === 'Enter') { open(active.value); return }
  const n = props.days.length
  if (!n) return
  const cur = active.value ?? -1
  const next = { ArrowRight: Math.min(n - 1, cur + 1), ArrowLeft: Math.max(0, cur - 1), Home: 0, End: elapsed.value > 0 ? elapsed.value - 1 : n - 1 }[e.key]
  if (next === undefined) return
  e.preventDefault()
  active.value = next
}

const tip = computed(() => {
  const d = active.value != null ? props.days[active.value] : null
  if (!d) return ''
  if (d.future) return t('story.day_tip', { day: d.day, text: t('story.day_tip_future') })
  const parts = []
  if (d.charges > 0) parts.push(t('story.day_tip_charges', { n: d.charges, kwh: `${formatDecimal(d.kwh, 1)} kWh` }, d.charges))
  if (d.trips > 0) parts.push(t('story.day_tip_trips', { n: d.trips, km: formatDistance(d.tripKm, { round: true }) }, d.trips))
  return t('story.day_tip', { day: d.day, text: parts.length ? parts.join(' · ') : t('story.day_tip_empty') })
})
/** Höchste km eines Tages: Maßstab der Fahrt-Säulen. */
const maxKm = computed(() => Math.max(0, ...props.days.map(d => d.tripKm)))
const hasTrips = computed(() => props.days.some(d => d.trips > 0))
/** Säulenhöhe in Prozent; eine Fahrt ohne km bleibt als Strich sichtbar. */
const kmHeight = (d: DayCoverage) => maxKm.value > 0 ? `${Math.max(12, Math.round((d.tripKm / maxKm.value) * 100))}%` : '12%'
/** Mitte des Felds in Prozent. */
const pos = (index: number) => `${((index + 0.5) / props.days.length) * 100}%`
/**
 * Sprechblase: mittig unter dem Tag, am Rand nach gemessener Breite in die Leiste geschoben, damit
 * kein Text abgeschnitten wird; die Spitze bleibt dabei über dem Tag. Ohne Maße (vor dem ersten
 * Rendern) nur die Mitte.
 */
const tipEl = ref<HTMLElement | null>(null)
const tipLeft = ref('0%')
const arrowLeft = ref('50%')
function placeTip() {
  const i = active.value
  if (i == null) return
  const center = (i + 0.5) / props.days.length
  const barWidth = bar.value?.getBoundingClientRect().width ?? 0
  const tipWidth = tipEl.value?.getBoundingClientRect().width ?? 0
  if (!barWidth || !tipWidth) { tipLeft.value = `${center * 100}%`; arrowLeft.value = '50%'; return }
  const left = Math.max(0, Math.min(barWidth - tipWidth, center * barWidth - tipWidth / 2))
  tipLeft.value = `${Math.round(left)}px`
  arrowLeft.value = `${Math.round(center * barWidth - left)}px`
}
watch([active, tip], async () => { await nextTick(); placeTip() }, { immediate: true })
const ticks = computed(() => [1, 8, 15, 22, 29].filter(d => d <= props.days.length))
</script>

<template>
  <div v-if="days.length" class="relative grid gap-2" data-testid="story-quality">
    <div class="relative">
      <div
        ref="bar"
        tabindex="0"
        role="group"
        data-testid="story-days"
        :aria-label="t('story.days_aria')"
        class="-my-1.5 flex cursor-pointer gap-[3px] rounded py-1.5 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent"
        @pointerdown="onPointer" @pointermove="onPointer" @pointerleave="onLeave"
        @click="onClick" @keydown="onKey" @blur="active = lastCharged">
        <span v-for="(d, i) in days" :key="d.day" class="flex flex-1 flex-col justify-end gap-[2px]" :class="hasTrips ? 'h-7' : ''">
          <span v-if="hasTrips" class="relative block flex-1">
            <i v-if="d.trips > 0" data-role="km" class="absolute inset-x-0 bottom-0 rounded-[2px]"
               :class="active === i ? 'bg-sky-700 dark:bg-sky-300' : 'bg-sky-500 dark:bg-sky-400'" :style="{ height: kmHeight(d) }" />
          </span>
          <i data-role="day" class="block h-2.5 rounded-[2px] transition-transform"
             :class="[cellClass(d), active === i ? 'scale-y-[1.4]' : '']" />
        </span>
      </div>
      <div class="relative mt-1 h-3.5 text-[10.5px] text-gray-500 tabular-nums dark:text-gray-400" aria-hidden="true">
        <span v-for="d in ticks" :key="d" data-role="tick" class="absolute -translate-x-1/2" :style="{ left: pos(d - 1) }">{{ d }}.</span>
      </div>
      <!-- Sprechblase unter der Achse, Spitze zum Tag; eigene Zeile, damit sie die Legende nicht verdeckt -->
      <div class="relative h-8">
        <span v-if="tip" ref="tipEl" data-testid="story-day-tip" role="status"
              class="pointer-events-none absolute top-1 whitespace-nowrap rounded-md bg-gray-900 px-2 py-1 text-[12px] font-semibold text-white tabular-nums shadow-sm dark:bg-gray-100 dark:text-gray-900"
              :style="{ left: tipLeft }">
          <i data-role="tip-arrow" class="absolute -top-1 h-2 w-2 -translate-x-1/2 rotate-45 bg-gray-900 dark:bg-gray-100" :style="{ left: arrowLeft }" aria-hidden="true" />
          {{ tip }}
        </span>
      </div>
    </div>
    <!-- Legende: nur, was im Monat vorkommt -->
    <div data-testid="story-days-legend" class="-mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-[11px] text-gray-500 dark:text-gray-400">
      <span class="inline-flex items-center gap-1"><i class="h-2 w-2.5 rounded-[2px] bg-green-700 dark:bg-green-400" />{{ t('story.days_legend_charged') }}</span>
      <span v-if="hasTrips" class="inline-flex items-center gap-1"><i class="h-2 w-2.5 rounded-[2px] bg-sky-500 dark:bg-sky-400" />{{ t('story.days_legend_drove') }}</span>
      <small data-testid="story-days-hint" class="ml-auto text-[11px] text-gray-500 dark:text-gray-400"><span class="md:hidden">{{ t('story.days_tap_hint') }}</span><span class="hidden md:inline">{{ t('story.days_click_hint') }}</span></small>
    </div>
  </div>
</template>
