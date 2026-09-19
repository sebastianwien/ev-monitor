<script setup lang="ts">
/**
 * Akkordeon-Rahmen der /imports-Sektionen. Die Reihenfolge kommt als Prop, der Inhalt als
 * benannter Slot je Sektion - so steht die relevanteste Sektion auch im DOM oben und nicht
 * nur optisch. Eine reine CSS-Umsortierung (`order`) wuerde Tastatur- und Screenreader-
 * Reihenfolge von der sichtbaren trennen (WCAG 1.3.2, 2.4.3).
 */
defineProps<{ order: readonly string[] }>()
</script>

<template>
  <div class="-mx-4 md:mx-0 border-y-2 md:border-2 border-gray-300 dark:border-gray-700 md:rounded-sm divide-y-2 divide-gray-300 dark:divide-gray-700 overflow-hidden md:shadow-[2px_2px_0_0_#d1d5db] dark:md:shadow-[2px_2px_0_0_#374151]">
    <template v-for="section in order" :key="section">
      <slot :name="section" />
    </template>
  </div>
</template>
