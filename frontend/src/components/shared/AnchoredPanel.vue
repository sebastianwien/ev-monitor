<script setup lang="ts">
import { onUnmounted, watch } from 'vue'
import type { PanelAnchor } from '../../utils/panelAnchor'

/**
 * Panel, das sich deckungsgleich über seinen Auslöser legt und nach unten aufklappt, der Rest der Seite dunkelt ab
 * (docs/design/ci.md, Auswahl in einer Kopfzeile: Autoauswahl, Monatsraster). Der Aufrufer misst den Auslöser
 * (panelAnchor) und legt die Kopfzeile oben in den Slot; shift schiebt sie am Bildschirmrand zurück auf den Auslöser.
 * Hängt an body: Übersicht und Logs liegen im SwipeTabPager, dessen translateX Abdunklung und Panel sonst unter die
 * Navbar sperren würde. Escape und ein Tipp daneben schließen mit Fokus zurück, Scrollen und eine neue Fenstergröße
 * ohne, statt das Panel mitzuziehen.
 */
const props = defineProps<{
  open: boolean
  anchor: PanelAnchor
  /** data-testid: `<testid>-backdrop` und `<testid>-panel`. */
  testid?: string
  role?: string
  label?: string
  /** Eigene Fläche und Kante statt der Box (rounded-2xl, p-2, edge-card). */
  panelClass?: string
  /** Eigene Bewegung statt Aufdecken (JS-Transition, done am Ende), etwa der wachsende Rand der Monats-Kapsel. */
  motion?: { enter: (el: Element, done: () => void) => void; leave: (el: Element, done: () => void) => void }
}>()
const emit = defineEmits<{ close: [returnFocus: boolean] }>()

function onKey(e: KeyboardEvent) {
  if (e.key !== 'Escape') return
  e.preventDefault()
  emit('close', true)
}
const dismiss = () => emit('close', false)

function listen(on: boolean) {
  if (on) {
    window.addEventListener('scroll', dismiss, { passive: true })
    window.addEventListener('resize', dismiss)
  } else {
    window.removeEventListener('scroll', dismiss)
    window.removeEventListener('resize', dismiss)
  }
}
watch(() => props.open, listen)
onUnmounted(() => listen(false))
</script>

<template>
  <Teleport to="body">
    <Transition name="anchored-dim">
      <div v-if="open" :data-testid="testid && `${testid}-backdrop`" class="fixed inset-0 z-[60] bg-gray-950/40 dark:bg-black/60"
        aria-hidden="true" @click="emit('close', true)" />
    </Transition>
    <Transition v-if="!motion" name="anchored-panel">
      <div v-if="open" :data-testid="testid && `${testid}-panel`" :role="role" :aria-label="label"
        class="fixed z-[61]" :class="panelClass ?? 'rounded-2xl bg-white p-2 edge-card dark:bg-gray-800'"
        :style="{ top: `${anchor.top}px`, left: `${anchor.left}px`, width: `${anchor.width}px` }" @keydown="onKey">
        <slot :shift-style="anchor.shift ? { transform: `translateX(${anchor.shift}px)` } : undefined" />
      </div>
    </Transition>
    <Transition v-else :css="false" @enter="motion.enter" @leave="motion.leave">
      <div v-if="open" :data-testid="testid && `${testid}-panel`" :role="role" :aria-label="label"
        class="fixed z-[61]" :class="panelClass"
        :style="{ top: `${anchor.top}px`, left: `${anchor.left}px`, width: `${anchor.width}px` }" @keydown="onKey">
        <slot :shift-style="anchor.shift ? { transform: `translateX(${anchor.shift}px)` } : undefined" />
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* Abdunkeln und Aufklappen: auf ease-out, zu als Umkehrung mit ease-in (wie Collapse) */
.anchored-dim-enter-active { transition: opacity 0.24s ease-out; }
.anchored-dim-leave-active { transition: opacity 0.2s ease-in; }
.anchored-dim-enter-from, .anchored-dim-leave-to { opacity: 0; }

/* Das Panel wächst aus der Kopfzeile nach unten: sichtbar ist anfangs nur sie, dann der Rest */
.anchored-panel-enter-active { transition: clip-path 0.28s cubic-bezier(0.22, 0.68, 0.24, 1), opacity 0.12s ease-out; }
.anchored-panel-leave-active { transition: clip-path 0.22s cubic-bezier(0.76, 0.32, 0.78, 0.32), opacity 0.12s ease-in 0.1s; }
.anchored-panel-enter-from, .anchored-panel-leave-to { clip-path: inset(-4px -4px calc(100% - 62px) -4px round 18px); opacity: 0; }
.anchored-panel-enter-to, .anchored-panel-leave-from { clip-path: inset(-4px -4px -4px -4px round 18px); opacity: 1; }

@media (prefers-reduced-motion: reduce) {
  .anchored-dim-enter-active, .anchored-dim-leave-active,
  .anchored-panel-enter-active, .anchored-panel-leave-active { transition: none; }
}
</style>
