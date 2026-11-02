<script setup lang="ts">
import BoldNumbers from './BoldNumbers.vue'
import { keepUnits } from '../../utils/modelStory'

/** Winter-Ausblick im leeren laufenden Monat: im Rückblick-Kopf unter den Zahlen, ohne Rückblick unter der Leerzeile. Je Fakt eine Zeile (Reichweite, Wetter der Woche). */
defineProps<{ eyebrow: string; lines: string[] }>()
</script>

<template>
  <div data-testid="story-winter-outlook" class="grid gap-1">
    <p class="text-center text-[16px] font-semibold uppercase tracking-[0.06em] text-green-700 dark:text-green-400">{{ eyebrow }}</p>
    <p v-for="line in lines" :key="line" class="text-[15px] text-pretty text-gray-900 tabular-nums dark:text-gray-50"><BoldNumbers :text="keepUnits(line)" /></p>
  </div>
</template>
