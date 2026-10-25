<script setup lang="ts">
import { LockClosedIcon } from '@heroicons/vue/24/outline'

/**
 * Ein Balken aus Anteilen, jedes Stück mit Beschriftung und Prozent darunter. Strom-Balken und Streckentypen nutzen ihn.
 * Ein gesperrtes Stück zeigt statt der Zahl ein Schloss; width weicht dann vom Anteil ab.
 */
export interface ShareSegment { key: string; label: string; pct: number; cls: string; text?: string; width?: number; locked?: boolean; hint?: string }
withDefaults(defineProps<{ segments: ShareSegment[]; large?: boolean; testid?: string }>(), { large: false, testid: undefined })
</script>

<template>
  <div class="grid gap-1" :data-testid="testid">
    <div class="flex overflow-hidden rounded-[3px]" :class="large ? 'h-4' : 'h-2.5'">
      <i v-for="s in segments" :key="s.key" data-role="segment" :data-locked="s.locked || undefined" :class="s.cls" :style="{ width: `${s.width ?? s.pct}%` }" />
    </div>
    <div class="flex justify-between gap-2 text-gray-500 tabular-nums dark:text-gray-400" :class="large ? 'text-[13px]' : 'text-[11px]'">
      <span v-for="s in segments" :key="s.key" data-role="label" class="truncate" :class="s.text">
        <template v-if="s.locked">{{ s.label }} <LockClosedIcon class="inline h-3 w-3 align-[-1px]" aria-hidden="true" /><span class="sr-only">{{ s.hint }}</span></template>
        <template v-else>{{ s.label }} {{ s.pct }} %</template>
      </span>
    </div>
  </div>
</template>
