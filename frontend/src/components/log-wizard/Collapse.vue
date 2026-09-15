<script setup lang="ts">
/**
 * Klappt seinen Inhalt animiert auf null Höhe zusammen statt ihn abrupt zu entfernen.
 * Rein CSS über grid-template-rows, damit keine Höhe gemessen werden muss.
 * Zugeklappt ist der Inhalt inert und der Abstand des umgebenden space-y-3 wird
 * über den negativen Rand ausgeglichen, sonst bliebe eine leere Lücke stehen.
 */
defineProps<{ open: boolean }>()
</script>

<template>
  <div :inert="!open" :class="['grid transition-[grid-template-rows,opacity,margin] duration-300 ease-out motion-reduce:transition-none',
    open ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0 -mb-3']">
    <div class="min-h-0 overflow-hidden"><slot /></div>
  </div>
</template>
