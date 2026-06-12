<template>
  <svg class="bolt-logo" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" aria-hidden="true">
    <defs>
      <clipPath :id="clipId">
        <path :d="BOLT"/>
      </clipPath>
      <filter :id="blurId" x="-50%" y="-50%" width="200%" height="200%">
        <feGaussianBlur stdDeviation="1.8"/>
      </filter>
    </defs>
    <path class="burst" :d="BOLT" :fill="fillColor" :filter="`url(#${blurId})`"/>
    <g :clip-path="`url(#${clipId})`">
      <rect class="fill" x="6" y="1" width="20" height="29" :fill="fillColor"/>
    </g>
    <path :d="BOLT" fill="none" :stroke="strokeColor" stroke-width="1.4" stroke-linejoin="round"/>
  </svg>
</template>

<script lang="ts">
let uidCounter = 0
</script>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  /** Helle Variante für dunkle/farbige Hintergründe (Indigo-Navbar) */
  light?: boolean
}>()

const BOLT = 'M19.5 3 L9 18 L15.5 18 L13 29 L24 14 L17.5 14 Z'

const uid = ++uidCounter
const clipId = `bolt-clip-${uid}`
const blurId = `bolt-blur-${uid}`

const fillColor = computed(() => props.light ? '#4ade80' : '#22c55e')
const strokeColor = computed(() => props.light ? '#ffffff' : '#16a34a')
</script>

<style scoped>
.bolt-logo .fill {
  transform-origin: 16px 29px;
  animation: bolt-charge 16s ease-out infinite;
}
.bolt-logo .burst {
  opacity: 0;
  animation: bolt-burst 16s ease-out infinite;
}
.bolt-logo:hover .fill {
  animation: bolt-charge-hover 1.8s ease-out;
}
.bolt-logo:hover .burst {
  animation: bolt-burst-hover 1.8s ease-out;
}

/* Idle-Loop: Aufladen in den ersten 4s, danach 12s Ruhe (voller Blitz) */
@keyframes bolt-charge {
  0%   { transform: scaleY(1); }
  2%   { transform: scaleY(0); }
  27%  { transform: scaleY(1); }
  100% { transform: scaleY(1); }
}
@keyframes bolt-burst {
  0%, 26% { opacity: 0; }
  31%     { opacity: 0.9; }
  40%     { opacity: 0; }
  100%    { opacity: 0; }
}

/* Hover: schnelles Wiederaufladen */
@keyframes bolt-charge-hover {
  0%   { transform: scaleY(1); }
  8%   { transform: scaleY(0); }
  72%  { transform: scaleY(1); }
  100% { transform: scaleY(1); }
}
@keyframes bolt-burst-hover {
  0%, 70% { opacity: 0; }
  80%     { opacity: 0.9; }
  100%    { opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .bolt-logo .fill,
  .bolt-logo .burst,
  .bolt-logo:hover .fill,
  .bolt-logo:hover .burst {
    animation: none;
  }
  .bolt-logo .fill { transform: scaleY(1); }
  .bolt-logo .burst { opacity: 0; }
}
</style>
