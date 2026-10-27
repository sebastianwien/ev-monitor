<script setup lang="ts">
import { computed } from 'vue'
import { prefersReducedEffects } from '../../composables/prefersReducedEffects'

/**
 * Konfetti für 1,5 s über dem Inhalt: 40 kleine Flächen in den Farben der App, die von oben
 * fallen und sich drehen. Nichts bei "Bewegung reduzieren". Nur Dekoration, aria-hidden.
 */
const COLORS = ['#16a34a', '#111827', '#0ea5e9', '#f59e0b', '#9ca3af']
const show = computed(() => !prefersReducedEffects())
const pieces = Array.from({ length: 40 }, (_, i) => ({
  left: `${(i * 37) % 100}%`,
  delay: `${((i * 53) % 40) / 100}s`,
  color: COLORS[i % COLORS.length],
  rotate: `${(i * 97) % 360}deg`,
  size: 6 + ((i * 7) % 5),
}))
</script>

<template>
  <div v-if="show" class="story-confetti pointer-events-none fixed inset-0 z-[70] overflow-hidden" aria-hidden="true" data-testid="story-confetti">
    <i v-for="(p, i) in pieces" :key="i" class="absolute -top-3 block rounded-[1px]"
       :style="{ left: p.left, width: `${p.size}px`, height: `${p.size * 1.6}px`, background: p.color, animationDelay: p.delay, '--rot': p.rotate }" />
  </div>
</template>

<style scoped>
.story-confetti i { animation: story-confetti-fall 1.5s cubic-bezier(0.2, 0.6, 0.4, 1) forwards; }
@keyframes story-confetti-fall {
  0% { transform: translateY(0) rotate(0deg); opacity: 1; }
  100% { transform: translateY(100vh) rotate(var(--rot)); opacity: 0; }
}
</style>
