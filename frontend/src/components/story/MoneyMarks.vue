<script setup lang="ts">
import { computed } from 'vue'
import { numberParts } from '../../utils/numberParts'

/** Ein Satz, dessen Geldbeträge (samt €) eine Hook-Klasse tragen; sichtbar nur im Probe-Skin v1 (Textmarker). Sonst unverändert, nicht fett. */
const props = defineProps<{ text: string }>()
const parts = computed(() => numberParts(props.text, { units: true }))
</script>

<template>
  <span><template v-for="(p, i) in parts" :key="i"><span v-if="p.money" class="ci-money">{{ p.text }}</span><template v-else>{{ p.text }}</template></template></span>
</template>
