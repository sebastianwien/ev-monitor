<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { flowShares, type EnergyFlow } from '../../composables/storyEnergy'
import ShareBar from './ShareBar.vue'

/** Wo der Netzstrom bleibt, als ein Balken: jedes Stück mit seinem Anteil beschriftet, in der Farbe des Stücks (Blau dunkler, damit kleiner Text lesbar bleibt). */
const props = withDefaults(defineProps<{ flow: EnergyFlow; large?: boolean }>(), { large: false })
const { t } = useI18n()

const STYLE = {
  home: { label: 'story.flow_home', cls: 'bg-green-700 dark:bg-green-400', text: 'text-green-700 dark:text-green-400' },
  public: { label: 'story.flow_public', cls: 'bg-sky-500', text: 'text-sky-700 dark:text-sky-400' },
  vehicle: { label: 'story.flow_in_battery', cls: 'bg-green-700 dark:bg-green-400', text: 'text-green-700 dark:text-green-400' },
  loss: { label: 'story.flow_loss_label', cls: 'bg-amber-500', text: 'text-amber-700 dark:text-amber-400' },
} as const

const segments = computed(() => flowShares(props.flow).map(s => ({ ...s, ...STYLE[s.key], label: t(STYLE[s.key].label) })))
</script>

<template>
  <ShareBar :segments="segments" :large="large" testid="story-flow-bar" />
</template>
