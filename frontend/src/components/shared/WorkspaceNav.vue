<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewSegmentedControl from './ViewSegmentedControl.vue'
import { WORKSPACE_TABS } from '../../config/tabs'

/**
 * Die vier Ziele des eingeloggten Bereichs als eine Leiste - nur Desktop. Auf Mobile
 * wischt man stattdessen paarweise durch die Layouts, dort steht der Zweier-Switch.
 * Der aktive Tab kommt aus der Route (ViewSegmentedControl).
 */
const { t } = useI18n()

const tabs = computed(() => WORKSPACE_TABS.map(tab => ({ to: tab.to, label: t(tab.labelKey) })))
</script>

<template>
  <ViewSegmentedControl :tabs="tabs" />
</template>
