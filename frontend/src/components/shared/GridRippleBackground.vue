<script setup lang="ts">
import { ref } from 'vue'
import { useGridRipple } from '@/composables/useGridRipple'

/**
 * Maus-reaktives Hintergrund-Gitter fuer die Landing-Page.
 *
 * Liegt als `position: fixed` Canvas hinter dem Content und deckt nur den
 * Viewport ab (nicht die volle, sehr lange Seitenhoehe). Auf Touch/Mobile oder
 * bei `prefers-reduced-motion: reduce` zeichnet es nichts - dort bleibt das
 * statische CSS-Gitter `.sl-grid` der Seite sichtbar (gleiche Optik).
 *
 * Verwendung: einmal als erstes Kind im `.sl-grid`-Root der Landing-Page
 * platzieren. Der Root muss einen eigenen Stacking-Context aufspannen (z. B.
 * `isolate`), damit der `-z-10`-Canvas ueber dem Root-Hintergrund (statisches
 * Gitter), aber hinter dem normalen Content liegt - ohne dass der Content eine
 * eigene Stapelebene braucht.
 */
const canvasRef = ref<HTMLCanvasElement | null>(null)
useGridRipple(canvasRef)
</script>

<template>
  <canvas
    ref="canvasRef"
    class="pointer-events-none fixed inset-0 -z-10 h-screen w-screen"
    aria-hidden="true"
  />
</template>
