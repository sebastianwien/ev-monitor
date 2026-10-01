<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { activeRuler } from './rulerState'

/**
 * Zahl mit Rädchen: ein horizontal wischbarer Maßstab unter dem Wert, als nativer Scroll-Container
 * mit Scroll-Snap - kein eigenes Touch-Handling, deshalb auf iOS und Android gleich robust.
 * Nur das aktive Feld zeigt seinen Maßstab (Akkordeon über `activeRuler`), die anderen sind eine
 * Zeile. Tipp auf die Zeile aktiviert das Rädchen, Tipp auf die Zahl öffnet die Tastatur.
 */
const props = withDefaults(defineProps<{
  id: string
  unit: string
  label: string
  min: number
  max: number
  step: number
  placeholder?: string
  inputmode?: 'decimal' | 'numeric'
  testid?: string
  autofocus?: boolean
  /** Zweite Zeile unter dem Wert, z. B. "+312 km seit dem letzten Log" */
  /** Grauer Zusatz in der Wertzeile direkt vor dem Wert, z. B. "+310 km" */
  prefix?: string | null
  sub?: string | null
  subTone?: 'muted' | 'warn' | 'notice'
  /** Wo der Maßstab steht, solange noch kein Wert gesetzt ist (z. B. geschätzter SoC); min bleibt der Anschlag */
  start?: number | null
  /** Pixel je Schritt - kleiner = mehr Strecke pro Wisch */
  pxPerStep?: number
  /** Beschriftung alle n Schritte */
  labelEvery?: number
  /** Beschriftung als Differenz zu diesem Wert ("+50", "+100") statt absolut - Tacho zeigt die gefahrene Strecke */
  labelBase?: number | null
}>(), { inputmode: 'decimal', pxPerStep: 9, labelEvery: 10, subTone: 'muted' })
const model = defineModel<number | null>()

const decimals = computed(() => Math.max(0, Math.ceil(-Math.log10(props.step))))
const steps = computed(() => Math.round((props.max - props.min) / props.step))
/** Ohne bekannten Bereich (erstes Log, kein letzter Tacho) wäre der Maßstab kilometerlang - dann nur Tastatur. */
const MAX_STEPS = 5000
const rulerable = computed(() => steps.value > 0 && steps.value <= MAX_STEPS)
const active = computed(() => activeRuler.value === props.id)
/**
 * Zeile antippen öffnet nur das Rädchen. Die Tastatur kommt erst, wenn der Nutzer in der
 * offenen Zeile direkt auf die Zahl tippt - sonst schiebt sich bei jedem Zeilenwechsel die
 * Tastatur über den Maßstab. Ein noch fokussiertes Feld der vorigen Zeile verliert den Fokus.
 */
const activate = () => {
  if (activeRuler.value === props.id) return
  activeRuler.value = props.id
  const el = document.activeElement
  if (el instanceof HTMLElement && el.id !== props.id && el.tagName === 'INPUT') el.blur()
}
/** Tipp auf das Label: offene Box zu, geschlossene auf. */
const toggleFromLabel = () => { if (active.value) activeRuler.value = null; else activate() }
const onInputPointerDown = (e: Event) => {
  if (active.value) return
  e.preventDefault()
  activate()
}

const ruler = ref<HTMLElement | null>(null)
const canvas = ref<HTMLCanvasElement | null>(null)
const toIndex = (v: number) => Math.round((Math.min(props.max, Math.max(props.min, v)) - props.min) / props.step)
const toValue = (i: number) => Number((props.min + i * props.step).toFixed(decimals.value))

