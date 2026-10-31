<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'

/**
 * Klappt seinen Inhalt animiert auf null Höhe zusammen statt ihn abrupt zu entfernen.
 * Rein CSS über grid-template-rows, damit keine Höhe gemessen werden muss.
 * Zugeklappt ist der Inhalt inert und nach der Animation display:none, damit
 * space-y/gap des Elternelements keine leere Lücke für ihn reservieren.
 * overflow-hidden gilt nur während der Animation: fertig aufgeklappt dürfen
 * absolut positionierte Inhalte (z. B. Such-Dropdowns) über den Rahmen hinausragen.
 * Zuklappen ist die Umkehrung des Aufklappens: auf ease-out, zu ease-in (sonst kriecht es am Ende aus).
 * Mit `appear` klappt ein offen eingehängter Inhalt beim Einhängen auf (z. B. nachgeladene Listeneinträge),
 * `appearDelay` (ms) versetzt das, damit eine Liste Eintrag für Eintrag einblendet.
 */
const props = defineProps<{ open: boolean; appear?: boolean; appearDelay?: number }>()
const startOpen = props.open && !props.appear
const rendered = ref(props.open)
const expanded = ref(startOpen)
/** Aufklapp-Animation beendet - erst dann Clipping lösen */
const settled = ref(startOpen)

const reducedMotion = () =>
  typeof window !== 'undefined' && typeof window.matchMedia === 'function'
  && window.matchMedia('(prefers-reduced-motion: reduce)').matches

let settleTimer: ReturnType<typeof setTimeout> | undefined
const expand = () => {
  requestAnimationFrame(() => {
    expanded.value = true
    // Ohne Transition feuert kein transitionend
    if (reducedMotion()) settled.value = true
  })
}
let delayTimer: ReturnType<typeof setTimeout> | undefined
onMounted(() => {
  if (!props.open || !props.appear) return
  const delay = props.appearDelay ?? 0
  settleTimer = setTimeout(onEnd, 400 + delay)
  if (delay > 0) delayTimer = setTimeout(expand, delay)
  else expand()
})
onUnmounted(() => { clearTimeout(settleTimer); clearTimeout(delayTimer) })
watch(() => props.open, async (open) => {
  settled.value = false
  clearTimeout(settleTimer)
  // Bleibt transitionend aus (Tab im Hintergrund, Browser überspringt die Transition), trotzdem freigeben
  settleTimer = setTimeout(onEnd, 400)
  if (open) {
    rendered.value = true
    await nextTick()
    expand()
  } else {
    expanded.value = false
    if (reducedMotion()) rendered.value = false
  }
})

const onEnd = () => {
  clearTimeout(settleTimer)
  if (props.open) settled.value = true
  else rendered.value = false
}
</script>

<template>
  <div v-show="rendered" :inert="!open" @transitionend.self="onEnd"
    :class="['grid grid-cols-[minmax(0,1fr)] transition-[grid-template-rows,opacity] duration-300 motion-reduce:transition-none',
             expanded ? 'grid-rows-[1fr] opacity-100 ease-out' : 'grid-rows-[0fr] opacity-0 ease-in']">
    <!-- p-1 -m-1: die 4 px Schatten der 3D-Kacheln bleiben auch sichtbar, solange geclippt wird -->
    <div :class="['min-h-0 min-w-0 p-1 -m-1', settled ? 'overflow-visible' : 'overflow-hidden']"><slot /></div>
  </div>
</template>
