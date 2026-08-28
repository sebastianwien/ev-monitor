<script setup lang="ts">
/**
 * Eine Zelle im Metrics-Grid einer Feed-Zeile: kleines Label, Wert dahinter.
 *
 * Fahrten und Ladungen teilen sich diese Zelle, damit beide mit identischen Abstaenden
 * fluchten und sich der Tag fast wie eine Tabelle liest. `emphasized` hebt den Hauptwert
 * einer Zeile (Verbrauch, Kosten) eine Graustufe an.
 */
defineProps<{ label: string; emphasized?: boolean }>()
</script>

<template>
  <span :class="['inline-flex items-baseline gap-1 whitespace-nowrap',
                 emphasized ? 'text-gray-600 dark:text-gray-300' : 'text-gray-500 dark:text-gray-400']">
    <span class="text-gray-400 text-[11px]">{{ label }}</span><slot />
  </span>
</template>