/** Der Maßstab wird einmal je Aktivierung gezeichnet - nur sichtbar hat er eine Breite. */
let drawnWidth = 0
const draw = () => {
  const el = ruler.value, cv = canvas.value
  if (!el || !cv || !el.clientWidth || drawnWidth === el.clientWidth) return
  const W = el.clientWidth, total = steps.value * props.pxPerStep + W, dpr = window.devicePixelRatio || 1
  drawnWidth = W
  cv.width = total * dpr; cv.height = 40 * dpr; cv.style.width = `${total}px`
  const g = cv.getContext('2d'); if (!g) return
  g.scale(dpr, dpr)
  const cs = getComputedStyle(el)
  g.strokeStyle = cs.getPropertyValue('--ruler-tick') || '#9ca3af'
  g.fillStyle = cs.getPropertyValue('--ruler-text') || '#6b7280'
  g.font = `11px ${cs.fontFamily}`; g.textAlign = 'center'; g.lineWidth = 1
  const half = Math.max(1, Math.round(props.labelEvery / 2))
  for (let i = 0; i <= steps.value; i++) {
    const x = W / 2 + i * props.pxPerStep + 0.5
    const major = i % props.labelEvery === 0, mid = !major && i % half === 0
    g.beginPath(); g.moveTo(x, 4); g.lineTo(x, major ? 22 : mid ? 16 : 11); g.stroke()
    if (major) {
      const v = toValue(i)
      g.fillText(props.labelBase != null ? `+${(v - props.labelBase).toLocaleString('de-DE')}` : v.toLocaleString('de-DE'), x, 36)
    }
  }
  syncScroll()
}

/** Modell → Maßstab (Tastatur, Chips, Vorbelegung); der Scroll-Handler ignoriert das Echo. */
let echo = false
const syncScroll = () => {
  const el = ruler.value; if (!el) return
  const v = model.value ?? props.start
  if (v == null) return
  const left = toIndex(v) * props.pxPerStep
  if (Math.abs(el.scrollLeft - left) < 1) return
  echo = true; el.scrollLeft = left
}
watch(model, () => { syncScroll() })
watch(() => props.start, () => { if (model.value == null) syncScroll() })
watch(() => props.labelBase, () => { drawnWidth = 0; if (active.value) draw() })

/**
 * Einmal pro Gerät: beim ersten offenen Maßstab ruckt der Streifen kurz an und zurück, mit
 * Haptik - das zeigt die Wischgeste, ohne dass ein Hinweistext Höhe kostet. Reine CSS-Animation
 * auf dem Canvas, kein Scroll, also kein Modell-Update.
 */
const NUDGE_KEY = 'wizard-ruler-nudged'
const nudging = ref(false)
const nudgeOnce = () => {
  try {
    if (localStorage.getItem(NUDGE_KEY)) return
    localStorage.setItem(NUDGE_KEY, '1')
  } catch { return }
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return
  nudging.value = true
  navigator.vibrate?.(3)
  setTimeout(() => { nudging.value = false }, 700)
}

/** Maßstab → Modell, gedrosselt auf einen Frame; Haptik alle labelEvery Schritte. */
let raf = 0, lastIndex = -1
const onScroll = () => {
  if (echo) { echo = false; return }
  if (raf) return
  raf = requestAnimationFrame(() => {
    raf = 0
    const el = ruler.value; if (!el) return
    const i = Math.max(0, Math.min(steps.value, Math.round(el.scrollLeft / props.pxPerStep)))
    if (i === lastIndex) return
    lastIndex = i
    model.value = toValue(i)
    if (i % props.labelEvery === 0) navigator.vibrate?.(3)
  })
}
const onKey = (e: KeyboardEvent) => {
  const dir = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0
  if (!dir) return
  e.preventDefault()
  model.value = toValue(Math.max(0, Math.min(steps.value, toIndex(model.value ?? props.min) + dir * (e.shiftKey ? props.labelEvery : 1))))
}
const onInput = (e: Event) => {
  const v = (e.target as HTMLInputElement).value
  model.value = v === '' ? null : Number(v)
}

