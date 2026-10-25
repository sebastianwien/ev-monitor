<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { flowShares, type EnergyFlow } from '../../composables/storyEnergy'

/** Wo der Netzstrom bleibt, als ein Balken: jedes Stück mit seinem Anteil beschriftet. */
const props = withDefaults(defineProps<{ flow: EnergyFlow; large?: boolean }>(), { large: false })
const { t } = useI18n()

const STYLE = {
  home: { label: 'story.flow_home', cls: 'bg-green-700 dark:bg-green-400', text: '' },
  public: { label: 'story.flow_public', cls: 'bg-sky-500', text: '' },
  vehicle: { label: 'story.flow_in_battery', cls: 'bg-green-700 dark:bg-green-400', text: '' },
  loss: { label: 'story.flow_loss_label', cls: 'bg-amber-500', text: 'text-amber-700 dark:text-amber-400' },
} as const

const segments = computed(() => flowShares(props.flow).map(s => ({ ...s, ...STYLE[s.key], label: t(STYLE[s.key].label) })))
</script>

<template>
  <div class="grid gap-1" data-testid="story-flow-bar">
    <div class="flex overflow-hidden rounded-[3px]" :class="large ? 'h-4' : 'h-2.5'">
      <i v-for="s in segments" :key="s.key" data-role="segment" :class="s.cls" :style="{ width: `${s.pct}%` }" />
    </div>
    <div class="flex justify-between text-gray-500 tabular-nums dark:text-gray-400" :class="large ? 'text-[13px]' : 'text-[11px]'">
      <span v-for="s in segments" :key="s.key" data-role="label" :class="s.text">{{ s.label }} {{ s.pct }} %</span>
    </div>
  </div>
</template>
