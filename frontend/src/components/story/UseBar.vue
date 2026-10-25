<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { EnergyUse } from '../../composables/storyEnergy'
import ShareBar from './ShareBar.vue'
import { LOCKED_CLS, USE_STYLE } from './energyUseStyle'

/** Wo der Strom des Monats bleibt, als ein Balken: Fahren, Stand, Laden. Gesperrter Stand als schraffierte Fläche fester Breite ohne Zahl. */
const props = withDefaults(defineProps<{ use: EnergyUse; large?: boolean }>(), { large: false })
const { t } = useI18n()

const LOCKED_WIDTH = 30

const segments = computed(() => {
  const locked = props.use.shares.some(s => s.pct == null)
  const scale = locked ? (100 - LOCKED_WIDTH) / 100 : 1
  return props.use.shares.map(s => s.pct == null
    ? { key: s.key, label: t(USE_STYLE[s.key].label), pct: 0, width: LOCKED_WIDTH, cls: LOCKED_CLS, locked: true, hint: t('story.use_locked') }
    : { key: s.key, label: t(USE_STYLE[s.key].label), pct: s.pct, width: s.pct * scale, cls: USE_STYLE[s.key].cls, text: USE_STYLE[s.key].text })
})
</script>

<template>
  <ShareBar :segments="segments" :large="large" testid="story-use-bar" />
</template>
