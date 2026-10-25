<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import ChoiceChip from './ChoiceChip.vue'
import { useHaptic } from '../../composables/useHaptic'
import { WORKSPACE_TABS } from '../../config/tabs'

/**
 * Die vier Ziele des eingeloggten Bereichs als eine Leiste - nur Desktop. Auf Mobile
 * wischt man stattdessen paarweise durch die Layouts, dort steht der Zweier-Switch.
 * Dieselbe Chip-Reihe wie über der Übersicht (/story), der aktive Chip kommt aus der Route.
 */
const { t } = useI18n()
const route = useRoute()
const { haptic } = useHaptic()

const tabs = computed(() => WORKSPACE_TABS.map(tab => ({ to: tab.to, label: t(tab.labelKey), active: tab.to === route.path })))
</script>

<template>
  <nav class="flex flex-wrap gap-2" :aria-label="t('story.workspace_aria')">
    <ChoiceChip v-for="tab in tabs" :key="tab.to" :href="tab.to" :active="tab.active"
      :aria-current="tab.active ? 'page' : undefined" @click="haptic()">
      {{ tab.label }}
    </ChoiceChip>
  </nav>
</template>
