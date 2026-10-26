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
  <!-- Liegt in der Box der Seite: nur Haarlinien zwischen den Sektionen, keine eigene Kante -->
  <div class="divide-y divide-gray-200 border-t border-gray-200 dark:divide-white/10 dark:border-white/10">
    <template v-for="section in order" :key="section">
      <slot :name="section" />
    </template>
  </div>
</template>
