<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useHaptic } from '../../composables/useHaptic'
import ChoiceChip from './ChoiceChip.vue'

/**
 * Tab-Umschalter zwischen verwandten Ansichten derselben Sache (mobil Fahrzeuge/Ladekarten
 * unter /cars). Dieselben Chips wie die Bereichs-Leiste auf dem Desktop (WorkspaceNav), der
 * aktive ist eingedrueckt. Der Zustand kommt aus der Route.
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
</script>

<template>
  <div class="flex justify-center gap-2 select-none" role="tablist">
    <ChoiceChip
      v-for="(tab, index) in tabs"
      :key="tab.to"
      :href="tab.to"
      :active="index === activeIndex"
      solid
      role="tab"
      :aria-selected="index === activeIndex"
      @click="haptic()">
      {{ tab.label }}
    </ChoiceChip>
  </div>
</template>
