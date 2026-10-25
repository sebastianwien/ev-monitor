<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { CostLayer } from '../../composables/storyEnergy'

/**
 * Die Kosten des Monats aufgefaechert: Strom, Fixkosten, Einnahmen, Netto. In Euro des Monats wie
 * Hero-Satz und Knopf, damit Netto die Summe auf dem Knopf ist. Gestaffeltes Einblenden ueber
 * @starting-style, Browser ohne Unterstuetzung zeigen die Zeilen sofort.
 */
defineProps<{ layers: CostLayer[] }>()

const { t } = useI18n()
const { formatCurrency } = useLocaleFormat()

function value(l: CostLayer): string {
  const s = formatCurrency(l.eur)
  return l.kind === 'income' ? `-${s}` : s
}
</script>

<template>
  <ul class="mt-3 max-w-[34ch]" data-testid="story-cost-layers">
    <li
      v-for="(l, i) in layers" :key="l.kind"
      class="story-layer flex items-baseline justify-between gap-4 py-1.5 text-[15px]"
      :class="l.kind === 'net' ? 'border-t border-gray-300 dark:border-gray-600 mt-1 pt-2 font-extrabold text-gray-900 dark:text-gray-50' : 'text-gray-700 dark:text-gray-300'"
      :style="{ '--i': i }">
      <span>{{ t(`story.layers_${l.kind}`) }}</span>
      <span class="tabular-nums" :class="l.kind === 'income' ? 'text-story-accent dark:text-story-accent-dark' : ''">{{ value(l) }}</span>
    </li>
    <li class="text-xs text-gray-500 dark:text-gray-400 pt-1">{{ t('story.layers_absolute') }}</li>
  </ul>
</template>

<style scoped>
.story-layer {
  opacity: 1;
  transform: translateY(0);
  transition: opacity 320ms ease, transform 320ms cubic-bezier(0.2, 0.7, 0.2, 1);
  transition-delay: calc(var(--i) * 60ms);
}
@starting-style {
  .story-layer { opacity: 0; transform: translateY(6px); }
}
@media (prefers-reduced-motion: reduce) {
  .story-layer { transition: none; }
}
</style>
