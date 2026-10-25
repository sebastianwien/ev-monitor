<script setup lang="ts">
/** Ein Balken aus Anteilen, jedes Stück mit Beschriftung und Prozent darunter. Strom-Balken und Streckentypen nutzen ihn. */
export interface ShareSegment { key: string; label: string; pct: number; cls: string; text?: string }
withDefaults(defineProps<{ segments: ShareSegment[]; large?: boolean; testid?: string }>(), { large: false, testid: undefined })
</script>

<template>
  <div class="grid gap-1" :data-testid="testid">
    <div class="flex overflow-hidden rounded-[3px]" :class="large ? 'h-4' : 'h-2.5'">
      <i v-for="s in segments" :key="s.key" data-role="segment" :class="s.cls" :style="{ width: `${s.pct}%` }" />
    </div>
    <div class="flex justify-between gap-2 text-gray-500 tabular-nums dark:text-gray-400" :class="large ? 'text-[13px]' : 'text-[11px]'">
      <span v-for="s in segments" :key="s.key" data-role="label" class="truncate" :class="s.text">{{ s.label }} {{ s.pct }} %</span>
    </div>
  </div>
</template>
