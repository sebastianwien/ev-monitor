<script setup lang="ts">
import { computed } from 'vue'
import { numberParts } from '../../utils/numberParts'

/** Ein fertiger Satz mit fett gesetzten Zahlen; mit `units` auch ihre Einheit ("45 kg CO₂", "7 €"). */
const props = defineProps<{ text: string; units?: boolean }>()
const parts = computed(() => numberParts(props.text, { units: props.units }))
</script>

<template>
  <span><template v-for="(p, i) in parts" :key="i"><strong v-if="p.number" class="font-bold" :class="p.money && 'ci-money'">{{ p.text }}</strong><template v-else>{{ p.text }}</template></template></span>
</template>
