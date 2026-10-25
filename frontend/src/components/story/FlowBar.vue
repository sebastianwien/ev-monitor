<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { EnergyFlow } from '../../composables/storyEnergy'

/**
 * Wo der Netzstrom bleibt, als ein Balken: Zuhause, Unterwegs, Verlust, jedes Stück mit
 * seinem Anteil beschriftet. Ohne Ortsaufteilung ein Stück "Im Akku" plus Verlust.
 */
const props = withDefaults(defineProps<{ flow: EnergyFlow; large?: boolean }>(), { large: false })
const { t } = useI18n()

const segments = computed(() => {
  const f = props.flow
  const loss = Math.min(100, Math.max(0, f.lossPercent))
  const split = f.privateKwh + f.publicKwh
  // Der Rest nach dem Verlust teilt sich nach Ladeort. Die Ortsmengen zählen alle Ladungen, Netz und
  // Verlust nur die gemessenen: so ergeben die Stücke immer 100 %.
  const home = split > 0 ? Math.round((100 - loss) * (f.privateKwh / split)) : 0
  const list = split > 0
    ? [
      { key: 'home', label: t('story.flow_home'), pct: home, cls: 'bg-green-700 dark:bg-green-400', text: '' },
      { key: 'public', label: t('story.flow_public'), pct: 100 - loss - home, cls: 'bg-sky-500', text: '' },
    ]
    : [{ key: 'vehicle', label: t('story.flow_in_battery'), pct: 100 - loss, cls: 'bg-green-700 dark:bg-green-400', text: '' }]
  list.push({ key: 'loss', label: t('story.flow_loss_label'), pct: loss, cls: 'bg-amber-500', text: 'text-amber-700 dark:text-amber-400' })
  // Ein Ort ohne Strom ist kein Stück: "Zuhause 0 %" sagt nichts.
  return list.filter(x => x.pct > 0 || x.key === 'loss')
})
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
