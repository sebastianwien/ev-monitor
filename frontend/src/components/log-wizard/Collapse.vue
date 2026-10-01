<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'

/**
 * Klappt seinen Inhalt animiert auf null Höhe zusammen statt ihn abrupt zu entfernen.
 * Rein CSS über grid-template-rows, damit keine Höhe gemessen werden muss.
 * Zugeklappt ist der Inhalt inert und nach der Animation display:none, damit
 * space-y/gap des Elternelements keine leere Lücke für ihn reservieren.
 * overflow-hidden gilt nur während der Animation: fertig aufgeklappt dürfen
 * absolut positionierte Inhalte (z. B. Such-Dropdowns) über den Rahmen hinausragen.
 */
const props = defineProps<{ open: boolean }>()
const rendered = ref(props.open)
const expanded = ref(props.open)
/** Aufklapp-Animation beendet - erst dann Clipping lösen */
const settled = ref(props.open)

const reducedMotion = () =>
  typeof window !== 'undefined' && typeof window.matchMedia === 'function'
  && window.matchMedia('(prefers-reduced-motion: reduce)').matches

let settleTimer: ReturnType<typeof setTimeout> | undefined
watch(() => props.open, async (open) => {
  settled.value = false
  clearTimeout(settleTimer)
  // Bleibt transitionend aus (Tab im Hintergrund, Browser überspringt die Transition), trotzdem freigeben
  settleTimer = setTimeout(onEnd, 400)
  if (open) {
    rendered.value = true
    await nextTick()
    requestAnimationFrame(() => {
      expanded.value = true
      // Ohne Transition feuert kein transitionend
      if (reducedMotion()) settled.value = true
    })
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
    :class="['grid transition-[grid-template-rows,opacity] duration-300 ease-out motion-reduce:transition-none',
             expanded ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0']">
    <!-- p-1 -m-1: die 4 px Schatten der 3D-Kacheln bleiben auch sichtbar, solange geclippt wird -->
    <div :class="['min-h-0 p-1 -m-1', settled ? 'overflow-visible' : 'overflow-hidden']"><slot /></div>
  </div>
</template>
