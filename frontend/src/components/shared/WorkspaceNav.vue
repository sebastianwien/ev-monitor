<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import ChoiceChip from './ChoiceChip.vue'
import { useHaptic } from '../../composables/useHaptic'
import { WORKSPACE_LINKS, WORKSPACE_TABS } from '../../config/tabs'

/**
 * Die Ziele des eingeloggten Bereichs als eine zentrierte Chip-Reihe - nur Desktop, mobil trägt
 * die Bottom-Nav. Eine Komponente für die Übersicht (/story) und die Workspace-Seiten; die Story
 * ist das Dashboard, dort ist dessen Chip aktiv.
 */
const { t } = useI18n()
const route = useRoute()
const { haptic } = useHaptic()

const activePath = computed(() => route.path === '/story' ? '/dashboard' : route.path)
const tabs = computed(() => [...WORKSPACE_TABS, ...WORKSPACE_LINKS].map(tab => ({ to: tab.to, label: t(tab.labelKey), active: tab.to === activePath.value })))
</script>

<template>
  <nav class="flex flex-wrap justify-center gap-2" :aria-label="t('story.workspace_aria')">
    <ChoiceChip v-for="tab in tabs" :key="tab.to" :href="tab.to" :active="tab.active" solid
      :aria-current="tab.active ? 'page' : undefined" @click="haptic()">
      {{ tab.label }}
    </ChoiceChip>
  </nav>
</template>
