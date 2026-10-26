<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'
import Collapse from './Collapse.vue'

/**
 * Collapse, dessen Inhalt nur gemountet ist, solange er offen ist (plus Zuklapp-Animation, 300 ms wie Collapse).
 * Für Inhalte, die beim Mounten laden oder nachfragen (Integrationen auf /imports): zugeklappt tun sie nichts.
 */
const props = defineProps<{ open: boolean }>()

const mounted = ref(props.open)
let unmountTimer: ReturnType<typeof setTimeout> | undefined
watch(() => props.open, (open) => {
  clearTimeout(unmountTimer)
  if (open) mounted.value = true
  else unmountTimer = setTimeout(() => { mounted.value = false }, 300)
})
onUnmounted(() => clearTimeout(unmountTimer))
</script>

<template>
  <Collapse :open="open">
    <div v-if="mounted"><slot /></div>
  </Collapse>
</template>
