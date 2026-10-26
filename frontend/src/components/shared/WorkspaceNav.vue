<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import ChoiceChip from './ChoiceChip.vue'
import { useHaptic } from '../../composables/useHaptic'
import { WORKSPACE_TABS } from '../../config/tabs'
import { useActiveIndicator } from '../../composables/useActiveIndicator'

/**
 * Die vier Bereiche des eingeloggten Bereichs - nur Desktop, mobil trägt die Bottom-Nav. chips: zentrierte
 * Chip-Reihe unter dem Kopf; bar: Reiter in der Navbar (WorkspaceFrame, sobald sie dort passen), kurz beschriftet. Die Story
 * ist das Dashboard, dort ist dessen Eintrag aktiv. Import und Autos vergleichen liegen im Kontomenü.
 */
withDefaults(defineProps<{ variant?: 'chips' | 'bar' }>(), { variant: 'chips' })
const { t } = useI18n()
const route = useRoute()
const { haptic } = useHaptic()

const activePath = computed(() => route.path === '/story' ? '/dashboard' : route.path)
const tabs = computed(() => WORKSPACE_TABS.map(tab => ({
  to: tab.to,
  label: t(tab.labelKey),
  barLabel: t('barLabelKey' in tab ? tab.barLabelKey : tab.labelKey),
  active: tab.to === activePath.value,
})))
const barTab = (active: boolean) => `inline-flex items-center px-2.5 text-[15px] font-semibold whitespace-nowrap transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-green-600 ${
  active ? 'text-gray-900 dark:text-gray-50' : 'text-gray-600 hover:text-gray-900 dark:text-gray-300 dark:hover:text-white'
}`

// Unterstrich statt Fläche, damit der aktive Bereich nicht wie ein Knopf im Inhalt aussieht: ein Strich, der gleitet
const bar = ref<HTMLElement | null>(null)
const line = useActiveIndicator(bar, '[aria-current="page"]', activePath)
</script>

<template>
  <nav v-if="variant === 'bar'" ref="bar" class="relative flex h-16 items-stretch" :aria-label="t('story.workspace_aria')">
    <router-link v-for="tab in tabs" :key="tab.to" :to="tab.to" :class="barTab(tab.active)"
      :aria-current="tab.active ? 'page' : undefined" @click="haptic()">
      {{ tab.barLabel }}
    </router-link>
    <span data-testid="workspace-nav-indicator" aria-hidden="true"
      class="pointer-events-none absolute bottom-0 left-0 h-[3px] rounded-t-sm bg-gray-900 dark:bg-gray-100"
      :class="[line.glide.value ? 'transition-[transform,width] duration-300 ease-out motion-reduce:transition-none' : '', line.visible.value ? '' : 'opacity-0']"
      :style="line.style.value" />
  </nav>
  <nav v-else class="flex flex-wrap justify-center gap-2" :aria-label="t('story.workspace_aria')">
    <ChoiceChip v-for="tab in tabs" :key="tab.to" :href="tab.to" :active="tab.active" solid
      :aria-current="tab.active ? 'page' : undefined" @click="haptic()">
      {{ tab.label }}
    </ChoiceChip>
  </nav>
</template>
