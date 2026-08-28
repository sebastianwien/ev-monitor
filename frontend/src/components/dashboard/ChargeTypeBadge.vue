<script setup lang="ts">
/**
 * AC/DC-Badge einer Ladung - eine Quelle fuer die Farbwelt (AC amber, DC blau),
 * statt der Klassenkaskade an jeder Fundstelle. Andere Werte rendern nichts.
 */
defineProps<{ type?: string | null }>()
</script>

<template>
  <span v-if="type === 'AC'"
    class="text-[10px] px-1.5 py-0.5 rounded bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300 font-medium whitespace-nowrap">AC</span>
  <span v-else-if="type === 'DC'"
    class="text-[10px] px-1.5 py-0.5 rounded bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300 font-medium whitespace-nowrap">DC</span>
</template>
