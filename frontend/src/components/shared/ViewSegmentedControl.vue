<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useHaptic } from '../../composables/useHaptic'

/**
 * Tab-Umschalter zwischen verwandten Ansichten derselben Sache (Dashboard/Log-Feed im
 * CarContextLayout, Fahrzeuge/Ladekarten unter /cars). Der aktive Zustand kommt aus der
 * Route; ein Pill-Indikator gleitet zwischen den Tabs.
 */
const props = defineProps<{
  tabs: { to: string; label: string }[]
}>()

const route = useRoute()
const { haptic } = useHaptic()

const activeIndex = computed(() => {
  const index = props.tabs.findIndex(tab => tab.to === route.path)
  return index === -1 ? 0 : index
})

const labelClass = (active: boolean) =>
  `relative z-10 flex-1 text-center text-sm font-semibold py-1.5 whitespace-nowrap transition-colors ${
    active ? 'text-indigo-600 dark:text-indigo-300' : 'text-gray-500 dark:text-gray-400'
  }`
</script>

<template>
  <div class="relative flex p-1 rounded-md bg-gray-100 dark:bg-gray-800 select-none" role="tablist">
    <!-- Gleitender Indikator: eine Tab-Breite minus Padding -->
    <div
      class="absolute top-1 bottom-1 left-1 rounded-sm bg-white dark:bg-gray-700 shadow-sm transition-transform duration-200 ease-out"
      :style="{
        width: `calc(${100 / tabs.length}% - 0.25rem)`,
        transform: `translateX(${activeIndex * 100}%)`,
      }"
      aria-hidden="true"
    />
    <router-link
      v-for="(tab, index) in tabs"
      :key="tab.to"
      :to="tab.to"
      role="tab"
      :aria-selected="index === activeIndex"
      :class="labelClass(index === activeIndex)"
      @click="haptic()">
      {{ tab.label }}
    </router-link>
  </div>
</template>