// Beim Wiederöffnen zeichnet draw() nicht neu (gleiche Breite), der Streifen stünde sonst auf dem alten
// Wert und der erste Wisch setzt das Modell zurück - deshalb immer nachziehen.
watch(active, async (on) => { if (on) { await nextTick(); draw(); syncScroll(); nudgeOnce() } })
onMounted(() => { if (props.autofocus) activate(); if (active.value) nextTick(draw) })
onBeforeUnmount(() => { if (raf) cancelAnimationFrame(raf); if (activeRuler.value === props.id) activeRuler.value = null })
const shown = computed(() => model.value == null ? '' : String(model.value))
</script>

<template>
  <div :data-testid="testid ? `${testid}-row` : undefined" :class="['rounded-sm border-2 px-3 py-2 transition cursor-pointer select-none',
      active ? 'border-indigo-600 bg-indigo-50/40 dark:bg-indigo-900/20' : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800']"
    v-haptic @click="activate">
    <div :class="['grid items-baseline gap-x-2 min-h-9', prefix ? 'grid-cols-[1fr_auto_auto_auto]' : 'grid-cols-[1fr_auto_auto]']">
      <span class="text-sm text-gray-500 dark:text-gray-400 cursor-pointer py-2 -my-2" aria-hidden="true" @click.stop="toggleFromLabel">{{ label }}</span>
      <span v-if="prefix" class="text-xs tabular-nums whitespace-nowrap text-gray-400 dark:text-gray-500">{{ prefix }}</span>
      <input :id="id" :data-testid="testid" type="number" :inputmode="inputmode" :step="step" :min="min" :max="max"
        :placeholder="placeholder" :value="shown" :aria-label="label" @input="onInput" @focus="activate" @pointerdown="onInputPointerDown"
        :class="['w-[8ch] min-w-0 bg-transparent border-0 p-0 text-right font-medium tabular-nums text-gray-900 dark:text-gray-100 placeholder:text-gray-300 dark:placeholder:text-gray-600 focus:ring-0 focus:outline-none [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none transition-[font-size]',
                 active ? 'text-3xl' : 'text-2xl']" />
      <span class="text-base text-gray-500 dark:text-gray-400">{{ unit }}</span>
      <span v-if="sub" :class="['col-span-full text-right text-xs tabular-nums -mt-1', subTone === 'warn' ? 'text-red-500' : subTone === 'notice' ? 'text-amber-600 dark:text-amber-400' : 'text-gray-400 dark:text-gray-500']">{{ sub }}</span>
    </div>
    <!-- Der Maßstab: Mittelmarke steht fest, der Streifen scrollt darunter durch -->
    <!-- Ränder laufen weich aus (Maske), die Mittelmarke ist kräftig: so liest sich der Streifen als etwas, das weitergeht -->
    <div v-show="active && rulerable" ref="ruler" class="ruler relative h-10 -mx-3 mt-1 overflow-x-auto overflow-y-hidden snap-x snap-mandatory touch-pan-x cursor-grab [mask-image:linear-gradient(to_right,transparent,black_18%,black_82%,transparent)]"
      role="slider" :aria-label="label" :aria-valuemin="min" :aria-valuemax="max" :aria-valuenow="model ?? min" tabindex="0"
      @scroll.passive="onScroll" @keydown="onKey">
      <canvas ref="canvas" :class="['block h-10', nudging && 'ruler-nudge']" />
      <i aria-hidden="true" class="absolute left-1/2 top-0 h-7 w-1 -ml-0.5 rounded-full bg-indigo-600 shadow-[0_0_0_2px_rgba(255,255,255,0.9)] dark:shadow-[0_0_0_2px_rgba(31,41,55,0.9)] pointer-events-none" />
    </div>
    <!-- Schnellwahl (z. B. 80/90/100 %) gehört in die offene Box, nicht darunter -->
    <div v-if="active && $slots.quick" class="mt-2" @click.stop><slot name="quick" /></div>
  </div>
</template>

<style scoped>
.ruler { scrollbar-width: none; --ruler-tick: #9ca3af; --ruler-text: #6b7280; }
.ruler::-webkit-scrollbar { display: none; }
:global(.dark) .ruler { --ruler-tick: #6b7180; --ruler-text: #9aa0b0; }
</style>
