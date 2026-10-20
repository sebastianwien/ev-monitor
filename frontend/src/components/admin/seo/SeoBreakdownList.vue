<script setup lang="ts">
import type { SeoBreakdown } from '../../../api/seoService'
import { num, pct, pos } from './seoFormat'

/** Inhalt einer aufgeklappten Zeile: Suchbegriffe einer Seite oder Seiten eines Suchbegriffs. */
defineProps<{ rows: SeoBreakdown[] | undefined; keyLabel: string; links?: boolean; intro: string }>()
</script>

<template>
  <div>
    <p class="text-xs text-gray-400 mb-2">{{ intro }}</p>
    <p v-if="!rows" class="text-xs text-gray-500">Lädt...</p>
    <p v-else-if="!rows.length" class="text-xs text-gray-500">Im Zeitraum keine Suchbegriffe von Google gemeldet. Google meldet seltene Suchen aus Datenschutzgründen nicht einzeln.</p>
    <table v-else class="text-xs w-full max-w-3xl">
      <thead>
        <tr class="text-gray-500">
          <th scope="col" class="text-left font-medium py-1 pr-3">{{ keyLabel }}</th>
          <th scope="col" class="text-right font-medium py-1 px-3">Impressionen</th>
          <th scope="col" class="text-right font-medium py-1 px-3">Klicks</th>
          <th scope="col" class="text-right font-medium py-1 px-3">Klickrate</th>
          <th scope="col" class="text-right font-medium py-1 pl-3">Position</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rows.slice(0, 50)" :key="r.key" class="border-t border-gray-800/60">
          <td class="py-1 pr-3 text-gray-200">
            <a v-if="links" :href="r.key" target="_blank" rel="noopener" class="font-mono hover:underline">{{ r.key }}</a>
            <template v-else>{{ r.key }}</template>
            <span v-if="r.brand" class="ml-1 text-gray-500">Brand</span>
          </td>
          <td class="py-1 px-3 text-right tabular-nums text-gray-300">{{ num(r.impressions) }}</td>
          <td class="py-1 px-3 text-right tabular-nums text-gray-300">{{ num(r.clicks) }}</td>
          <td class="py-1 px-3 text-right tabular-nums text-gray-300">{{ r.impressions ? pct(r.ctr) : '-' }}</td>
          <td class="py-1 pl-3 text-right tabular-nums text-gray-300">{{ pos(r.avgPosition) }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="rows && rows.length > 50" class="text-xs text-gray-500 mt-1">Die 50 häufigsten von {{ rows.length }}.</p>
  </div>
</template>
