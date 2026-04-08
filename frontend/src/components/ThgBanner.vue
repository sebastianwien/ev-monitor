<script setup lang="ts">
import { useCountryStore } from '../stores/country'
const countryStore = useCountryStore()
</script>

<template>
  <a
    v-if="countryStore.country === 'DE'"
    href="https://Geld-fuer-eAuto.de/ref/evmonitor"
    target="_blank"
    rel="noopener sponsored"
    class="relative flex items-center justify-between gap-3 bg-green-50 dark:bg-green-900/15 border border-green-100 dark:border-green-900/40 rounded-xl px-4 py-3 hover:bg-green-100 dark:hover:bg-green-900/25 transition-colors group mb-4"
  >
    <div class="flex items-center gap-3 min-w-0">
<div class="min-w-0">
        <p class="text-sm font-semibold text-gray-800 dark:text-gray-200 leading-snug">THG-Prämie schon beantragt?</p>
        <p class="text-xs text-gray-500 dark:text-gray-400 leading-snug">Bis zu 260 € zurückholen.</p>
      </div>
    </div>
    <span class="shrink-0 text-xs font-semibold text-green-700 dark:text-green-400 group-hover:underline whitespace-nowrap">Jetzt beantragen →</span>
    <span class="absolute bottom-1 right-3 text-[10px] text-gray-300 dark:text-gray-600">Anzeige</span>
  </a>
</template>
