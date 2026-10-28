<script setup lang="ts">
/**
 * Beschriftete Balken unter einer Insight-Zeile: Bezeichnung links, Wert rechts, Maßstab ist der
 * größte Wert. Die Zeile sagt dasselbe als Satz, deshalb ist die Grafik aria-hidden. Farben nach
 * CI: der eigene oder gemessene Wert Blau (sky-500), der Vergleichs- oder Schätzwert gray-300.
 */
export interface InsightBar {
  label: string
  value: number
  text: string
  /** Hervorgehoben (eigener, gemessener Wert). */
  accent?: boolean
}
defineProps<{ bars: InsightBar[] }>()

const width = (bars: InsightBar[], b: InsightBar) => {
  const max = Math.max(0, ...bars.map(x => x.value))
  return max > 0 ? `${Math.max(4, Math.round((b.value / max) * 100))}%` : '4%'
}
</script>

<template>
  <div class="grid gap-1.5" aria-hidden="true" data-testid="story-insight-bars">
    <div v-for="b in bars" :key="b.label" class="flex items-center gap-2 text-[12.5px] tabular-nums">
      <span class="w-20 shrink-0 truncate text-gray-500 dark:text-gray-400">{{ b.label }}</span>
      <span class="h-2 flex-1 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
        <i class="block h-full rounded-full" :class="b.accent ? 'bg-sky-500 dark:bg-sky-400' : 'bg-gray-300 dark:bg-gray-600'" :style="{ width: width(bars, b) }" />
      </span>
      <span class="w-16 shrink-0 text-right text-gray-600 dark:text-gray-300">{{ b.text }}</span>
    </div>
  </div>
</template>
